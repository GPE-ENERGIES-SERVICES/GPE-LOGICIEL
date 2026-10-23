package eu.gpefrance.tenders.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import eu.gpefrance.tenders.ClockConfiguration;
import eu.gpefrance.tenders.TestUsers;
import eu.gpefrance.tenders.TestcontainersConfiguration;
import eu.gpefrance.tenders.eligibility.EligibilityService;
import eu.gpefrance.tenders.tender.MarketType;
import eu.gpefrance.tenders.tender.Tender;
import eu.gpefrance.tenders.tender.TenderDetails;
import eu.gpefrance.tenders.tender.TenderRepository;

/**
 * « Mes veilles » through the API, with two users: each one only sees and
 * changes their own saved searches.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AlertApiTests {

	private static final Instant NOW = Instant.parse("2026-10-21T08:00:00Z");

	private static final String LIGHTING = """
			{"name": "Éclairage PACA", "keywords": ["éclairage"], "region": "Provence-Alpes-Côte d'Azur",
			 "departments": [], "marketTypes": ["WORKS"], "activities": [], "cpvPrefixes": [],
			 "minAmount": null, "maxAmount": null, "active": true}""";

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private TenderRepository tenders;

	@Autowired
	private EligibilityService eligibility;

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
	void createsChangesDeactivatesAndDeletesASavedSearch() {
		long revision = revision();
		assertThat(mvc.post().uri("/api/alerts").with(csrf()).with(TestUsers.user()).contentType(MediaType.APPLICATION_JSON)
			.content(LIGHTING)).hasStatus(HttpStatus.CREATED)
			.bodyJson()
			.isLenientlyEqualTo("""
					{"id": 1, "name": "Éclairage PACA", "keywords": ["éclairage"], "marketTypes": ["WORKS"],
					 "active": true, "lastViewedAt": null, "matchCount": 0, "newCount": 0}""");
		assertThat(revision()).isEqualTo(revision + 1);

		assertThat(mvc.put().uri("/api/alerts/1").with(csrf()).with(TestUsers.user()).contentType(MediaType.APPLICATION_JSON)
			.content(LIGHTING.replace("\"active\": true", "\"active\": false").replace("éclairage\"]", "éclairage\", \"IRVE\"]")))
			.hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{"id": 1, "keywords": ["éclairage", "IRVE"], "active": false}""");
		assertThat(revision()).isEqualTo(revision + 2);

		assertThat(mvc.get().uri("/api/alerts").with(TestUsers.user())).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("[{\"id\": 1, \"active\": false}]");

		assertThat(mvc.delete().uri("/api/alerts/1").with(csrf()).with(TestUsers.user())).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(mvc.get().uri("/api/alerts").with(TestUsers.user())).hasStatusOk().bodyJson().isEqualTo("[]");
		assertThat(revision()).isEqualTo(revision + 3);
	}

	@Test
	void aUserNeitherSeesNorChangesTheSavedSearchesOfAnother() {
		mvc.post().uri("/api/alerts").with(csrf()).with(TestUsers.user()).contentType(MediaType.APPLICATION_JSON).content(LIGHTING)
			.exchange();

		assertThat(mvc.get().uri("/api/alerts").with(TestUsers.admin())).hasStatusOk().bodyJson().isEqualTo("[]");
		assertThat(mvc.get().uri("/api/alerts/1").with(TestUsers.admin())).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(mvc.put().uri("/api/alerts/1").with(csrf()).with(TestUsers.admin()).contentType(MediaType.APPLICATION_JSON)
			.content(LIGHTING)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(mvc.delete().uri("/api/alerts/1").with(csrf()).with(TestUsers.admin())).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(mvc.post().uri("/api/alerts/1/viewed").with(csrf()).with(TestUsers.admin())).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(jdbc.queryForObject("SELECT user_id FROM alert WHERE id = 1", Long.class)).isEqualTo(1L);
	}

	@Test
	void theOwnerIsTheLoggedInUserWhateverTheRequestSays() {
		mvc.post().uri("/api/alerts").with(csrf()).with(TestUsers.admin()).contentType(MediaType.APPLICATION_JSON)
			.content(LIGHTING.replace("{\"name\"", "{\"userId\": 1, \"name\"")).exchange();

		assertThat(jdbc.queryForObject("SELECT user_id FROM alert", Long.class)).isEqualTo(2L);
	}

	@Test
	void refusesInvalidCriteria() {
		assertThat(post(LIGHTING.replace("\"name\": \"Éclairage PACA\"", "\"name\": \" \""))).isEqualTo(400);
		assertThat(post("""
				{"name": "Vide", "keywords": [], "region": null, "departments": [], "marketTypes": [],
				 "activities": [], "cpvPrefixes": [], "minAmount": null, "maxAmount": null}""")).isEqualTo(400);
		assertThat(post(LIGHTING.replace("\"departments\": []", "\"departments\": [\"999\"]"))).isEqualTo(400);
		assertThat(post(LIGHTING.replace("Provence-Alpes-Côte d'Azur", "Atlantide"))).isEqualTo(400);
		assertThat(post(LIGHTING.replace("\"activities\": []", "\"activities\": [\"JARDINAGE\"]"))).isEqualTo(400);
		assertThat(post(LIGHTING.replace("\"cpvPrefixes\": []", "\"cpvPrefixes\": [\"45x\"]"))).isEqualTo(400);
		assertThat(post(LIGHTING.replace("\"minAmount\": null, \"maxAmount\": null",
				"\"minAmount\": 500000, \"maxAmount\": 100000"))).isEqualTo(400);
		assertThat(post(LIGHTING.replace("\"minAmount\": null", "\"minAmount\": -1"))).isEqualTo(400);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM alert", Long.class)).isZero();
	}

	@Test
	void requiresAnAuthenticatedUser() {
		assertThat(mvc.get().uri("/api/alerts")).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.post().uri("/api/alerts").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(LIGHTING))
			.hasStatus4xxClientError();
	}

	@Test
	void countsItsResultsAndFiltersTheListOfTenders() {
		Tender lighting = save("26-1", "Travaux d'éclairage public", "13", LocalDate.of(2026, 10, 20));
		save("26-2", "Travaux d'éclairage du stade", "59", LocalDate.of(2026, 10, 20));
		save("26-3", "Nettoyage des locaux", "13", LocalDate.of(2026, 10, 20));
		mvc.post().uri("/api/alerts").with(csrf()).with(TestUsers.user()).contentType(MediaType.APPLICATION_JSON).content(LIGHTING)
			.exchange();

		eligibility.evaluateOutOfDate();

		assertThat(mvc.get().uri("/api/alerts").with(TestUsers.user())).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("[{\"matchCount\": 1, \"newCount\": 1, \"indeterminateCount\": 0}]");
		assertThat(mvc.get().uri("/api/tenders?tab=ALL&alert=1").with(TestUsers.user())).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("{\"totalElements\": 1, \"content\": [{\"id\": " + lighting.getId() + "}]}");
		// The saved search of another user filters nothing in.
		assertThat(mvc.get().uri("/api/tenders?tab=ALL&alert=1").with(TestUsers.admin())).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("{\"totalElements\": 0}");

		mvc.post().uri("/api/alerts/1/viewed").with(csrf()).with(TestUsers.user()).exchange();
		assertThat(mvc.get().uri("/api/alerts").with(TestUsers.user())).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("[{\"matchCount\": 1, \"newCount\": 0}]");
	}

	@Test
	void deletingASavedSearchKeepsTheTendersAndTheirLinks() {
		Tender tender = save("26-1", "Travaux d'éclairage public", "13", LocalDate.of(2026, 10, 20));
		jdbc.update("""
				INSERT INTO dce_link (tender_id, source, url, platform, status, first_seen_at, last_seen_at)
				VALUES (?, 'BOAMP', 'https://plateforme.test/dce/1', 'plateforme.test', 'AVAILABLE', now(), now())""",
				tender.getId());
		mvc.post().uri("/api/alerts").with(csrf()).with(TestUsers.user()).contentType(MediaType.APPLICATION_JSON).content(LIGHTING)
			.exchange();
		eligibility.evaluateOutOfDate();

		mvc.delete().uri("/api/alerts/1").with(csrf()).with(TestUsers.user()).exchange();
		eligibility.evaluateOutOfDate();

		assertThat(tenders.count()).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT url FROM dce_link", String.class)).isEqualTo("https://plateforme.test/dce/1");
		assertThat(jdbc.queryForObject("SELECT state FROM tender_eligibility", String.class)).isEqualTo("NOT_ELIGIBLE");
	}

	private int post(String body) {
		return mvc.post().uri("/api/alerts").with(csrf()).with(TestUsers.user()).contentType(MediaType.APPLICATION_JSON).content(body)
			.exchange()
			.getResponse()
			.getStatus();
	}

	private long revision() {
		return jdbc.queryForObject("SELECT revision FROM alert_revision", Long.class);
	}

	private Tender save(String externalId, String title, String department, LocalDate publishedAt) {
		return tenders.save(new Tender("BOAMP", externalId,
				new TenderDetails(title, null, null, null, null, List.of(department), List.of(MarketType.WORKS), null,
						List.of(), new BigDecimal("200000"), publishedAt, NOW.plusSeconds(86_400 * 30), null, null,
						List.of()),
				NOW));
	}

}
