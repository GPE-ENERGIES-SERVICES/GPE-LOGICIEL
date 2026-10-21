package eu.gpefrance.tenders.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpServerErrorException;

import eu.gpefrance.tenders.TestcontainersConfiguration;
import eu.gpefrance.tenders.collect.boamp.BoampClient;
import eu.gpefrance.tenders.collect.ted.TedClient;

/**
 * Catch-up of the collections missed during a stop, with the schedule of
 * production (7:00 and 13:00, Monday to Friday) and BOAMP and TED mocked. It
 * is started by hand: the automatic start is disabled for the tests.
 */
@SpringBootTest(properties = { "collect.disabled-sources=APPROCH,AWS", "collect.cron=0 0 7,13 * * MON-FRI",
		"collect.catch-up.max-runs=5", "collect.catch-up.max-days-per-run=14" })
@Import(TestcontainersConfiguration.class)
class CollectCatchUpTests {

	/** Thursday 22/10/2026 at 9:30 in Paris: the 7:00 collection is the last one. */
	private static final Instant NOW = Instant.parse("2026-10-22T07:30:00Z");

	private static final Instant SLOT = Instant.parse("2026-10-22T05:00:00Z");

	@Autowired
	private CollectService service;

	@Autowired
	private CollectCatchUp catchUp;

	@Autowired
	private CollectRunRepository runs;

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoBean
	private BoampClient boampClient;

	@MockitoBean
	private TedClient tedClient;

	@TestBean
	private Clock clock;

	static Clock clock() {
		return new MutableClock(NOW);
	}

	@BeforeEach
	void cleanDatabase() {
		jdbc.execute("TRUNCATE collect_run, tender_follow, tender_notice, lot, dce_link, tender, raw_notice "
				+ "RESTART IDENTITY CASCADE");
		((MutableClock) clock).set(NOW);
		given(boampClient.fetchPublishedOn(any())).willReturn(List.of());
		given(tedClient.fetchPublishedOn(any())).willReturn(List.of());
	}

	@Test
	void aLongStopIsCaughtUpInLimitedCollectionsUpToToday() {
		lastSuccess("BOAMP", "2026-09-22T10:00:00Z", "2026-09-22");
		lastSuccess("TED", "2026-09-22T10:00:10Z", "2026-09-22");

		assertThat(catchUp.start()).isTrue();
		awaitCatchUp();

		for (String source : List.of("BOAMP", "TED")) {
			assertThat(catchUpRuns(source)).containsExactly(
					tuple(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 10, 4), CollectStatus.SUCCESS),
					tuple(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 16), CollectStatus.SUCCESS),
					tuple(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 22), CollectStatus.SUCCESS));
			assertThat(service.isUpToDate(source)).isTrue();
		}
		Map<LocalDate, Long> asked = boampDays().stream()
			.collect(Collectors.groupingBy(day -> day, Collectors.counting()));
		assertThat(asked.keySet()).hasSize(32)
			.allSatisfy(day -> assertThat(day).isBetween(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 10, 22)));
		// Only the last day of a collection is asked again by the next one.
		assertThat(asked.values()).allSatisfy(count -> assertThat(count).isLessThanOrEqualTo(2L));
		// The next scheduled collection is not a catch-up and asks two days only.
		assertThat(service.sourcesMissingTheLastSlot()).isEmpty();
	}

	@Test
	void aCatchUpStopsAfterItsNumberOfCollections() {
		lastSuccess("BOAMP", "2026-06-01T10:00:00Z", "2026-06-01");
		lastSuccess("TED", "2026-10-22T05:00:05Z", "2026-10-22");

		catchUp.start();
		awaitCatchUp();

		assertThat(catchUpRuns("BOAMP")).hasSize(5).allSatisfy(run -> assertThat(run.toList().get(2))
			.isEqualTo(CollectStatus.SUCCESS));
		assertThat(service.isUpToDate("BOAMP")).isFalse();
		assertThat(catchUpRuns("TED")).isEmpty();
		// 5 collections of 14 days with one day asked again: from 31/05 to 31/07.
		assertThat(runs.findFirstBySourceAndStatusOrderByStartedAtDescIdDesc("BOAMP", CollectStatus.SUCCESS)
			.orElseThrow()
			.getUntilDate()).isEqualTo(LocalDate.of(2026, 7, 31));
	}

	@Test
	void nothingIsCaughtUpWhenTheLastScheduledCollectionRan() {
		lastSuccess("BOAMP", "2026-10-22T05:00:20Z", "2026-10-22");
		lastSuccess("TED", "2026-10-22T05:00:30Z", "2026-10-22");

		assertThat(catchUp.start()).isFalse();
		verify(boampClient, never()).fetchPublishedOn(any());
	}

	@Test
	void aFailedScheduledCollectionIsLeftToTheNextOne() {
		lastSuccess("BOAMP", "2026-10-21T11:00:20Z", "2026-10-21");
		run("BOAMP", "2026-10-22T05:00:20Z", CollectStatus.FAILED, "HttpServerErrorException: 503");
		lastSuccess("TED", "2026-10-22T05:00:30Z", "2026-10-22");

		assertThat(service.sourcesMissingTheLastSlot()).isEmpty();
		assertThat(catchUp.start()).isFalse();
	}

	@Test
	void aCollectionInterruptedByARestartIsCaughtUp() {
		lastSuccess("BOAMP", "2026-10-21T11:00:20Z", "2026-10-21");
		run("BOAMP", "2026-10-22T05:00:20Z", CollectStatus.RUNNING, null);
		lastSuccess("TED", "2026-10-22T05:00:30Z", "2026-10-22");

		service.closeInterruptedRuns();

		assertThat(service.sourcesMissingTheLastSlot()).containsExactly("BOAMP");
		assertThat(catchUp.start()).isTrue();
		awaitCatchUp();
		assertThat(catchUpRuns("BOAMP")).containsExactly(
				tuple(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 22), CollectStatus.SUCCESS));
	}

	@Test
	void anUnavailableApiStopsTheCatchUpOfItsSourceOnly() {
		lastSuccess("BOAMP", "2026-10-12T14:24:20Z", "2026-10-12");
		lastSuccess("TED", "2026-10-12T14:24:30Z", "2026-10-12");
		given(boampClient.fetchPublishedOn(any()))
			.willThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE));

		catchUp.start();
		awaitCatchUp();

		assertThat(catchUpRuns("BOAMP")).singleElement()
			.isEqualTo(tuple(LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 22), CollectStatus.FAILED));
		assertThat(catchUpRuns("TED")).containsExactly(
				tuple(LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 22), CollectStatus.SUCCESS));
		CollectService.SourceState boamp = state("BOAMP");
		assertThat(boamp.lastFailure().getErrorMessage()).contains("503");
		assertThat(boamp.overdue()).isTrue();
		// The next scheduled collection starts again from the same day.
		assertThat(service.collect("BOAMP", CollectTrigger.SCHEDULED, null).getSinceDate())
			.isEqualTo(LocalDate.of(2026, 10, 11));
	}

	@Test
	void aSecondTriggerWhileCatchingUpStartsNothingMore() throws InterruptedException {
		lastSuccess("BOAMP", "2026-10-21T11:00:20Z", "2026-10-21");
		lastSuccess("TED", "2026-10-22T05:00:30Z", "2026-10-22");
		CountDownLatch inside = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		given(boampClient.fetchPublishedOn(any())).willAnswer(invocation -> {
			inside.countDown();
			release.await(30, TimeUnit.SECONDS);
			return List.of();
		});

		assertThat(catchUp.start()).isTrue();
		assertThat(inside.await(30, TimeUnit.SECONDS)).isTrue();
		try {
			assertThat(catchUp.start()).isFalse();
			assertThatThrownBy(() -> service.collect("BOAMP", CollectTrigger.MANUAL, null))
				.isInstanceOf(CollectAlreadyRunningException.class);
			assertThat(service.collectActive(CollectTrigger.SCHEDULED)).extracting(CollectRun::getSource)
				.containsExactly("TED");
		}
		finally {
			release.countDown();
		}
		awaitCatchUp();
		assertThat(catchUpRuns("BOAMP")).hasSize(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM collect_run WHERE status = 'RUNNING'", Integer.class))
			.isZero();
	}

	@Test
	void aPartialCollectionIsResumedWithoutDuplicatesNorTouchingTheTeamData() {
		lastSuccess("BOAMP", "2026-10-12T14:24:20Z", "2026-10-12");
		lastSuccess("TED", "2026-10-22T05:00:30Z", "2026-10-22");
		AtomicInteger failures = new AtomicInteger();
		given(boampClient.fetchPublishedOn(any())).willAnswer(invocation -> {
			LocalDate day = invocation.getArgument(0);
			if (day.equals(LocalDate.of(2026, 10, 15))) {
				return List.of(boamp("26-1", "2026-10-15"));
			}
			if (day.equals(LocalDate.of(2026, 10, 19)) && failures.getAndIncrement() == 0) {
				throw new HttpServerErrorException(HttpStatus.BAD_GATEWAY);
			}
			return day.equals(LocalDate.of(2026, 10, 20)) ? List.of(boamp("26-2", "2026-10-20")) : List.of();
		});

		catchUp.start();
		awaitCatchUp();
		assertThat(catchUpRuns("BOAMP")).singleElement().extracting(run -> run.toList().get(2))
			.isEqualTo(CollectStatus.FAILED);
		// The notice read before the failure is usable; the team works on it.
		Long id = jdbc.queryForObject("SELECT id FROM tender WHERE external_id = '26-1'", Long.class);
		jdbc.update("UPDATE tender SET follow_up_status = 'RESPONDING', follow_up_comment = 'Chiffrage' WHERE id = ?",
				id);
		String before = jdbc.queryForObject("SELECT row_to_json(t)::text FROM tender t WHERE id = ?", String.class, id);

		CollectRun next = service.collect("BOAMP", CollectTrigger.SCHEDULED, null);

		assertThat(next.getStatus()).isEqualTo(CollectStatus.SUCCESS);
		assertThat(next.getSinceDate()).isEqualTo(LocalDate.of(2026, 10, 11));
		assertThat(next.getNoticesKnown()).isEqualTo(1);
		assertThat(jdbc.queryForList("SELECT external_id FROM raw_notice ORDER BY external_id", String.class))
			.containsExactly("26-1", "26-2");
		assertThat(jdbc.queryForList("SELECT external_id FROM tender ORDER BY external_id", String.class))
			.containsExactly("26-1", "26-2");
		assertThat(jdbc.queryForObject("SELECT row_to_json(t)::text FROM tender t WHERE id = ?", String.class, id))
			.isEqualTo(before);
	}

	@Test
	void eachSourceShowsItsCoveredPeriodItsNextCollectionAndItsDelay() {
		lastSuccess("BOAMP", "2026-10-16T11:00:00Z", "2026-10-16");
		lastSuccess("TED", "2026-10-19T05:00:10Z", "2026-10-19");
		MutableClock mutable = (MutableClock) clock;

		// Monday 19/10 at 7:00:30: 18 working hours since Friday 13:00.
		mutable.set(Instant.parse("2026-10-19T05:00:30Z"));
		CollectService.SourceState boamp = state("BOAMP");
		assertThat(boamp.workingHoursSinceSuccess()).isEqualTo(18);
		assertThat(boamp.overdue()).isFalse();
		assertThat(boamp.nextRun()).isEqualTo(Instant.parse("2026-10-19T11:00:00Z"));
		assertThat(boamp.lastSuccess().getSinceDate()).isEqualTo(LocalDate.of(2026, 10, 15));
		assertThat(boamp.lastSuccess().getUntilDate()).isEqualTo(LocalDate.of(2026, 10, 16));

		// Monday 19/10 at 13:00:30, the 13:00 collection missed too: more than 24 working hours.
		mutable.set(Instant.parse("2026-10-19T11:00:30Z"));
		assertThat(state("BOAMP").overdue()).isTrue();
		assertThat(state("TED").overdue()).isFalse();
		assertThat(state("AWS").overdue()).isFalse();
		assertThat(state("AWS").nextRun()).isNull();
	}

	@Test
	void aSourceNeverCollectedIsShownLate() {
		assertThat(state("BOAMP").overdue()).isTrue();
		assertThat(state("BOAMP").workingHoursSinceSuccess()).isNull();
	}

	private CollectService.SourceState state(String source) {
		return service.sources().stream().filter(state -> state.source().equals(source)).findFirst().orElseThrow();
	}

	private List<org.assertj.core.groups.Tuple> catchUpRuns(String source) {
		return jdbc.queryForList("""
				SELECT since_date, until_date, status FROM collect_run
				WHERE source = ? AND trigger = 'CATCH_UP' ORDER BY started_at, id""", source)
			.stream()
			.map(row -> tuple(((java.sql.Date) row.get("since_date")).toLocalDate(),
					((java.sql.Date) row.get("until_date")).toLocalDate(),
					CollectStatus.valueOf((String) row.get("status"))))
			.toList();
	}

	private List<LocalDate> boampDays() {
		ArgumentCaptor<LocalDate> days = ArgumentCaptor.forClass(LocalDate.class);
		verify(boampClient, atLeastOnce()).fetchPublishedOn(days.capture());
		return days.getAllValues();
	}

	private void lastSuccess(String source, String startedAt, String until) {
		jdbc.update("""
				INSERT INTO collect_run (source, trigger, status, since_date, until_date, started_at, finished_at)
				VALUES (?, 'SCHEDULED', 'SUCCESS', CAST(? AS date) - 1, CAST(? AS date), CAST(? AS timestamptz),
				        CAST(? AS timestamptz) + interval '10 seconds')""", source, until, until, startedAt,
				startedAt);
	}

	private void run(String source, String startedAt, CollectStatus status, String error) {
		jdbc.update("""
				INSERT INTO collect_run (source, trigger, status, since_date, started_at, error_message)
				VALUES (?, 'SCHEDULED', ?, DATE '2026-10-20', CAST(? AS timestamptz), ?)""", source, status.name(),
				startedAt, error);
	}

	private void awaitCatchUp() {
		long end = System.nanoTime() + Duration.ofSeconds(60).toNanos();
		while (catchUp.isRunning() && System.nanoTime() < end) {
			Thread.onSpinWait();
		}
		assertThat(catchUp.isRunning()).isFalse();
	}

	private static SourceRecord boamp(String idweb, String published) {
		return new SourceRecord(idweb, LocalDate.parse(published), """
				{"idweb": "%s", "nature": "APPEL_OFFRE", "etat": "INITIAL", "objet": "Travaux %s",
				 "dateparution": "%s", "code_departement": ["13"], "datelimitereponse": "2026-11-30T11:00:00Z",
				 "donnees": "{}"}""".formatted(idweb, idweb, published));
	}

}
