package eu.gpefrance.tenders.eligibility;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.convention.TestBean;

import eu.gpefrance.tenders.ClockConfiguration;
import eu.gpefrance.tenders.TestcontainersConfiguration;
import eu.gpefrance.tenders.alert.AlertRequest;
import eu.gpefrance.tenders.alert.AlertResponse;
import eu.gpefrance.tenders.alert.AlertService;
import eu.gpefrance.tenders.document.DceStatusService;
import eu.gpefrance.tenders.tender.MarketType;
import eu.gpefrance.tenders.tender.Tender;
import eu.gpefrance.tenders.tender.TenderDetails;
import eu.gpefrance.tenders.tender.TenderRepository;
import eu.gpefrance.tenders.user.Role;
import eu.gpefrance.tenders.user.UpdateUserRequest;
import eu.gpefrance.tenders.user.UserService;

/**
 * Only the active saved searches of an enabled account make a consultation
 * file eligible. Disabling or enabling an account goes through the service
 * of the administration of the accounts, as from its screen; its saved
 * searches, the tenders, links and documents are never changed nor deleted.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AccountStatusEligibilityTests {

	private static final Instant NOW = Instant.parse("2026-10-22T08:00:00Z");

	private static final long MARIE = 1L;

	private static final long PAUL = 3L;

	@Autowired
	private EligibilityService eligibility;

	@Autowired
	private AlertService alerts;

	@Autowired
	private UserService users;

	@Autowired
	private DceStatusService dceStatus;

	@Autowired
	private TenderRepository tenders;

	@Autowired
	private TenderEligibilityRepository eligibilities;

	@Autowired
	private JdbcTemplate jdbc;

	@TestBean
	private Clock clock;

	static Clock clock() {
		return Clock.fixed(NOW, ClockConfiguration.ZONE);
	}

	@BeforeEach
	void cleanDatabase() {
		jdbc.execute("TRUNCATE alert, tender_eligibility, dce_link_event, dce_link, document_page, document, dossier, "
				+ "tender_follow, tender_notice, lot, tender, raw_notice RESTART IDENTITY CASCADE");
		jdbc.execute("DELETE FROM app_user");
		jdbc.execute("""
				INSERT INTO app_user (id, email, display_name, password_hash, role, enabled, created_at)
				VALUES (1, 'marie@gpe.test', 'Marie Durand', 'x', 'USER', true, now()),
				       (2, 'admin@gpe.test', 'Admin GPE', 'x', 'ADMIN', true, now()),
				       (3, 'paul@gpe.test', 'Paul Martin', 'x', 'USER', true, now())
				""");
	}

	@Test
	void theSavedSearchOfADisabledAccountNoLongerCountsAndCountsAgainOnceEnabled() {
		Tender lighting = save("26-1", "Travaux d'éclairage public");
		Long link = link(lighting);
		jdbc.update("""
				INSERT INTO dossier (id, tender_id, version, status, uploaded_by, uploaded_at) VALUES (1, ?, 1, 'DONE', 'Marie', now());
				INSERT INTO document (dossier_id, file_name, storage_path, size_bytes, sha256, piece_type, state)
				VALUES (1, 'RC.pdf', 'tenders/1/v1/files/RC.pdf', 50, repeat('a', 64), 'RC', 'READ')""",
				lighting.getId());
		jdbc.update("UPDATE tender SET retained = false, filter_reason = 'Écarté : hors zone' WHERE id = ?",
				lighting.getId());
		AlertResponse alert = alerts.create(lighting(), MARIE);
		eligibility.evaluateOutOfDate();
		assertThat(state(lighting)).isEqualTo(EligibilityState.ELIGIBLE);

		users.update(MARIE, new UpdateUserRequest("Marie Durand", Role.USER, false, null));

		assertThat(eligibility.outOfDate()).isEqualTo(1);
		eligibility.evaluateOutOfDate();
		assertThat(state(lighting)).isEqualTo(EligibilityState.NOT_ELIGIBLE);
		assertThat(eligibilities.findById(lighting.getId()).orElseThrow().getReason()).isEqualTo("Aucune veille active");
		assertThat(linkStatus(link)).isEqualTo("NOT_ELIGIBLE");
		// Nothing is deleted nor changed: the saved search stays active, the link and the document stay.
		assertThat(jdbc.queryForMap("SELECT active, user_id FROM alert WHERE id = ?", alert.id()))
			.containsEntry("active", true)
			.containsEntry("user_id", MARIE);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM dce_link", Long.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM document", Long.class)).isEqualTo(1);
		assertThat(jdbc.queryForMap("SELECT retained, filter_reason FROM tender WHERE id = ?", lighting.getId()))
			.containsEntry("retained", false)
			.containsEntry("filter_reason", "Écarté : hors zone");

		users.update(MARIE, new UpdateUserRequest("Marie Durand", Role.USER, true, null));

		assertThat(eligibility.outOfDate()).isEqualTo(1);
		eligibility.evaluateOutOfDate();
		assertThat(state(lighting)).isEqualTo(EligibilityState.ELIGIBLE);
		assertThat(eligibilities.findById(lighting.getId()).orElseThrow().getMatchedAlertIds())
			.containsExactly(alert.id());
		assertThat(linkStatus(link)).isEqualTo("AUTOMATED_ACCESS_NOT_AUTHORIZED");
	}

	@Test
	void anotherEnabledAccountMatchingTheTenderKeepsItEligible() {
		Tender lighting = save("26-1", "Travaux d'éclairage public");
		alerts.create(lighting(), MARIE);
		AlertResponse paul = alerts.create(new AlertRequest("Public", List.of("public"), null, List.of(), List.of(),
				List.of(), List.of(), null, null, true), PAUL);
		eligibility.evaluateOutOfDate();

		users.update(MARIE, new UpdateUserRequest("Marie Durand", Role.USER, false, null));
		eligibility.evaluateOutOfDate();

		assertThat(state(lighting)).isEqualTo(EligibilityState.ELIGIBLE);
		assertThat(eligibilities.findById(lighting.getId()).orElseThrow().getMatchedAlertIds())
			.containsExactly(paul.id());
	}

	@Test
	void disablingMarksOnlyTheTendersItsSavedSearchesReferAndEnablingOnlyTheOpenOnes() {
		Tender lighting = save("26-1", "Travaux d'éclairage public");
		Tender cleaning = save("26-2", "Nettoyage des locaux");
		Tender closed = save("26-3", "Travaux d'éclairage du stade");
		jdbc.update("UPDATE tender SET status = 'CLOSED' WHERE id = ?", closed.getId());
		alerts.create(lighting(), MARIE);
		eligibility.evaluateOutOfDate();
		assertThat(eligibility.outOfDate()).isZero();

		users.update(MARIE, new UpdateUserRequest("Marie Durand", Role.USER, false, null));
		assertThat(outOfDate()).containsExactly(lighting.getId());
		eligibility.evaluateOutOfDate();

		users.update(MARIE, new UpdateUserRequest("Marie Durand", Role.USER, true, null));
		assertThat(outOfDate()).containsExactlyInAnyOrder(lighting.getId(), cleaning.getId());
	}

	@Test
	void anAccountWithoutActiveSavedSearchChangesNothing() {
		save("26-1", "Travaux d'éclairage public");
		alerts.create(new AlertRequest("Inactive", List.of("éclairage"), null, List.of(), List.of(), List.of(),
				List.of(), null, null, false), PAUL);
		eligibility.evaluateOutOfDate();

		users.update(PAUL, new UpdateUserRequest("Paul Martin", Role.USER, false, null));
		users.update(PAUL, new UpdateUserRequest("Paul Martin", Role.USER, true, null));

		assertThat(eligibility.outOfDate()).isZero();
	}

	@Test
	void eligibilityIsSharedByTheCompanyButTheSavedSearchesStayPersonal() {
		Tender lighting = save("26-1", "Travaux d'éclairage public");
		link(lighting);
		alerts.create(lighting(), MARIE);
		eligibility.evaluateOutOfDate();

		var forMarie = dceStatus.linksOf(lighting.getId(), MARIE).getFirst();
		var forPaul = dceStatus.linksOf(lighting.getId(), PAUL).getFirst();

		assertThat(forMarie.eligibility()).isEqualTo(EligibilityState.ELIGIBLE);
		assertThat(forPaul.eligibility()).isEqualTo(EligibilityState.ELIGIBLE);
		assertThat(forMarie.yourAlerts()).containsExactly("Éclairage");
		assertThat(forPaul.yourAlerts()).isEmpty();
		assertThat(forPaul.eligibilityReason()).isEqualTo("Correspond à 1 veille active").doesNotContain("Éclairage");
		assertThat(alerts.list(PAUL)).isEmpty();
	}

	@Test
	void evaluatingAgainWithoutAChangeOfStatusAddsNoHistory() {
		Tender cleaning = save("26-1", "Nettoyage des locaux");
		Long link = link(cleaning);
		eligibility.evaluateOutOfDate();
		assertThat(linkStatus(link)).isEqualTo("NOT_ELIGIBLE");
		assertThat(events()).isEqualTo(1);

		// A saved search that does not match: same status, another wording, no new entry.
		alerts.create(lighting(), MARIE);
		eligibility.evaluateOutOfDate();
		assertThat(jdbc.queryForObject("SELECT status_detail FROM dce_link", String.class))
			.isEqualTo("Ne correspond à aucune veille active");
		jdbc.update("UPDATE alert_revision SET revision = revision + 1");
		eligibility.evaluateOutOfDate();
		eligibility.evaluateOutOfDate();

		assertThat(events()).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM tender_eligibility", Long.class)).isEqualTo(1);
	}

	private List<Long> outOfDate() {
		return jdbc.queryForList("""
				SELECT t.id FROM tender t LEFT JOIN tender_eligibility e ON e.tender_id = t.id
				WHERE e.tender_id IS NULL OR e.alert_revision < (SELECT revision FROM alert_revision)
				   OR e.evaluated_at < t.updated_at ORDER BY t.id""", Long.class);
	}

	private long events() {
		return jdbc.queryForObject("SELECT count(*) FROM dce_link_event", Long.class);
	}

	private EligibilityState state(Tender tender) {
		return eligibilities.findById(tender.getId()).orElseThrow().getState();
	}

	private String linkStatus(Long link) {
		return jdbc.queryForObject("SELECT status FROM dce_link WHERE id = ?", String.class, link);
	}

	private Long link(Tender tender) {
		return jdbc.queryForObject("""
				INSERT INTO dce_link (tender_id, source, url, platform, status, first_seen_at, last_seen_at)
				VALUES (?, 'BOAMP', ?, 'plateforme.test', 'AVAILABLE', now(), now()) RETURNING id""", Long.class,
				tender.getId(), "https://plateforme.test/dce/" + tender.getExternalId());
	}

	private static AlertRequest lighting() {
		return new AlertRequest("Éclairage", List.of("éclairage"), null, List.of("13"), List.of(MarketType.WORKS),
				List.of(), List.of(), null, null, true);
	}

	private Tender save(String externalId, String title) {
		return tenders.save(new Tender("BOAMP", externalId,
				new TenderDetails(title, null, null, null, null, List.of("13"), List.of(MarketType.WORKS), null,
						List.of(), new BigDecimal("200000"), NOW.atZone(ClockConfiguration.ZONE).toLocalDate(),
						NOW.plusSeconds(86_400 * 30), null, null, List.of()),
				NOW));
	}

}
