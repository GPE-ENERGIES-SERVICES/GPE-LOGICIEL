package eu.gpefrance.tenders.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import eu.gpefrance.tenders.ClockConfiguration;
import eu.gpefrance.tenders.TestcontainersConfiguration;
import eu.gpefrance.tenders.collect.boamp.BoampClient;
import eu.gpefrance.tenders.tender.TenderRepository;

@SpringBootTest(properties = "collect.initial-days=2")
@Import(TestcontainersConfiguration.class)
class CollectServiceTests {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);

	@Autowired
	private CollectService service;

	@Autowired
	private CollectRunRepository runs;

	@Autowired
	private RawNoticeRepository rawNotices;

	@MockitoBean
	private BoampClient boampClient;

	@TestBean
	private Clock clock;

	static Clock clock() {
		return Clock.fixed(Instant.parse("2026-10-05T08:00:00Z"), ClockConfiguration.ZONE);
	}

	@Autowired
	private TenderRepository tenders;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void cleanDatabase() {
		jdbc.execute("TRUNCATE collect_run, tender_notice, lot, tender, raw_notice RESTART IDENTITY CASCADE");
	}

	@Test
	void firstCollectionFetchesTheInitialDays() {
		given(boampClient.fetchPublishedOn(any())).willReturn(List.of());
		given(boampClient.fetchPublishedOn(TODAY.minusDays(2))).willReturn(List.of(record("26-1")));
		given(boampClient.fetchPublishedOn(TODAY)).willReturn(List.of(record("26-2"), record("26-3")));

		CollectRun run = service.collect("BOAMP", CollectTrigger.MANUAL, null);

		assertThat(run.getStatus()).isEqualTo(CollectStatus.SUCCESS);
		assertThat(run.getSinceDate()).isEqualTo(TODAY.minusDays(2));
		assertThat(run.getNoticesReceived()).isEqualTo(3);
		assertThat(run.getNoticesNew()).isEqualTo(3);
		assertThat(rawNotices.count()).isEqualTo(3);
	}

	@Test
	void nextCollectionResumesTheDayBeforeTheLastSuccessAndSkipsKnownNotices() {
		given(boampClient.fetchPublishedOn(any())).willReturn(List.of(record("26-1")));
		service.collect("BOAMP", CollectTrigger.SCHEDULED, null);

		CollectRun second = service.collect("BOAMP", CollectTrigger.SCHEDULED, null);

		assertThat(second.getSinceDate()).isEqualTo(TODAY.minusDays(1));
		assertThat(second.getNoticesReceived()).isEqualTo(2);
		assertThat(second.getNoticesNew()).isZero();
		assertThat(rawNotices.count()).isEqualTo(1);
	}

	@Test
	void failedCollectionKeepsWhatWasStoredAndIsRetriedFromTheSameDay() {
		given(boampClient.fetchPublishedOn(TODAY.minusDays(2))).willReturn(List.of(record("26-1")));
		given(boampClient.fetchPublishedOn(TODAY.minusDays(1))).willThrow(new IllegalStateException("API down"));

		CollectRun failed = service.collect("BOAMP", CollectTrigger.SCHEDULED, null);

		assertThat(failed.getStatus()).isEqualTo(CollectStatus.FAILED);
		assertThat(failed.getErrorMessage()).isEqualTo("IllegalStateException: API down");
		assertThat(failed.getNoticesNew()).isEqualTo(1);
		assertThat(failed.getFinishedAt()).isNotNull();
		assertThat(rawNotices.count()).isEqualTo(1);
		verify(boampClient, never()).fetchPublishedOn(TODAY);

		given(boampClient.fetchPublishedOn(any())).willReturn(List.of());
		assertThat(service.collect("BOAMP", CollectTrigger.SCHEDULED, null).getSinceDate()).isEqualTo(TODAY.minusDays(2));
	}

	@Test
	void convertsTheCollectedNoticesToTenders() {
		given(boampClient.fetchPublishedOn(any())).willReturn(List.of());
		given(boampClient.fetchPublishedOn(TODAY)).willReturn(List.of(new SourceRecord("26-9", TODAY, """
				{"idweb": "26-9", "nature": "APPEL_OFFRE", "etat": "INITIAL", "objet": "Travaux"}""")));

		service.collect("BOAMP", CollectTrigger.SCHEDULED, null);

		assertThat(tenders.findBySourceAndExternalId("BOAMP", "26-9")).isPresent();
		assertThat(rawNotices.findAll()).allSatisfy(notice -> assertThat(notice.getNormalizedAt()).isNotNull());
	}

	@Test
	void runsLeftRunningByARestartAreMarkedAsFailed() {
		runs.save(new CollectRun("BOAMP", CollectTrigger.SCHEDULED, TODAY, clock.instant()));

		service.closeInterruptedRuns();

		assertThat(runs.findAll()).singleElement()
			.satisfies(run -> {
				assertThat(run.getStatus()).isEqualTo(CollectStatus.FAILED);
				assertThat(run.getErrorMessage()).contains("restart");
			});
	}

	private static SourceRecord record(String idweb) {
		return new SourceRecord(idweb, TODAY, "{\"idweb\": \"" + idweb + "\"}");
	}

}
