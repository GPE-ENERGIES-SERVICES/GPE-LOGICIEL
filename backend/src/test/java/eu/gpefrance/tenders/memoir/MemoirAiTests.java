package eu.gpefrance.tenders.memoir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import eu.gpefrance.tenders.ClockConfiguration;
import eu.gpefrance.tenders.TestUsers;
import eu.gpefrance.tenders.TestcontainersConfiguration;
import eu.gpefrance.tenders.analysis.ExtractionClient;
import eu.gpefrance.tenders.analysis.ExtractionClient.ModelAnswer;
import eu.gpefrance.tenders.analysis.ExtractionRequest;

/**
 * The AI on a memoir, with a simulated provider: the consultation file read
 * and its quotes checked, an outline proposed then accepted by the user, the
 * sections written one after the other without writing over the user.
 */
@SpringBootTest(properties = { "analysis.enabled=true", "analysis.provider=test",
		"analysis.external-transmission-allowed=true" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MemoirAiTests {

	private static final Instant NOW = Instant.parse("2026-10-31T08:00:00Z");

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoBean
	private ExtractionClient client;

	@TestBean
	private Clock clock;

	static Clock clock() {
		return Clock.fixed(NOW, ClockConfiguration.ZONE);
	}

	private final List<ExtractionRequest> sent = new ArrayList<>();

	@BeforeEach
	void prepare() {
		jdbc.execute("TRUNCATE memoir, memoir_library, ai_call, document_page, document, dossier, lot, tender "
				+ "RESTART IDENTITY CASCADE");
		long tenderId = jdbc.queryForObject("""
				INSERT INTO tender (source, external_id, title, status, created_at, updated_at)
				VALUES ('BOAMP', '26-1', 'Rénovation de l''éclairage du stade', 'ACTIVE', now(), now()) RETURNING id""",
				Long.class);
		jdbc.update("INSERT INTO dossier (tender_id, version, status, uploaded_at) VALUES (?, 1, 'DONE', now())", tenderId);
		jdbc.update("""
				INSERT INTO document (dossier_id, file_name, storage_path, size_bytes, sha256, piece_type, state, page_count)
				VALUES (1, 'RC.pdf', 'x/RC.pdf', 1, 'x', 'RC', 'READ', 1),
				       (1, 'Plan.pdf', 'x/Plan.pdf', 1, 'y', 'PLAN', 'READ', 1)""");
		jdbc.update("""
				INSERT INTO document_page (document_id, page_number, text, method) VALUES
				(1, 1, 'Article 6 - Jugement des offres. Valeur technique (60 %) appréciée au vu du mémoire technique : '
				    || 'moyens humains affectés au chantier (30 %), méthodologie d''intervention (30 %). Le mémoire ne '
				    || 'dépassera pas 20 pages.', 'NATIVE'),
				(2, 1, 'Plan de masse du stade', 'NATIVE')""");
		post("/api/memoirs", "{\"tenderId\": " + tenderId + "}").exchange();
		given(client.provider()).willReturn("test");
		given(client.model()).willReturn("test-model");
		given(client.complete(any(ExtractionRequest.class))).willAnswer(invocation -> {
			ExtractionRequest request = invocation.getArgument(0);
			sent.add(request);
			return switch (request.analysisType()) {
				case "MEMOIR_ANALYSIS" -> new ModelAnswer("""
						{"requirements": [
						  {"category": "SUBCRITERION", "text": "Moyens humains affectés au chantier", "weight": "30 %",
						   "document_id": 1, "page": 1, "quote": "moyens humains affectés au chantier (30 %)"},
						  {"category": "FORM", "text": "20 pages au plus", "weight": null,
						   "document_id": 1, "page": 1, "quote": "ne dépassera pas 20 pages"},
						  {"category": "METHOD", "text": "Méthodologie d'intervention", "weight": "30 %",
						   "document_id": 1, "page": 1, "quote": "texte inventé absent du DCE"}],
						 "outline": [
						  {"level": 1, "title": "Moyens humains", "description": "Équipe affectée, sous-critère à 30 %", "requirements": [0]},
						  {"level": 2, "title": "Encadrement", "description": "Chef de chantier", "requirements": [0]},
						  {"level": 1, "title": "Méthodologie", "description": "Phasage", "requirements": [2, 7]}],
						 "missing": ["Plan du mémoire non imposé"]}""", 3000, 400);
				default -> new ModelAnswer("""
						{"html": "<p>Texte rédigé par l'IA.</p><script>x</script>", "missing": ["Effectif réel"]}""", 2000, 300);
			};
		});
	}

	@Test
	void readsTheConsultationFileThenWritesTheValidatedOutline() {
		assertThat(post("/api/memoirs/1/analysis", "")).hasStatus(HttpStatus.ACCEPTED);
		// No plan sent to the AI, the rules of consultation first.
		assertThat(sent.getFirst().documents()).contains("RC.pdf", "Article 6").doesNotContain("Plan de masse");

		assertThat(mvc.get().uri("/api/memoirs/1").with(TestUsers.user())).bodyJson()
			.isLenientlyEqualTo("""
					{"memoir": {"status": "OUTLINE_TO_VALIDATE"},
					 "job": {"kind": "ANALYSIS", "status": "DONE", "message": "Plan du mémoire non imposé"},
					 "requirements": [
					   {"id": 1, "category": "SUBCRITERION", "weight": "30 %", "documentId": 1, "page": 1, "verified": true},
					   {"id": 2, "category": "FORM", "verified": true},
					   {"id": 3, "category": "METHOD", "verified": false, "quote": null}],
					 "sections": [
					   {"id": 1, "title": "Moyens humains", "proposed": true, "requirementIds": [1]},
					   {"id": 2, "title": "Encadrement", "parentId": 1, "level": 2, "proposed": true},
					   {"id": 3, "title": "Méthodologie", "proposed": true, "requirementIds": [3]}]}""");

		// Proposed parts wait for the user before the outline is validated.
		assertThat(post("/api/memoirs/1/outline/validation", "")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(post("/api/memoirs/1/writing", "")).hasStatus(HttpStatus.BAD_REQUEST);
		post("/api/memoirs/1/outline/acceptance", "").exchange();
		post("/api/memoirs/1/outline/validation", "").exchange();

		// Written by the user meanwhile: never written over.
		mvc.put().uri("/api/memoir-sections/3/content").with(csrf()).with(TestUsers.user())
			.contentType(MediaType.APPLICATION_JSON).content("{\"html\": \"<p>Notre méthode.</p>\"}").exchange();
		assertThat(post("/api/memoirs/1/writing", "")).hasStatus(HttpStatus.ACCEPTED);
		assertThat(mvc.get().uri("/api/memoirs/1").with(TestUsers.user())).bodyJson()
			.isLenientlyEqualTo("""
					{"memoir": {"status": "TO_REVIEW"},
					 "job": {"kind": "WRITING", "status": "DONE", "doneSteps": 1, "totalSteps": 1},
					 "sections": [{"id": 1, "content": null}, {"id": 2, "contentOrigin": "AI"},
					              {"id": 3, "content": "<p>Notre méthode.</p>", "contentOrigin": "MANUAL"}]}""");
		assertThat(jdbc.queryForObject("SELECT content FROM memoir_section WHERE id = 2", String.class))
			.isEqualTo("<p>Texte rédigé par l'IA.</p>");

		// A request on a written section is a proposal.
		assertThat(post("/api/memoir-sections/3/ai", "{\"action\": \"ADAPT\"}")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(post("/api/memoir-sections/3/ai", "{\"action\": \"ADAPT\", \"requirementId\": 3}")).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("{\"content\": \"<p>Notre méthode.</p>\", \"proposal\": \"<p>Texte rédigé par l'IA.</p>\"}");
		assertThat(sent.getLast().documents()).contains("Méthodologie d'intervention", "Notre méthode");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_call", Long.class)).isEqualTo(3);
	}

	private MockMvcTester.MockMvcRequestBuilder post(String uri, String body) {
		return mvc.post().uri(uri).with(csrf()).with(TestUsers.user()).contentType(MediaType.APPLICATION_JSON).content(body);
	}

}
