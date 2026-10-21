package eu.gpefrance.tenders.collect.ted;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import eu.gpefrance.tenders.ClockConfiguration;
import eu.gpefrance.tenders.TestcontainersConfiguration;
import eu.gpefrance.tenders.collect.CollectRun;
import eu.gpefrance.tenders.collect.CollectService;
import eu.gpefrance.tenders.collect.CollectTrigger;
import eu.gpefrance.tenders.collect.SourceRecord;
import eu.gpefrance.tenders.collect.boamp.BoampClient;
import eu.gpefrance.tenders.tender.NoticeKind;
import eu.gpefrance.tenders.tender.Tender;
import eu.gpefrance.tenders.tender.TenderNotice;
import eu.gpefrance.tenders.tender.TenderRepository;
import eu.gpefrance.tenders.tender.TenderStatus;

/**
 * The TED notices of one procedure make one tender, whatever the order in
 * which they are collected; the real fixtures are varied by their
 * publication number, their changed notice and their deadline.
 */
@SpringBootTest(properties = "collect.disabled-sources=APPROCH,AWS")
@Import(TestcontainersConfiguration.class)
class TedCollectTests {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);

	private static final String PROCEDURE = "5c87fdc2-d20a-42e9-9b11-59f45d4a37af";

	@Autowired
	private CollectService service;

	@Autowired
	private TenderRepository tenders;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private TransactionTemplate transaction;

	@MockitoBean
	private TedClient tedClient;

	@MockitoBean
	private BoampClient boampClient;

	@TestBean
	private Clock clock;

	static Clock clock() {
		return Clock.fixed(Instant.parse("2026-10-05T08:00:00Z"), ClockConfiguration.ZONE);
	}

	@BeforeEach
	void cleanDatabase() {
		jdbc.execute("TRUNCATE collect_run, tender_follow, tender_notice, lot, tender, raw_notice RESTART IDENTITY CASCADE");
		given(boampClient.fetchPublishedOn(any())).willReturn(List.of());
	}

	@Test
	void aChangeNoticeUpdatesTheTenderOfItsProcedure() {
		collect(initial());
		collect(initial(), change("698900-2026", "2026-11-20"));

		assertThat(tenders.count()).isEqualTo(1);
		inTransaction(() -> {
			Tender tender = tenders.findBySourceAndExternalId("TED", "698125-2026").orElseThrow();
			assertThat(tender.getProcedureId()).isEqualTo(PROCEDURE);
			assertThat(tender.getDepartments()).containsExactly("50");
			assertThat(tender.getDeadline()).isEqualTo(Instant.parse("2026-11-20T11:00:00Z"));
			assertThat(tender.getNotices()).extracting(TenderNotice::getExternalId, TenderNotice::getKind)
				.containsExactly(tuple("698125-2026", NoticeKind.INITIAL),
						tuple("698900-2026", NoticeKind.RECTIFICATION));
		});
	}

	@Test
	void anInitialNoticeCollectedAfterItsChangeJoinsTheSameTender() {
		collect(change("698900-2026", "2026-11-20"));
		collect(initial());

		assertThat(tenders.count()).isEqualTo(1);
		inTransaction(() -> {
			Tender tender = tenders.findAll().getFirst();
			assertThat(tender.getExternalId()).isEqualTo("698900-2026");
			assertThat(tender.getDeadline()).isEqualTo(Instant.parse("2026-11-20T11:00:00Z"));
			assertThat(tender.getNotices()).extracting(TenderNotice::getKind)
				.containsExactlyInAnyOrder(NoticeKind.INITIAL, NoticeKind.RECTIFICATION);
		});
	}

	@Test
	void anAwardClosesAndACancellationCancelsTheTenderOfTheProcedure() {
		collect(initial());
		collect(withProcedure(TedNoticeParserTests.fixture("award")));
		assertThat(tenders.findAll()).singleElement()
			.extracting(Tender::getStatus)
			.isEqualTo(TenderStatus.CLOSED);

		jdbc.execute("TRUNCATE collect_run, tender_follow, tender_notice, lot, tender, raw_notice RESTART IDENTITY CASCADE");
		collect(initial());
		collect(change("698901-2026", "2026-11-10").replace("\"cor-buy\"", "\"cancel\""));
		assertThat(tenders.findAll()).singleElement()
			.extracting(Tender::getStatus)
			.isEqualTo(TenderStatus.CANCELLED);
	}

	@Test
	void aReimportCreatesNoDuplicateAndLeavesTheTeamDataAlone() {
		collect(initial(), change("698900-2026", "2026-11-20"));
		Long id = tenders.findAll().getFirst().getId();
		jdbc.update("UPDATE tender SET follow_up_status = 'TO_STUDY', follow_up_comment = 'À étudier' WHERE id = ?",
				id);

		CollectRun again = collect(initial(), change("698900-2026", "2026-11-20"));

		assertThat(again.getNoticesNew()).isZero();
		assertThat(again.getNoticesKnown()).isEqualTo(2);
		assertThat(tenders.count()).isEqualTo(1);
		assertThat(jdbc.queryForMap("SELECT follow_up_status, follow_up_comment FROM tender WHERE id = ?", id))
			.containsEntry("follow_up_status", "TO_STUDY")
			.containsEntry("follow_up_comment", "À étudier");
	}

	@Test
	void aNoticeOutOfScopeMakesNoTender() {
		CollectRun run = collect(record(TedNoticeParserTests.fixture("other")));

		assertThat(tenders.count()).isZero();
		assertThat(run.getNoticesIgnored()).isEqualTo(1);
	}

	private CollectRun collect(String... payloads) {
		given(tedClient.fetchPublishedOn(any()))
			.willReturn(Arrays.stream(payloads).map(TedCollectTests::record).toList());
		return service.collect("TED", CollectTrigger.MANUAL, TODAY);
	}

	private CollectRun collect(SourceRecord record) {
		given(tedClient.fetchPublishedOn(any())).willReturn(List.of(record));
		return service.collect("TED", CollectTrigger.MANUAL, TODAY);
	}

	private void inTransaction(Runnable assertions) {
		transaction.executeWithoutResult(status -> assertions.run());
	}

	private static String initial() {
		return TedNoticeParserTests.fixture("works-lots");
	}

	/**
	 * The initial notice changed, as TED publishes it: a new publication
	 * number citing the eForms identifier of the notice changed.
	 */
	private static String change(String number, String deadline) {
		return initial().replace("\"698125-2026\"", "\"" + number + "\"")
			.replace("\"notice-version\": 1", "\"notice-version\": 2, \"change-reason-code\": \"cor-buy\", "
					+ "\"change-notice-version-identifier\": \"128e9011-31c6-49c9-9b18-76b66293757a-01\"")
			.replace("2026-11-10+01:00", deadline + "+01:00");
	}

	/** The award fixture, given the procedure of the initial notice. */
	private static String withProcedure(String payload) {
		return payload.replace("1ff10451-6429-4570-aaa8-a22113d007b7", PROCEDURE);
	}

	private static SourceRecord record(String payload) {
		String number = payload.replaceAll("(?s).*\"publication-number\": \"([^\"]+)\".*", "$1");
		return new SourceRecord(number, TODAY, payload);
	}

}
