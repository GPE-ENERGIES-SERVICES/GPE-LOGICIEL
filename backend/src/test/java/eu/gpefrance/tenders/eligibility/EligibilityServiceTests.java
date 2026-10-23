package eu.gpefrance.tenders.eligibility;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.transaction.support.TransactionTemplate;

import eu.gpefrance.tenders.ClockConfiguration;
import eu.gpefrance.tenders.TestcontainersConfiguration;
import eu.gpefrance.tenders.alert.AlertRequest;
import eu.gpefrance.tenders.alert.AlertResponse;
import eu.gpefrance.tenders.alert.AlertService;
import eu.gpefrance.tenders.document.DceLinkService;
import eu.gpefrance.tenders.tender.MarketType;
import eu.gpefrance.tenders.tender.Tender;
import eu.gpefrance.tenders.tender.TenderDetails;
import eu.gpefrance.tenders.tender.TenderRepository;

/**
 * Eligibility of the consultation files by the active saved searches of the
 * users, with no platform allowed (the default): eligible links are shown as
 * not allowed to be retrieved automatically, and nothing is queued.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class EligibilityServiceTests {

	private static final Instant NOW = Instant.parse("2026-10-21T08:00:00Z");

	@Autowired
	private EligibilityService eligibility;

	@Autowired
	private AlertService alerts;

	@Autowired
	private TenderRepository tenders;

	@Autowired
	private TenderEligibilityRepository eligibilities;

	@Autowired
	private DceLinkService dceLinks;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private TransactionTemplate transaction;

	@TestBean
	private Clock clock;

	static Clock clock() {
		return Clock.fixed(NOW, ClockConfiguration.ZONE);
	}

	@BeforeEach
	void cleanDatabase() {
		jdbc.execute("TRUNCATE alert, tender_eligibility, dce_link_event, dce_link, tender_follow, tender_notice, lot, "
				+ "tender, raw_notice RESTART IDENTITY CASCADE");
		jdbc.execute("""
				INSERT INTO app_user (id, email, display_name, password_hash, role, enabled, created_at)
				VALUES (1, 'marie@gpe.test', 'Marie Durand', 'x', 'USER', true, now()),
				       (2, 'admin@gpe.test', 'Admin GPE', 'x', 'ADMIN', true, now())
				ON CONFLICT DO NOTHING
				""");
	}

	@Test
	void aTenderMatchingNoActiveSavedSearchIsNotEligibleAndItsLinkIsNotQueued() {
		Tender cleaning = save("26-1", "Nettoyage des locaux", "13", "90910000");
		link(cleaning);
		alerts.create(alert("Éclairage", List.of("éclairage"), true), 1L);

		eligibility.evaluateOutOfDate();

		assertThat(state(cleaning)).isEqualTo(EligibilityState.NOT_ELIGIBLE);
		assertThat(reason(cleaning)).isEqualTo("Ne correspond à aucune veille active");
		assertThat(linkStatus(cleaning)).isEqualTo("NOT_ELIGIBLE");
	}

	@Test
	void aTenderMatchingSeveralSavedSearchesIsEligibleOnceAndNeverQueuedWithoutAnAllowedPlatform() {
		Tender lighting = save("26-1", "Travaux d'éclairage public", "13", "45316110");
		link(lighting);
		AlertResponse first = alerts.create(alert("Éclairage", List.of("éclairage"), true), 1L);
		AlertResponse second = alerts.create(alert("Travaux 13", List.of("travaux"), true), 2L);

		eligibility.evaluateOutOfDate();

		assertThat(state(lighting)).isEqualTo(EligibilityState.ELIGIBLE);
		assertThat(eligibilities.findById(lighting.getId()).orElseThrow().getMatchedAlertIds())
			.containsExactly(first.id(), second.id());
		assertThat(reason(lighting)).isEqualTo("Correspond à 2 veilles actives").doesNotContain("Éclairage");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM tender_eligibility", Long.class)).isEqualTo(1);
		assertThat(linkStatus(lighting)).isEqualTo("AUTOMATED_ACCESS_NOT_AUTHORIZED");
		assertThat(jdbc.queryForObject("SELECT status_detail FROM dce_link", String.class))
			.contains("aucune plateforme n'est autorisée", "action manuelle autorisée");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM dce_link WHERE status = 'ELIGIBLE_PENDING'", Long.class))
			.isZero();
	}

	@Test
	void anInactiveSavedSearchMakesNothingEligibleButAnotherActiveOneStillDoes() {
		Tender lighting = save("26-1", "Travaux d'éclairage public", "13", "45316110");
		alerts.create(alert("Éclairage", List.of("éclairage"), false), 1L);

		eligibility.evaluateOutOfDate();
		assertThat(state(lighting)).isEqualTo(EligibilityState.NOT_ELIGIBLE);
		assertThat(reason(lighting)).isEqualTo("Aucune veille active");

		AlertResponse other = alerts.create(alert("Public", List.of("public"), true), 2L);
		eligibility.evaluateOutOfDate();
		assertThat(state(lighting)).isEqualTo(EligibilityState.ELIGIBLE);
		assertThat(eligibilities.findById(lighting.getId()).orElseThrow().getMatchedAlertIds())
			.containsExactly(other.id());
	}

	@Test
	void aTenderBecomesEligibleWhenASavedSearchChangesThenNotWhenItIsDeactivated() {
		Tender lighting = save("26-1", "Travaux d'éclairage public", "13", "45316110");
		link(lighting);
		AlertResponse created = alerts.create(alert("Chauffage", List.of("chauffage"), true), 1L);
		eligibility.evaluateOutOfDate();
		assertThat(state(lighting)).isEqualTo(EligibilityState.NOT_ELIGIBLE);

		alerts.update(created.id(), alert("Éclairage", List.of("éclairage"), true), 1L);
		assertThat(eligibility.outOfDate()).isEqualTo(1);
		eligibility.evaluateOutOfDate();
		assertThat(state(lighting)).isEqualTo(EligibilityState.ELIGIBLE);
		assertThat(linkStatus(lighting)).isEqualTo("AUTOMATED_ACCESS_NOT_AUTHORIZED");

		alerts.update(created.id(), alert("Éclairage", List.of("éclairage"), false), 1L);
		eligibility.evaluateOutOfDate();
		assertThat(state(lighting)).isEqualTo(EligibilityState.NOT_ELIGIBLE);
		assertThat(linkStatus(lighting)).isEqualTo("NOT_ELIGIBLE");
		assertThat(jdbc.queryForList("SELECT to_status FROM dce_link_event ORDER BY id", String.class))
			.containsExactly("NOT_ELIGIBLE", "AUTOMATED_ACCESS_NOT_AUTHORIZED", "NOT_ELIGIBLE");
	}

	@Test
	void aSavedSearchThatCannotDecideMakesTheTenderIndeterminateAndNothingIsRetrieved() {
		Tender withoutCpv = save("26-1", "Travaux", "13", null);
		link(withoutCpv);
		alerts.create(new AlertRequest("CPV électricité", List.of(), null, List.of(), List.of(), List.of(),
				List.of("4531"), null, null, true), 1L);

		eligibility.evaluateOutOfDate();

		assertThat(state(withoutCpv)).isEqualTo(EligibilityState.INDETERMINATE);
		assertThat(reason(withoutCpv)).isEqualTo("Données insuffisantes pour 1 veille : code CPV non indiqué");
		assertThat(linkStatus(withoutCpv)).isEqualTo("AVAILABLE");
		assertThat(jdbc.queryForObject("SELECT status_detail FROM dce_link", String.class))
			.startsWith("Éligibilité indéterminée");
	}

	@Test
	void aClosedTenderIsNeverEligible() {
		Tender closed = save("26-1", "Travaux d'éclairage public", "13", "45316110");
		jdbc.update("UPDATE tender SET status = 'CLOSED' WHERE id = ?", closed.getId());
		alerts.create(alert("Éclairage", List.of("éclairage"), true), 1L);

		eligibility.evaluateOutOfDate();

		assertThat(state(closed)).isEqualTo(EligibilityState.NOT_ELIGIBLE);
		assertThat(reason(closed)).isEqualTo("Consultation non ouverte (close)");
	}

	@Test
	void evaluatingAgainChangesNothingAndAnInterruptedEvaluationGoesOn() {
		for (int i = 1; i <= 5; i++) {
			link(save("26-" + i, "Travaux d'éclairage n°" + i, "13", "45316110"));
		}
		alerts.create(alert("Éclairage", List.of("éclairage"), true), 1L);
		// An evaluation stopped after two tenders: their eligibility is up to date, not the others.
		long revision = jdbc.queryForObject("SELECT revision FROM alert_revision", Long.class);
		jdbc.update("""
				INSERT INTO tender_eligibility (tender_id, state, reason, alert_revision, evaluated_at)
				SELECT id, 'ELIGIBLE', 'Correspond à 1 veille active', ?, ? FROM tender ORDER BY id LIMIT 2""",
				revision, java.sql.Timestamp.from(NOW));

		assertThat(eligibility.outOfDate()).isEqualTo(3);
		assertThat(eligibility.evaluateOutOfDate()).isEqualTo(3);
		Map<String, Object> events = jdbc.queryForMap("SELECT count(*) AS n FROM dce_link_event");

		assertThat(eligibility.evaluateOutOfDate()).isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM tender_eligibility", Long.class)).isEqualTo(5);
		assertThat(jdbc.queryForMap("SELECT count(*) AS n FROM dce_link_event")).isEqualTo(events);
	}

	@Test
	void theCriteriaOfTheCompanyAreNeitherRequiredNorChanged() {
		Tender excluded = save("26-1", "Travaux d'éclairage public", "13", "45316110");
		jdbc.update("UPDATE tender SET retained = false, filter_reason = 'Écarté : hors zone' WHERE id = ?",
				excluded.getId());
		alerts.create(alert("Éclairage", List.of("éclairage"), true), 1L);

		eligibility.evaluateOutOfDate();

		assertThat(state(excluded)).isEqualTo(EligibilityState.ELIGIBLE);
		assertThat(jdbc.queryForMap("SELECT retained, filter_reason FROM tender WHERE id = ?", excluded.getId()))
			.containsEntry("retained", false)
			.containsEntry("filter_reason", "Écarté : hors zone");
	}

	@Test
	void aRetrievedOrFailedLinkIsNeverChangedByAnEvaluation() {
		Tender lighting = save("26-1", "Travaux d'éclairage public", "13", "45316110");
		link(lighting);
		jdbc.update("UPDATE dce_link SET status = 'FETCHED', status_detail = '3 fichiers récupérés'");
		alerts.create(alert("Chauffage", List.of("chauffage"), true), 1L);

		eligibility.evaluateOutOfDate();

		assertThat(state(lighting)).isEqualTo(EligibilityState.NOT_ELIGIBLE);
		assertThat(linkStatus(lighting)).isEqualTo("FETCHED");
		assertThat(jdbc.queryForObject("SELECT status_detail FROM dce_link", String.class))
			.isEqualTo("3 fichiers récupérés");
	}

	@Test
	void aNewLinkOfAnEvaluatedTenderIsReviewedAtTheNextEvaluation() {
		Tender lighting = save("26-1", "Travaux d'éclairage public", "13", "45316110");
		alerts.create(alert("Éclairage", List.of("éclairage"), true), 1L);
		eligibility.evaluateOutOfDate();
		assertThat(eligibility.outOfDate()).isZero();

		transaction.executeWithoutResult(status -> dceLinks.record(lighting.getId(), "TED",
				"https://plateforme.test/dce/2", NOW));

		assertThat(eligibility.outOfDate()).isEqualTo(1);
		eligibility.evaluateOutOfDate();
		assertThat(linkStatus(lighting)).isEqualTo("AUTOMATED_ACCESS_NOT_AUTHORIZED");
	}

	private EligibilityState state(Tender tender) {
		return eligibilities.findById(tender.getId()).orElseThrow().getState();
	}

	private String reason(Tender tender) {
		return eligibilities.findById(tender.getId()).orElseThrow().getReason();
	}

	private String linkStatus(Tender tender) {
		return jdbc.queryForObject("SELECT status FROM dce_link WHERE tender_id = ? ORDER BY id LIMIT 1", String.class,
				tender.getId());
	}

	private void link(Tender tender) {
		jdbc.update("""
				INSERT INTO dce_link (tender_id, source, url, platform, status, first_seen_at, last_seen_at)
				VALUES (?, 'BOAMP', ?, 'plateforme.test', 'AVAILABLE', now(), now())""", tender.getId(),
				"https://plateforme.test/dce/" + tender.getExternalId());
	}

	private static AlertRequest alert(String name, List<String> keywords, boolean active) {
		return new AlertRequest(name, keywords, null, List.of("13"), List.of(MarketType.WORKS), List.of(), List.of(),
				null, null, active);
	}

	private Tender save(String externalId, String title, String department, String cpv) {
		return tenders.save(new Tender("BOAMP", externalId,
				new TenderDetails(title, null, null, null, null, List.of(department), List.of(MarketType.WORKS), null,
						cpv == null ? List.of() : List.of(cpv), new BigDecimal("200000"), NOW.atZone(ClockConfiguration.ZONE)
							.toLocalDate(), NOW.plusSeconds(86_400 * 30), null, null, List.of()),
				NOW));
	}

}
