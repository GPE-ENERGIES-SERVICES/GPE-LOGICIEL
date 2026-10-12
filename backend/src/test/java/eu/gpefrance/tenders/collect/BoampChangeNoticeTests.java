package eu.gpefrance.tenders.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;

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
import eu.gpefrance.tenders.collect.boamp.BoampClient;
import eu.gpefrance.tenders.tender.NoticeKind;
import eu.gpefrance.tenders.tender.Tender;
import eu.gpefrance.tenders.tender.TenderNotice;
import eu.gpefrance.tenders.tender.TenderRepository;
import eu.gpefrance.tenders.tender.TenderStatus;

/**
 * Two real BOAMP notices of one procedure, published with etat = INITIAL
 * although each is an eForms change notice: the second one, which pushes the
 * deadline from 12 to 16 October 2026, updates the tender of the first one
 * instead of making a second tender, and opens it again if its first
 * deadline passed. The decision of the team is kept.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class BoampChangeNoticeTests {

	/** After the first deadline (12/10 10:00 UTC), before the second one (16/10 10:00 UTC). */
	private static final Instant NOW = Instant.parse("2026-10-12T12:00:00Z");

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);

	@Autowired
	private CollectService service;

	@Autowired
	private TenderRepository tenders;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private TransactionTemplate transaction;

	@MockitoBean
	private BoampClient boampClient;

	@TestBean
	private Clock clock;

	static Clock clock() {
		return Clock.fixed(NOW, ClockConfiguration.ZONE);
	}

	@BeforeEach
	void cleanDatabase() {
		jdbc.execute("TRUNCATE collect_run, dce_link, tender_match, tender_follow, tender_notice, lot, tender, "
				+ "raw_notice RESTART IDENTITY CASCADE");
	}

	@Test
	void theSecondChangeUpdatesTheTenderOfTheFirstAndOpensItAgain() {
		collect(fixture("change-as-initial-1"));
		Tender first = tenders.findBySourceAndExternalId("BOAMP", "26-92510").orElseThrow();
		// Its deadline has passed: closed by the collection, then answered by the team.
		assertThat(first.getStatus()).isEqualTo(TenderStatus.CLOSED);
		jdbc.update("UPDATE tender SET follow_up_status = 'SUBMITTED', follow_up_comment = 'Offre déposée', "
				+ "follow_up_by = 'Marie Durand' WHERE id = ?", first.getId());

		CollectRun run = collect(fixture("change-as-initial-1"), fixture("change-as-initial-2"));

		assertThat(run.getTendersCreated()).isZero();
		assertThat(run.getTendersUpdated()).isEqualTo(1);
		assertThat(tenders.findAll()).singleElement().satisfies(tender -> {
			assertThat(tender.getId()).isEqualTo(first.getId());
			assertThat(tender.getDeadline()).isEqualTo(Instant.parse("2026-10-16T10:00:00Z"));
			assertThat(tender.getStatus()).isEqualTo(TenderStatus.ACTIVE);
		});
		assertThat(jdbc.queryForMap("SELECT follow_up_status, follow_up_comment, follow_up_by FROM tender"))
			.containsEntry("follow_up_status", "SUBMITTED")
			.containsEntry("follow_up_comment", "Offre déposée")
			.containsEntry("follow_up_by", "Marie Durand");
		transaction.executeWithoutResult(status -> assertThat(tenders.findAll().getFirst().getNotices())
			.extracting(TenderNotice::getExternalId, TenderNotice::getKind)
			.containsExactly(tuple("26-92510", NoticeKind.RECTIFICATION),
					tuple("26-97093", NoticeKind.RECTIFICATION)));
	}

	@Test
	void aTenderAwardedIsNeverOpenedAgainByALaterDeadline() {
		collect(fixture("change-as-initial-1"));
		Tender first = tenders.findBySourceAndExternalId("BOAMP", "26-92510").orElseThrow();
		jdbc.update("""
				INSERT INTO raw_notice (source, external_id, payload, published_at, collected_at, normalized_at)
				VALUES ('BOAMP', '26-99999', CAST('{}' AS jsonb), DATE '2026-10-10', now(), now())""");
		Long award = jdbc.queryForObject("SELECT id FROM raw_notice WHERE external_id = '26-99999'", Long.class);
		jdbc.update("""
				INSERT INTO tender_notice (tender_id, raw_notice_id, external_id, kind, published_at, source)
				VALUES (?, ?, '26-99999', 'AWARD', DATE '2026-10-10', 'BOAMP')""", first.getId(), award);

		collect(fixture("change-as-initial-2"));

		assertThat(tenders.findById(first.getId()).orElseThrow().getStatus()).isEqualTo(TenderStatus.CLOSED);
	}

	private CollectRun collect(String... payloads) {
		given(boampClient.fetchPublishedOn(any())).willReturn(
				Arrays.stream(payloads).map(BoampChangeNoticeTests::record).toList());
		return service.collect("BOAMP", CollectTrigger.MANUAL, TODAY);
	}

	private static SourceRecord record(String payload) {
		String idweb = payload.replaceAll("(?s).*\"idweb\": \"([^\"]+)\".*", "$1");
		String published = payload.replaceAll("(?s).*\"dateparution\": \"([^\"]+)\".*", "$1");
		return new SourceRecord(idweb, LocalDate.parse(published), payload);
	}

	private static String fixture(String name) {
		try (InputStream in = BoampChangeNoticeTests.class.getResourceAsStream("/boamp/" + name + ".json")) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
