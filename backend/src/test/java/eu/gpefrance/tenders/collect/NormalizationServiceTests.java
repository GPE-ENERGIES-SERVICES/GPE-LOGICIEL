package eu.gpefrance.tenders.collect;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import eu.gpefrance.tenders.TestcontainersConfiguration;
import eu.gpefrance.tenders.tender.NoticeKind;
import eu.gpefrance.tenders.tender.Tender;
import eu.gpefrance.tenders.tender.TenderNotice;
import eu.gpefrance.tenders.tender.TenderRepository;
import eu.gpefrance.tenders.tender.TenderStatus;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class NormalizationServiceTests {

	@Autowired
	private NormalizationService service;

	@Autowired
	private RawNoticeRepository rawNotices;

	@Autowired
	private TenderRepository tenders;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private TransactionTemplate transaction;

	@BeforeEach
	void cleanDatabase() {
		jdbc.execute("TRUNCATE tender_notice, lot, tender, raw_notice RESTART IDENTITY CASCADE");
	}

	@Test
	void convertsRealNoticesAndExplainsTheOnesItSkips() {
		for (String fixture : List.of("eforms-works", "eforms-services", "fns-lots", "fns-initial", "mapa",
				"eforms-change", "fns-rectification", "award", "cancellation")) {
			store(fixture.hashCode() + "", fixture(fixture));
		}

		assertThat(service.normalizePending("BOAMP").total()).isEqualTo(9);

		assertThat(tenders.findAll()).extracting(Tender::getExternalId)
			.containsExactlyInAnyOrder("26-94229", "26-94446", "26-94039", "26-94116", "26-93202", "26-91123");
		assertThat(rawNotices.findAll()).allSatisfy(notice -> assertThat(notice.getNormalizedAt()).isNotNull());
		assertThat(rawNotices.findAll()).extracting(RawNotice::getNormalizationNote)
			.filteredOn(note -> note != null)
			.containsOnly("Initial notice not collected")
			.hasSize(3);
	}

	@Test
	void storesLotsWithTheTender() {
		store("26-94446", fixture("eforms-services"));

		service.normalizePending("BOAMP");

		inTransaction(() -> {
			Tender tender = tenders.findBySourceAndExternalId("BOAMP", "26-94446").orElseThrow();
			assertThat(tender.getLots()).hasSize(4);
			assertThat(tender.getStatus()).isEqualTo(TenderStatus.ACTIVE);
			assertThat(tender.getNotices()).singleElement()
				.extracting(TenderNotice::getKind)
				.isEqualTo(NoticeKind.INITIAL);
		});
	}

	@Test
	void attachesARectificationAndAppliesTheNewDeadline() {
		store("26-100", notice("26-100", "APPEL_OFFRE", "INITIAL", null, "2026-11-10T12:00:00"));
		store("26-101", notice("26-101", "RECTIFICATIF", "RECTIFICATIF", "26-100", "2026-11-20T12:00:00"));

		service.normalizePending("BOAMP");

		inTransaction(() -> {
			assertThat(tenders.count()).isEqualTo(1);
			Tender tender = tenders.findBySourceAndExternalId("BOAMP", "26-100").orElseThrow();
			assertThat(tender.getDeadline()).isEqualTo(Instant.parse("2026-11-20T11:00:00Z"));
			assertThat(tender.getNotices()).extracting(TenderNotice::getKind)
				.containsExactly(NoticeKind.INITIAL, NoticeKind.RECTIFICATION);
			assertThat(tender.getNotices().getFirst().getDeadline()).isEqualTo(Instant.parse("2026-11-10T11:00:00Z"));
		});
	}

	@Test
	void attachesACompleteRectificationToTheInitialNotice() {
		store("26-100", notice("26-100", "APPEL_OFFRE", "INITIAL", null, "2026-11-10T12:00:00"));
		store("26-102", notice("26-102", "APPEL_OFFRE", "RECTIFICATIF", "26-100", "2026-11-25T12:00:00"));

		service.normalizePending("BOAMP");

		inTransaction(() -> {
			assertThat(tenders.findAll()).singleElement().satisfies(tender -> {
				assertThat(tender.getExternalId()).isEqualTo("26-100");
				assertThat(tender.getDeadline()).isEqualTo(Instant.parse("2026-11-25T11:00:00Z"));
				assertThat(tender.getNotices()).hasSize(2);
			});
		});
	}

	@Test
	void anAwardClosesTheTenderAndACancellationCancelsIt() {
		store("26-100", notice("26-100", "APPEL_OFFRE", "INITIAL", null, "2026-11-10T12:00:00"));
		store("26-200", notice("26-200", "APPEL_OFFRE", "INITIAL", null, "2026-11-10T12:00:00"));
		store("26-101", notice("26-101", "ATTRIBUTION", "INITIAL", "26-100", null));
		store("26-201", notice("26-201", "ANNULATION", "INITIAL", "26-200", null));

		service.normalizePending("BOAMP");

		assertThat(tenders.findBySourceAndExternalId("BOAMP", "26-100").orElseThrow().getStatus())
			.isEqualTo(TenderStatus.CLOSED);
		assertThat(tenders.findBySourceAndExternalId("BOAMP", "26-200").orElseThrow().getStatus())
			.isEqualTo(TenderStatus.CANCELLED);
	}

	@Test
	void processesEachNoticeOnlyOnce() {
		store("26-100", notice("26-100", "APPEL_OFFRE", "INITIAL", null, null));
		service.normalizePending("BOAMP");

		assertThat(service.normalizePending("BOAMP").total()).isZero();
		assertThat(tenders.count()).isEqualTo(1);
	}

	@Test
	void recordsAnUnreadableNoticeWithoutBlockingTheOthers() {
		store("26-100", "{\"idweb\": \"26-100\", \"nature\": \"APPEL_OFFRE\", \"objet\": \"Travaux\", "
				+ "\"donnees\": \"not json\"}");
		store("26-200", notice("26-200", "APPEL_OFFRE", "INITIAL", null, null));

		assertThat(service.normalizePending("BOAMP")).isEqualTo(new NormalizationReport(1, 0, 0, 1));

		assertThat(tenders.findAll()).extracting(Tender::getExternalId).containsExactly("26-200");
		assertThat(rawNotices.findAll()).extracting(RawNotice::getNormalizationNote)
			.anySatisfy(note -> assertThat(note).startsWith("Error: "));
	}

	@Test
	void aLateInitialNoticeJoinsTheTenderOfItsCompleteRectificationAndStaysThere() {
		// A complete rectification collected before its initial notice makes its own tender.
		store("26-102", notice("26-102", "APPEL_OFFRE", "RECTIFICATIF", "26-100", "2026-11-25T12:00:00"));
		service.normalizePending("BOAMP");
		store("26-100", notice("26-100", "APPEL_OFFRE", "INITIAL", null, "2026-11-10T12:00:00"));
		assertThat(service.normalizePending("BOAMP")).isEqualTo(new NormalizationReport(0, 1, 0, 0));

		// Read again, each notice stays with its tender.
		jdbc.update("UPDATE raw_notice SET normalized_at = NULL");
		assertThat(service.normalizePending("BOAMP")).isEqualTo(new NormalizationReport(0, 2, 0, 0));

		inTransaction(() -> {
			assertThat(tenders.findAll()).singleElement().satisfies(tender -> {
				assertThat(tender.getExternalId()).isEqualTo("26-102");
				assertThat(tender.getNotices()).extracting(TenderNotice::getExternalId)
					.containsExactlyInAnyOrder("26-102", "26-100");
				assertThat(tender.getDeadline()).isEqualTo(Instant.parse("2026-11-25T11:00:00Z"));
			});
		});
	}

	@Test
	void aRectificationCitingAnUnknownNoticeAttachesNothing() {
		store("26-102", notice("26-102", "APPEL_OFFRE", "RECTIFICATIF", "26-999", "2026-11-25T12:00:00"));
		store("26-100", notice("26-100", "APPEL_OFFRE", "INITIAL", null, "2026-11-10T12:00:00"));

		service.normalizePending("BOAMP");

		inTransaction(() -> {
			assertThat(tenders.findAll()).extracting(Tender::getExternalId).containsExactlyInAnyOrder("26-102", "26-100");
			assertThat(tenders.findAll()).allSatisfy(tender -> assertThat(tender.getNotices()).hasSize(1));
		});
	}

	@Test
	void retriesANoticeInErrorAtTheNextRun() {
		store("26-100", "{\"idweb\": \"26-100\", \"nature\": \"APPEL_OFFRE\", \"objet\": \"Travaux\", "
				+ "\"donnees\": \"not json\"}");
		assertThat(service.normalizePending("BOAMP")).isEqualTo(new NormalizationReport(0, 0, 0, 1));
		// The cause is fixed meanwhile, here by a readable payload.
		jdbc.update("UPDATE raw_notice SET payload = CAST(? AS jsonb)", notice("26-100", "APPEL_OFFRE", "INITIAL", null, null));

		assertThat(service.normalizePending("BOAMP")).isEqualTo(new NormalizationReport(1, 0, 0, 0));
		assertThat(tenders.findAll()).extracting(Tender::getExternalId).containsExactly("26-100");
		assertThat(service.normalizePending("BOAMP").total()).isZero();
	}

	private void store(String idweb, String json) {
		transaction.executeWithoutResult(
				status -> rawNotices.insertIfAbsent("BOAMP", idweb, json, LocalDate.of(2026, 10, 1)));
	}

	private void inTransaction(Runnable assertions) {
		transaction.executeWithoutResult(status -> assertions.run());
	}

	private static String notice(String idweb, String nature, String etat, String linkedId, String deadline) {
		String gestion = deadline == null ? "{}"
				: "{\\\"INDEXATION\\\": {\\\"DATE_LIMITE_REPONSE\\\": \\\"" + deadline + "\\\"}}";
		return """
				{"idweb": "%s", "nature": "%s", "etat": "%s", "objet": "Travaux d'électricité %s",
				 "dateparution": "2026-10-01", "code_departement": ["69"], "type_marche": ["TRAVAUX"],
				 "annonce_lie": %s, "gestion": "%s", "donnees": "{}"}
				""".formatted(idweb, nature, etat, idweb, linkedId == null ? "null" : "[\"" + linkedId + "\"]",
				gestion);
	}

	private String fixture(String name) {
		try (InputStream in = getClass().getResourceAsStream("/boamp/" + name + ".json")) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
