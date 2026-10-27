package eu.gpefrance.tenders.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import eu.gpefrance.tenders.TestUsers;
import eu.gpefrance.tenders.TestcontainersConfiguration;
import eu.gpefrance.tenders.document.Dossier;
import eu.gpefrance.tenders.document.DossierRepository;
import eu.gpefrance.tenders.tender.Tender;
import eu.gpefrance.tenders.tender.TenderDetails;
import eu.gpefrance.tenders.tender.TenderRepository;

/**
 * The configuration as delivered: no key, no provider chosen, analysis
 * disabled. The Anthropic client is there but has no key, so it cannot be
 * used. The application starts, the interface says so, nothing can start and
 * no request waits for ever.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AnalysisWithoutProviderTests {

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private TenderRepository tenders;

	@Autowired
	private DossierRepository dossiers;

	@Autowired
	private AnalysisRepository analyses;

	@Autowired
	private AnalysisWorker worker;

	@Autowired
	private AnalysisGate gate;

	@Autowired
	private ApplicationContext context;

	@Autowired
	private JdbcTemplate jdbc;

	private Tender tender;

	@BeforeEach
	void createTender() {
		jdbc.execute("TRUNCATE extracted_field, analysis_document, analysis, document_page, document, dossier, "
				+ "tender_notice, lot, tender RESTART IDENTITY CASCADE");
		tender = tenders.save(new Tender("BOAMP", "26-1", new TenderDetails("Travaux", null, null, null, null,
				List.of(), List.of(), null, List.of(), null, null, null, null, null, List.of()), Instant.now()));
	}

	@Test
	void startsWithoutKeyAndWithoutAnyProvider() {
		// The only provider of the application, without key: no model, never usable.
		assertThat(context.getBeansOfType(ExtractionClient.class).values()).singleElement()
			.satisfies(client -> assertThat(client.provider()).isEqualTo("anthropic"))
			.satisfies(client -> assertThat(client.model()).isNull());
		assertThat(gate.status().state()).isEqualTo(AnalysisGate.State.DISABLED);
		assertThat(gate.readyClient()).isEmpty();
	}

	@Test
	void saysThatTheAnalysisIsPreparedButDisabled() {
		assertThat(mvc.get().uri("/api/tenders/{id}/analysis", tender.getId()).with(TestUsers.user())).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("{\"configured\": false, \"latest\": null, \"history\": []}");
		assertThat(mvc.get().uri("/api/tenders/{id}/analysis", tender.getId()).with(TestUsers.user()))
			.bodyJson()
			.extractingPath("$.unavailableReason")
			.asString()
			.contains("préparée mais désactivée");
		assertThat(mvc.post().uri("/api/dossiers/1/analyses").with(TestUsers.user()).with(csrf()))
			.hasStatus(HttpStatus.CONFLICT)
			.bodyJson()
			.extractingPath("$.detail")
			.asString()
			.contains("désactivée");
		assertThat(analyses.count()).isZero();
	}

	@Test
	void showsTheAdministratorTheConfigurationWithoutSecret() {
		assertThat(mvc.get().uri("/api/analyses/configuration").with(TestUsers.admin())).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{"state": "DISABLED", "enabled": false, "provider": "none", "model": null,
					 "externalTransmissionAllowed": false, "availableProviders": ["anthropic"], "problems": [],
					 "limits": {"maxCharacters": 400000, "maxDocumentsPerTender": 10, "maxConcurrentRequests": 1,
					   "maxAttempts": 3, "monthlyBudget": null, "pricesSet": false},
					 "queue": {"PENDING": 0, "DONE": 0}, "monthToDate": {"inputTokens": 0, "estimatedCost": null}}""");
	}

	@Test
	void closesAtStartARequestLeftWaitingInsteadOfKeepingIt() {
		Dossier dossier = dossiers.save(new Dossier(tender.getId(), 1, "Marie Durand", Instant.now()));
		Analysis waiting = analyses.save(new Analysis(tender.getId(), dossier.getId(), "test", "test-model",
				ExtractionPrompt.VERSION, List.of(), "a".repeat(64), "Marie Durand", Instant.now()));

		worker.recoverInterrupted();
		worker.poll();

		Analysis closed = analyses.findById(waiting.getId()).orElseThrow();
		assertThat(closed.getStatus()).isEqualTo(AnalysisStatus.FAILED);
		assertThat(closed.getErrorKind()).isEqualTo(FailureKind.NOT_CONFIGURED);
		assertThat(closed.getAttempts()).isZero();
	}

}
