package eu.gpefrance.tenders.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.JdkClientHttpRequestFactoryBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import eu.gpefrance.tenders.ClockConfiguration;
import eu.gpefrance.tenders.TestUsers;
import eu.gpefrance.tenders.TestcontainersConfiguration;
import eu.gpefrance.tenders.alert.AlertRequest;
import eu.gpefrance.tenders.alert.AlertService;
import eu.gpefrance.tenders.eligibility.EligibilityService;
import eu.gpefrance.tenders.tender.MarketType;
import eu.gpefrance.tenders.tender.Tender;
import eu.gpefrance.tenders.tender.TenderDetails;
import eu.gpefrance.tenders.tender.TenderRepository;

/**
 * The configuration of production: no platform allowed, no retriever, the
 * worker disabled. An eligible tender is shown, its file is never requested,
 * even for an address that looks like a file or a link forced into the queue.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DceDefaultConfigurationTests {

	private static final Instant NOW = Instant.parse("2026-10-21T08:00:00Z");

	private static final SyntheticDceServer SERVER = new SyntheticDceServer();

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private org.springframework.context.ApplicationContext context;

	@Autowired
	private org.springframework.web.client.RestClient.Builder restClientBuilder;

	@Autowired
	private DceRetrievalWorker worker;

	@Autowired
	private DceRetrievalProperties properties;

	@Autowired
	private EligibilityService eligibility;

	@Autowired
	private AlertService alerts;

	@Autowired
	private TenderRepository tenders;

	@Autowired
	private JdbcTemplate jdbc;

	@TestBean
	private Clock clock;

	static Clock clock() {
		return Clock.fixed(NOW, ClockConfiguration.ZONE);
	}

	@AfterAll
	static void stopServer() {
		SERVER.stop();
	}

	@BeforeEach
	void cleanDatabase() {
		jdbc.execute("TRUNCATE alert, tender_eligibility, dce_storage_reservation, dce_link_event, dce_link, "
				+ "tender_follow, tender_notice, lot, tender, raw_notice RESTART IDENTITY CASCADE");
		jdbc.execute("""
				INSERT INTO app_user (id, email, display_name, password_hash, role, enabled, created_at)
				VALUES (1, 'marie@gpe.test', 'Marie Durand', 'x', 'USER', true, now()),
				       (2, 'admin@gpe.test', 'Admin GPE', 'x', 'ADMIN', true, now())
				ON CONFLICT DO NOTHING
				""");
		SERVER.reset();
	}

	@Test
	void noPlatformIsAllowedAndTheWorkerIsDisabledByDefault() throws IOException {
		assertThat(properties.allowedPlatforms()).isEmpty();
		assertThat(properties.workerEnabled()).isFalse();
		assertThat(properties.quota().toBytes()).isEqualTo(3_000_000_000L);
		String production = new ClassPathResource("application.properties").getContentAsString(StandardCharsets.UTF_8);
		assertThat(production).containsPattern("(?m)^dce\\.retrieval\\.allowed-platforms=$")
			.containsPattern("(?m)^dce\\.retrieval\\.worker-enabled=false$")
			.containsPattern("(?m)^dce\\.retrieval\\.quota=3000000000B$")
			.containsPattern("(?m)^collect\\.disabled-sources=APPROCH,AWS$");
	}

	@Test
	void theCollectorsKeepTheHttpClientOfTheJdkDespiteApacheHttpClient() {
		ClientHttpRequestFactoryBuilder<?> builder = context.getBean(ClientHttpRequestFactoryBuilder.class);

		assertThat(builder).isInstanceOf(JdkClientHttpRequestFactoryBuilder.class);
		assertThat(restClientBuilder.build()).extracting("clientRequestFactory")
			.isInstanceOf(org.springframework.http.client.JdkClientHttpRequestFactory.class);
	}

	@Test
	void anEligibleFileIsShownButNeverRequested() {
		Long tender = eligibleTenderWithLink(SERVER.url("/ok.pdf"));

		assertThat(status()).isEqualTo("AUTOMATED_ACCESS_NOT_AUTHORIZED");
		worker.poll();
		jdbc.update("UPDATE dce_link SET status = 'ELIGIBLE_PENDING'");
		worker.poll();
		assertThat(status()).as("the disabled worker takes nothing").isEqualTo("ELIGIBLE_PENDING");
		Long claimed = worker.claim();
		worker.process(claimed);

		assertThat(status()).isEqualTo("AUTOMATED_ACCESS_NOT_AUTHORIZED");
		assertThat(SERVER.totalRequests()).isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM dossier", Long.class)).isZero();
		assertThat(mvc.get().uri("/api/tenders/" + tender + "/dce-links").with(TestUsers.user())).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					[{"status": "AUTOMATED_ACCESS_NOT_AUTHORIZED", "eligibility": "ELIGIBLE",
					  "eligibilityReason": "Correspond à 1 veille active", "yourAlerts": ["Éclairage"],
					  "platformAllowed": false, "actionRequired": true, "fileCount": null}]""");
		assertThat(mvc.get().uri("/api/tenders/" + tender + "/dce-links").with(TestUsers.admin())).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("[{\"yourAlerts\": []}]");
	}

	@Test
	void theSummaryJournalAndNewAttemptsAreForAdministratorsOnly() {
		eligibleTenderWithLink(SERVER.url("/ok.pdf"));
		Long link = jdbc.queryForObject("SELECT id FROM dce_link", Long.class);

		assertThat(mvc.get().uri("/api/dce/summary").with(TestUsers.user())).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(mvc.get().uri("/api/dce/events").with(TestUsers.user())).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(mvc.post().uri("/api/dce-links/" + link + "/retry").with(TestUsers.user()).with(csrf()))
			.hasStatus(HttpStatus.FORBIDDEN);
		assertThat(mvc.get().uri("/api/dce/summary")).hasStatus(HttpStatus.UNAUTHORIZED);

		assertThat(mvc.get().uri("/api/dce/summary").with(TestUsers.admin())).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{"allowedPlatforms": [], "workerEnabled": false, "quotaBytes": 3000000000, "usedBytes": 0,
					 "reservedBytes": 0, "eligibility": {"ELIGIBLE": 1}, "links": {"AUTOMATED_ACCESS_NOT_AUTHORIZED": 1}}""");
		assertThat(mvc.get().uri("/api/dce/events").with(TestUsers.admin())).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("[{\"dceLinkId\": " + link + ", \"toStatus\": \"AUTOMATED_ACCESS_NOT_AUTHORIZED\"}]");
		// Not a failure, and the platform is not allowed: no new attempt.
		assertThat(mvc.post().uri("/api/dce-links/" + link + "/retry").with(TestUsers.admin()).with(csrf()))
			.hasStatus(HttpStatus.CONFLICT);
		jdbc.update("UPDATE dce_link SET status = 'PERMANENT_ERROR'");
		assertThat(mvc.post().uri("/api/dce-links/" + link + "/retry").with(TestUsers.admin()).with(csrf()))
			.hasStatus(HttpStatus.CONFLICT)
			.bodyJson()
			.isLenientlyEqualTo("{\"detail\": \"Accès automatisé non autorisé : aucune plateforme n'est autorisée.\"}");
		assertThat(SERVER.totalRequests()).isZero();
	}

	private Long eligibleTenderWithLink(String url) {
		alerts.create(new AlertRequest("Éclairage", List.of("éclairage"), null, List.of("13"), List.of(MarketType.WORKS),
				List.of(), List.of(), null, null, true), 1L);
		Tender tender = tenders.save(new Tender("BOAMP", "26-1", new TenderDetails("Travaux d'éclairage public", null,
				null, null, null, List.of("13"), List.of(MarketType.WORKS), null, List.of(), new BigDecimal("200000"),
				NOW.atZone(ClockConfiguration.ZONE).toLocalDate(), NOW.plus(Duration.ofDays(30)), null, null, List.of()),
				NOW));
		jdbc.update("""
				INSERT INTO dce_link (tender_id, source, url, platform, status, first_seen_at, last_seen_at)
				VALUES (?, 'BOAMP', ?, '127.0.0.1', 'AVAILABLE', now(), now())""", tender.getId(), url);
		eligibility.evaluateOutOfDate();
		return tender.getId();
	}

	private String status() {
		return jdbc.queryForObject("SELECT status FROM dce_link", String.class);
	}

}
