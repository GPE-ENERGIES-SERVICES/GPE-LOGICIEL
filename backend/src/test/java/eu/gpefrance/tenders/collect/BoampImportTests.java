package eu.gpefrance.tenders.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import eu.gpefrance.tenders.ClockConfiguration;
import eu.gpefrance.tenders.TestcontainersConfiguration;
import eu.gpefrance.tenders.collect.boamp.BoampClient;
import eu.gpefrance.tenders.tender.FollowUpStatus;
import eu.gpefrance.tenders.tender.NoticeKind;
import eu.gpefrance.tenders.tender.Tender;
import eu.gpefrance.tenders.tender.TenderNotice;
import eu.gpefrance.tenders.tender.TenderRepository;
import eu.gpefrance.tenders.tender.TenderStatus;

/**
 * Life cycle of the BOAMP data through successive imports: what is created,
 * updated, kept and refused.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class BoampImportTests {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);

	@Autowired
	private CollectService service;

	@Autowired
	private RawNoticeRepository rawNotices;

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
		return Clock.fixed(Instant.parse("2026-10-05T08:00:00Z"), ClockConfiguration.ZONE);
	}

	@BeforeEach
	void cleanDatabase() {
		jdbc.execute("TRUNCATE collect_run, tender_follow, tender_notice, lot, tender, raw_notice RESTART IDENTITY CASCADE");
		jdbc.execute("""
				INSERT INTO app_user (id, email, display_name, password_hash, role, enabled, created_at)
				VALUES (1, 'marie@gpe.test', 'Marie Durand', 'x', 'USER', true, now())
				ON CONFLICT DO NOTHING
				""");
		given(boampClient.fetchPublishedOn(any())).willReturn(List.of());
	}

	@Test
	void anInitialLoadCreatesTheTendersOfTheChosenPeriod() {
		published(TODAY.minusDays(30), initial("26-1", TODAY.minusDays(30), "2026-11-10T12:00:00"));
		published(TODAY, initial("26-2", TODAY, "2026-11-12T12:00:00"));

		CollectRun run = service.collect("BOAMP", CollectTrigger.MANUAL, TODAY.minusDays(30));

		assertThat(run.getStatus()).isEqualTo(CollectStatus.SUCCESS);
		assertThat(run.getSinceDate()).isEqualTo(TODAY.minusDays(30));
		assertThat(run.getNoticesNew()).isEqualTo(2);
		assertThat(tenders.findAll()).extracting(Tender::getExternalId).containsExactlyInAnyOrder("26-1", "26-2");
		assertThat(tenders.findAll()).allSatisfy(tender -> {
			assertThat(tender.getFollowUpStatus()).isEqualTo(FollowUpStatus.NEW);
			assertThat(tender.getStatus()).isEqualTo(TenderStatus.ACTIVE);
		});
	}

	@Test
	void aSecondImportCreatesNoDuplicate() {
		published(TODAY, initial("26-1", TODAY, null), initial("26-2", TODAY, null));
		service.collect("BOAMP", CollectTrigger.MANUAL, TODAY);

		CollectRun second = service.collect("BOAMP", CollectTrigger.MANUAL, TODAY);

		assertThat(second.getNoticesReceived()).isEqualTo(2);
		assertThat(second.getNoticesNew()).isZero();
		assertThat(rawNotices.count()).isEqualTo(2);
		assertThat(tenders.count()).isEqualTo(2);
	}

	@Test
	void aRectificationUpdatesTheTenderAndKeepsTheDataOfTheTeam() {
		published(TODAY.minusDays(1), initial("26-1", TODAY.minusDays(1), "2026-11-10T12:00:00"));
		service.collect("BOAMP", CollectTrigger.MANUAL, TODAY.minusDays(1));
		Long id = tenders.findBySourceAndExternalId("BOAMP", "26-1").orElseThrow().getId();
		jdbc.update("""
				UPDATE tender SET follow_up_status = 'TO_STUDY', follow_up_comment = 'Visite prévue',
				follow_up_by = 'Marie Durand', follow_up_at = now() WHERE id = ?""", id);
		jdbc.update("INSERT INTO tender_follow (user_id, tender_id, created_at) VALUES (1, ?, now())", id);

		published(TODAY, notice("26-5", "RECTIFICATIF", "RECTIFICATIF", "26-1", TODAY, "2026-11-20T12:00:00"));
		service.collect("BOAMP", CollectTrigger.SCHEDULED, null);

		inTransaction(() -> {
			Tender tender = tenders.findAll().getFirst();
			assertThat(tenders.count()).isEqualTo(1);
			assertThat(tender.getId()).isEqualTo(id);
			assertThat(tender.getDeadline()).isEqualTo(Instant.parse("2026-11-20T11:00:00Z"));
			assertThat(tender.getNotices()).extracting(TenderNotice::getKind)
				.containsExactly(NoticeKind.INITIAL, NoticeKind.RECTIFICATION);
			assertThat(tender.getFollowUpStatus()).isEqualTo(FollowUpStatus.TO_STUDY);
			assertThat(tender.getFollowUpComment()).isEqualTo("Visite prévue");
			assertThat(tender.getFollowUpBy()).isEqualTo("Marie Durand");
		});
		assertThat(jdbc.queryForObject("SELECT count(*) FROM tender_follow WHERE tender_id = ?", Long.class, id))
			.isEqualTo(1);
	}

	@Test
	void aTenderNoLongerPublishedIsKept() {
		published(TODAY.minusDays(1), initial("26-1", TODAY.minusDays(1), null));
		service.collect("BOAMP", CollectTrigger.MANUAL, TODAY.minusDays(1));
		given(boampClient.fetchPublishedOn(any())).willReturn(List.of());

		service.collect("BOAMP", CollectTrigger.MANUAL, TODAY);

		assertThat(tenders.findAll()).singleElement()
			.satisfies(tender -> assertThat(tender.getStatus()).isEqualTo(TenderStatus.ACTIVE));
	}

	@Test
	void aNoticeWithoutIdentifierDoesNotStopTheImport() {
		published(TODAY, new SourceRecord(null, TODAY, "{\"objet\": \"Sans identifiant\"}"),
				record(initial("26-1", TODAY, null)));

		CollectRun run = service.collect("BOAMP", CollectTrigger.MANUAL, TODAY);

		assertThat(run.getStatus()).isEqualTo(CollectStatus.SUCCESS);
		assertThat(run.getNoticesReceived()).isEqualTo(2);
		assertThat(run.getNoticesNew()).isEqualTo(1);
		assertThat(tenders.findAll()).extracting(Tender::getExternalId).containsExactly("26-1");
	}

	@Test
	void aNoticeWaitingForItsInitialNoticeIsAppliedOnceItIsCollected() {
		published(TODAY, notice("26-9", "ATTRIBUTION", "INITIAL", "26-1", TODAY, null));
		service.collect("BOAMP", CollectTrigger.MANUAL, TODAY);
		assertThat(tenders.count()).isZero();
		assertThat(rawNotices.findAll()).extracting(RawNotice::getNormalizationNote)
			.containsExactly(NormalizationService.INITIAL_NOT_COLLECTED);

		published(TODAY.minusDays(40), initial("26-1", TODAY.minusDays(40), null));
		service.collect("BOAMP", CollectTrigger.MANUAL, TODAY.minusDays(40));

		inTransaction(() -> {
			Tender tender = tenders.findBySourceAndExternalId("BOAMP", "26-1").orElseThrow();
			assertThat(tender.getStatus()).isEqualTo(TenderStatus.CLOSED);
			assertThat(tender.getNotices()).extracting(TenderNotice::getKind)
				.containsExactly(NoticeKind.INITIAL, NoticeKind.AWARD);
		});
		assertThat(rawNotices.findAll()).extracting(RawNotice::getNormalizationNote).containsOnlyNulls();
	}

	@Test
	void anOlderRectificationDoesNotReplaceANewerDeadline() {
		published(TODAY.minusDays(10), notice("26-2", "RECTIFICATIF", "RECTIFICATIF", "26-1", TODAY.minusDays(10),
				"2026-11-20T12:00:00"));
		service.collect("BOAMP", CollectTrigger.MANUAL, TODAY.minusDays(10));

		published(TODAY.minusDays(20), initial("26-1", TODAY.minusDays(20), "2026-11-10T12:00:00"));
		published(TODAY, notice("26-3", "RECTIFICATIF", "RECTIFICATIF", "26-1", TODAY, "2026-11-25T12:00:00"));
		service.collect("BOAMP", CollectTrigger.MANUAL, TODAY.minusDays(20));

		inTransaction(() -> {
			Tender tender = tenders.findBySourceAndExternalId("BOAMP", "26-1").orElseThrow();
			assertThat(tender.getDeadline()).isEqualTo(Instant.parse("2026-11-25T11:00:00Z"));
			assertThat(tender.getNotices()).hasSize(3);
		});
	}

	@Test
	void theDatabaseRefusesASecondTenderWithTheSameIdentifier() {
		String insert = """
				INSERT INTO tender (source, external_id, title, status, created_at, updated_at)
				VALUES ('BOAMP', '26-1', 'Travaux', 'ACTIVE', now(), now())""";
		jdbc.update(insert);

		assertThatThrownBy(() -> jdbc.update(insert)).isInstanceOf(DuplicateKeyException.class);
	}

	@Test
	void refusesAPeriodInTheFutureOrTooFarBack() {
		assertThatThrownBy(() -> service.collect("BOAMP", CollectTrigger.MANUAL, TODAY.plusDays(1)))
			.isInstanceOf(CollectPeriodException.class);
		assertThatThrownBy(() -> service.collect("BOAMP", CollectTrigger.MANUAL, TODAY.minusDays(400)))
			.isInstanceOf(CollectPeriodException.class);
	}

	private void published(LocalDate day, String... notices) {
		published(day, List.of(notices).stream().map(BoampImportTests::record).toArray(SourceRecord[]::new));
	}

	private void published(LocalDate day, SourceRecord... records) {
		given(boampClient.fetchPublishedOn(day)).willReturn(List.of(records));
	}

	private void inTransaction(Runnable assertions) {
		transaction.executeWithoutResult(status -> assertions.run());
	}

	private static SourceRecord record(String json) {
		String idweb = json.replaceFirst("(?s).*\"idweb\": \"([^\"]+)\".*", "$1");
		String day = json.replaceFirst("(?s).*\"dateparution\": \"([^\"]+)\".*", "$1");
		return new SourceRecord(idweb, LocalDate.parse(day), json);
	}

	private static String initial(String idweb, LocalDate publishedAt, String deadline) {
		return notice(idweb, "APPEL_OFFRE", "INITIAL", null, publishedAt, deadline);
	}

	private static String notice(String idweb, String nature, String etat, String linkedId, LocalDate publishedAt,
			String deadline) {
		String gestion = deadline == null ? "{}"
				: "{\\\"INDEXATION\\\": {\\\"DATE_LIMITE_REPONSE\\\": \\\"" + deadline + "\\\"}}";
		return """
				{"idweb": "%s", "nature": "%s", "etat": "%s", "objet": "Travaux d'électricité %s",
				 "dateparution": "%s", "code_departement": ["69"], "type_marche": ["TRAVAUX"],
				 "annonce_lie": %s, "gestion": "%s", "donnees": "{}"}
				""".formatted(idweb, nature, etat, idweb, publishedAt,
				linkedId == null ? "null" : "[\"" + linkedId + "\"]", gestion);
	}

}
