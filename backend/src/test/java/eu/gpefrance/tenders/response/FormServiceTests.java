package eu.gpefrance.tenders.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import eu.gpefrance.tenders.TestUsers;
import eu.gpefrance.tenders.TestcontainersConfiguration;
import eu.gpefrance.tenders.analysis.ExtractionClient;
import eu.gpefrance.tenders.analysis.ExtractionClient.ModelAnswer;
import eu.gpefrance.tenders.analysis.ExtractionRequest;
import eu.gpefrance.tenders.document.DossierRepository;
import eu.gpefrance.tenders.document.DossierStatus;
import eu.gpefrance.tenders.document.FileStorage;
import eu.gpefrance.tenders.response.FormService.FieldResponse;
import eu.gpefrance.tenders.response.FormService.FormResponse;
import eu.gpefrance.tenders.tender.Tender;
import eu.gpefrance.tenders.tender.TenderDetails;
import eu.gpefrance.tenders.tender.TenderRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The AI says what goes where, the software writes the values of the company: it
 * never receives them, and what a user wrote stays. No real provider is called.
 */
@SpringBootTest(properties = { "analysis.enabled=true", "analysis.provider=test",
		"analysis.external-transmission-allowed=true", "analysis.input-price-per-million=3",
		"analysis.output-price-per-million=15", "analysis.max-attempts=2", "analysis.max-documents-per-tender=2" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FormServiceTests {

	private static final String SIRET = "12345678900011";

	private static final String IBAN = "FR7612345678901234567890123";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private FormService service;

	@Autowired
	private ResponseService responses;

	@Autowired
	private TenderRepository tenders;

	@Autowired
	private DossierRepository dossiers;

	@Autowired
	private FileStorage storage;

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoBean
	private ExtractionClient client;

	private Long tenderId;

	@BeforeEach
	void prepare() throws Exception {
		jdbc.execute("TRUNCATE response_form_field, response_form, response_piece, extracted_field, analysis_document, "
				+ "analysis, document_page, document, dossier, ai_call, tender_notice, lot, tender RESTART IDENTITY CASCADE");
		jdbc.update("UPDATE company_profile SET legal_name = 'GPE', siret = ?, iban = ?, email = NULL", SIRET, IBAN);
		tenderId = tenders.save(Tender.manual(new TenderDetails("Toiture", null, null, null, null, List.of(), List.of(),
				null, List.of(), null, null, null, null, null, List.of()), "Marie", Instant.now())).getId();
		given(client.provider()).willReturn("test");
		given(client.model()).willReturn("test-model");
		given(client.complete(any(ExtractionRequest.class))).willAnswer((call) -> answer(call.getArgument(0)));
		assertThat(mvc.post()
			.uri("/api/tenders/{id}/dossiers", tenderId)
			.multipart()
			.file(new MockMultipartFile("files", "26-13-AE.docx",
					"application/vnd.openxmlformats-officedocument.wordprocessingml.document", FormDocumentTests.form()))
			.with(TestUsers.user())
			.with(csrf())).hasStatus(HttpStatus.ACCEPTED);
		for (int attempt = 0; attempt < 100; attempt++) {
			var status = dossiers.findByTenderIdOrderByVersionDesc(tenderId).getFirst().getStatus();
			if (status == DossierStatus.DONE || status == DossierStatus.FAILED) {
				return;
			}
			Thread.sleep(100);
		}
	}

	/** What a model would answer, and attempts a good one would not make. */
	private static ModelAnswer answer(ExtractionRequest request) {
		if (request.analysisType().equals(PieceWriter.KIND)) {
			return new ModelAnswer("""
					{"html": "<p>GPE garantit les modules.</p><script>x</script><p>[À compléter : durée]</p>",
					 "missing": ["Durée de garantie des onduleurs"]}""", 100, 50);
		}
		JsonNode content = JSON.readTree(request.documents());
		var answer = JSON.createObjectNode();
		var places = answer.putArray("emplacements");
		for (JsonNode place : content.path("emplacements")) {
			String around = place.path("texte_autour").asString();
			var entry = places.addObject().put("id", place.path("id").asString());
			entry.putNull("cle");
			entry.putNull("valeur");
			entry.putNull("remarque");
			if (around.endsWith("SIRET : …")) {
				entry.put("action", "CLE").put("cle", "siret");
			}
			else if (around.endsWith("Adresse électronique :")) {
				entry.put("action", "CLE").put("cle", "courriel");
			}
			else if (around.endsWith("Montant HT (en chiffres) (€) :")) {
				entry.put("action", "CLE").put("cle", "montant_ht");
			}
			else if (around.endsWith("Téléphone : …")) {
				entry.put("action", "CLE").put("cle", "cle_inventee");
			}
			else if (around.endsWith("Adresse de l'établissement : …")) {
				entry.put("action", "TEXTE").put("valeur", "Vitrolles").put("remarque", "Tiré des données");
			}
			else if (around.endsWith("Refuse de percevoir l'avance")) {
				entry.put("action", "MANQUANT").put("remarque", "Choix à faire");
			}
			else {
				entry.put("action", "IGNORER");
			}
		}
		return new ModelAnswer(answer.toString(), 100, 50);
	}

	private Long piece() {
		return responses.add(tenderId, ResponsePiece.Category.OFFER, "Acte d'engagement", "Marie").pieces().getFirst().id();
	}

	private static FieldResponse field(FormResponse form, String labelEnd) {
		return form.fields()
			.stream()
			.filter((field) -> field.label().endsWith(labelEnd))
			.findFirst()
			.orElseThrow(() -> new AssertionError(labelEnd));
	}

	@Test
	void theAiSaysWhereTheSoftwareWritesAndNeverSeesTheBankDetails() {
		FormResponse form = service.start(piece(), "2", true, "Marie");

		FieldResponse siret = field(form, "SIRET : …");
		assertThat(siret.value()).isEqualTo(SIRET);
		assertThat(siret.source()).isEqualTo("COMPANY");
		ArgumentCaptor<ExtractionRequest> sent = ArgumentCaptor.forClass(ExtractionRequest.class);
		verify(client).complete(sent.capture());
		assertThat(sent.getValue().documents()).doesNotContain(IBAN);
		assertThat(sent.getValue().documents()).contains("\"siret\"").contains("\"renseignee\":true");

		// Not entered, unknown or without validated estimate: flagged, nothing made up.
		assertThat(field(form, "Adresse électronique :").source()).isEqualTo("MISSING");
		assertThat(field(form, "Téléphone : …").source()).isEqualTo("MISSING");
		FieldResponse amount = field(form, "Montant HT (en chiffres) (€) :");
		assertThat(amount.source()).isEqualTo("MISSING");
		assertThat(amount.note()).contains("chiffrage validé");
		assertThat(amount.value()).isNull();
		assertThat(field(form, "Adresse de l'établissement : …").source()).isEqualTo("AI");
		assertThat(field(form, "Refuse de percevoir l'avance").note()).isEqualTo("Choix à faire");
		assertThat(form.lotNumber()).isEqualTo("2");
		assertThat(form.documentName()).isEqualTo("26-13-AE.docx");
	}

	@Test
	void whatEngagesTheCandidateIsNeitherSentNorFilled() {
		FormResponse form = service.start(piece(), null, true, "Marie");

		ArgumentCaptor<ExtractionRequest> sent = ArgumentCaptor.forClass(ExtractionRequest.class);
		verify(client).complete(sent.capture());
		assertThat(sent.getValue().documents()).doesNotContain("Nom du signataire").doesNotContain("Signature :");
		assertThat(form.lockedCount()).isGreaterThan(0);
		assertThat(form.fields()).noneMatch((field) -> field.label().contains("ne pas entrer dans un cas d'exclusion"));
	}

	@Test
	void aNewFillingKeepsWhatAUserWrote() {
		Long piece = piece();
		FormResponse first = service.start(piece, null, true, "Marie");
		FieldResponse siret = field(first, "SIRET : …");
		service.change(siret.id(), "99999999900011", false);

		FormResponse second = service.start(piece, null, true, "Paul");

		FieldResponse kept = field(second, "SIRET : …");
		assertThat(kept.value()).isEqualTo("99999999900011");
		assertThat(kept.source()).isEqualTo("MANUAL");
	}

	@Test
	void fillsByHandWithoutCallingTheAi() {
		FormResponse form = service.start(piece(), null, false, "Marie");

		verify(client, never()).complete(any(ExtractionRequest.class));
		assertThat(field(form, "SIRET : …").source()).isEqualTo("NONE");
		assertThat(field(form, "SIRET : …").value()).isNull();
	}

	@Test
	void writesTheWordAndJoinsItToThePieceToBeCheckedAgain() throws IOException {
		Long piece = piece();
		FormResponse form = service.start(piece, "2", true, "Marie");
		service.change(field(form, "Refuse de percevoir l'avance").id(), null, true);
		service.change(field(form, "Montant HT (en chiffres) (€) :").id(), "10 000,00 €", false);

		service.generate(piece, "Marie");

		ResponseService.PieceResponse joined = responses.view(tenderId).pieces().getFirst();
		assertThat(joined.fileName()).isEqualTo("26-13-AE - lot 2 - rempli.docx");
		assertThat(joined.state()).isEqualTo(ResponseService.PieceState.TO_CHECK);
		String path = jdbc.queryForObject("SELECT storage_path FROM response_piece WHERE id = ?", String.class, piece);
		try (XWPFDocument word = new XWPFDocument(new ByteArrayInputStream(Files.readAllBytes(storage.resolve(path))))) {
			List<String> lines = word.getParagraphs().stream().map(XWPFParagraph::getText).toList();
			assertThat(lines).contains("SIRET : " + SIRET, "☒ Refuse de percevoir l'avance", "Montant HT (en chiffres) (€) : 10 000,00 €", "Vitrolles");
			assertThat(lines).contains("Nom du signataire : ..........", "☐ ne pas entrer dans un cas d'exclusion");
		}
	}

	@Test
	void eachPieceSaysHowItIsFilled() {
		assertThat(ResponseService.fillOf("Mémoire justificatif")).isEqualTo(ResponseService.Fill.MEMOIR);
		assertThat(ResponseService.fillOf("DPGF datée et tamponnée")).isEqualTo(ResponseService.Fill.PRICES);
		assertThat(ResponseService.fillOf("Documentation technique du matériel")).isEqualTo(ResponseService.Fill.EQUIPMENT);
		assertThat(ResponseService.fillOf("Liste des travaux des 5 dernières années")).isEqualTo(ResponseService.Fill.WRITE);
		assertThat(ResponseService.fillOf("Annexe bilan carbone simplifié")).isEqualTo(ResponseService.Fill.WRITE);
		assertThat(ResponseService.fillOf("Modèle de contrat de maintenance")).isEqualTo(ResponseService.Fill.WRITE);
		assertThat(ResponseService.fillOf("Acte d'engagement")).isEqualTo(ResponseService.Fill.FORM);
		assertThat(ResponseService.fillOf("Extrait Kbis")).isNull();
		assertThat(ResponseService.fillOf("Attestation d'assurance décennale")).isNull();
		assertThat(ResponseService.fillOf("Plan de calepinage sommaire")).isNull();
	}

	@Test
	void aPieceWrittenByHandOrByTheAiIsJoinedAsAWordFileToCheck() throws IOException {
		Long piece = responses.add(tenderId, ResponsePiece.Category.OFFER, "Modèle de contrat de maintenance", "Marie")
			.pieces().getFirst().id();

		ResponseService.PieceResponse written = responses.write(piece, "<p>Visite annuelle.</p><script>x</script>", "Marie")
			.pieces().getFirst();
		assertThat(written.fill()).isEqualTo(ResponseService.Fill.WRITE);
		assertThat(written.state()).isEqualTo(ResponseService.PieceState.TO_CHECK);
		assertThat(written.content()).isEqualTo("<p>Visite annuelle.</p>");
		assertThat(written.fileName()).isEqualTo("Modèle de contrat de maintenance.docx");
		assertThat(written.detail()).startsWith("Rédigée dans l'application");
		assertThat(wordText(piece)).contains("Modèle de contrat de maintenance", "Visite annuelle.");

		ResponseService.PieceResponse drafted = responses.draft(piece, "Marie").pieces().getFirst();
		assertThat(drafted.contentOrigin()).isEqualTo("AI");
		assertThat(drafted.content()).contains("GPE garantit les modules.").doesNotContain("script");
		assertThat(drafted.missing()).containsExactly("Durée de garantie des onduleurs");
		assertThat(drafted.detail()).startsWith("Rédigée par l'IA, à relire");
		assertThat(wordText(piece)).contains("GPE garantit les modules.").doesNotContain("Visite annuelle.");
		// One file only: the former one is replaced.
		assertThat(Files.list(storage.resolve("responses/" + tenderId + "/" + piece))
			.filter((file) -> file.getFileName().toString().startsWith("Modèle de contrat"))).hasSize(1);

		Long kbis = responses.add(tenderId, ResponsePiece.Category.APPLICATION, "Extrait Kbis", "Marie").pieces().stream()
			.filter((item) -> item.label().equals("Extrait Kbis")).findFirst().orElseThrow().id();
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> responses.draft(kbis, "Marie"))
			.hasMessageContaining("ne se rédige pas");
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> responses.fromStep(kbis, "Marie"))
			.hasMessageContaining("autre étape");
	}

	private String wordText(Long piece) throws IOException {
		String path = jdbc.queryForObject("SELECT storage_path FROM response_piece WHERE id = ?", String.class, piece);
		try (XWPFDocument word = new XWPFDocument(new ByteArrayInputStream(Files.readAllBytes(storage.resolve(path))))) {
			return word.getParagraphs().stream().map(XWPFParagraph::getText).collect(java.util.stream.Collectors.joining("\n"));
		}
	}

	@Test
	void takesTheFileOfAnotherStep() throws IOException {
		jdbc.update("""
				INSERT INTO memoir (tender_id, title, status, created_by, created_at, updated_by, updated_at)
				VALUES (?, 'Mémoire Toiture', 'DRAFT', 'Marie', now(), 'Marie', now())""", tenderId);
		Long memoir = responses.add(tenderId, ResponsePiece.Category.OFFER, "Mémoire technique", "Marie").pieces()
			.getFirst().id();

		ResponseService.PieceResponse joined = responses.fromStep(memoir, "Marie").pieces().getFirst();
		assertThat(joined.fill()).isEqualTo(ResponseService.Fill.MEMOIR);
		assertThat(joined.fileName()).isEqualTo("Mémoire Toiture.docx");
		assertThat(joined.state()).isEqualTo(ResponseService.PieceState.TO_CHECK);

		Long prices = responses.add(tenderId, ResponsePiece.Category.OFFER, "DPGF", "Marie").pieces().stream()
			.filter((item) -> item.label().equals("DPGF")).findFirst().orElseThrow().id();
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> responses.fromStep(prices, "Marie"))
			.hasMessageContaining("Aucun chiffrage");
	}

	@Test
	void saysWhenTheFileHoldsNoSuchForm() {
		Long piece = responses.add(tenderId, ResponsePiece.Category.APPLICATION, "Formulaire DC1", "Marie").pieces().getFirst().id();

		FormResponse form = service.view(piece);

		assertThat(form.documentName()).isNull();
		assertThat(form.problem()).contains("ne fournit pas ce formulaire en Word");
		assertThat(mvc.post()
			.uri("/api/response-pieces/{id}/form", piece)
			.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
			.content("{\"useAi\": false}")
			.with(TestUsers.user())
			.with(csrf())).hasStatus(HttpStatus.CONFLICT);

		// The model dropped by a user comes first; a file that is not Word is refused.
		assertThat(mvc.post().uri("/api/response-pieces/{id}/form/template", piece).multipart()
			.file(new MockMultipartFile("file", "dc1.pdf", "application/pdf", new byte[] { 1 }))
			.with(TestUsers.user()).with(csrf())).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(mvc.post().uri("/api/response-pieces/{id}/form/template", piece).multipart()
			.file(new MockMultipartFile("file", "DC1-officiel.docx", "application/octet-stream", new byte[] { 1 }))
			.with(TestUsers.user()).with(csrf())).hasStatusOk();
		FormResponse dropped = service.view(piece);
		assertThat(dropped.documentName()).isEqualTo("DC1-officiel.docx");
		assertThat(dropped.templateDropped()).isTrue();
		assertThat(dropped.problem()).isNull();
	}

	@Test
	void aFormFilledByTheAiWaitsForAUserWhoValidatesIt() {
		Long piece = piece();
		assertThat(service.view(piece).status()).isEqualTo("TODO");
		assertThat(service.start(piece, "2", true, "Marie").status()).isEqualTo("TO_VALIDATE");

		FormResponse validated = service.review(piece, FormService.Decision.VALIDATE, null, "Paul");

		assertThat(validated.status()).isEqualTo("VALIDATED");
		assertThat(validated.reviewedBy()).isEqualTo("Paul");
		ResponseService.PieceResponse joined = responses.view(tenderId).pieces().getFirst();
		assertThat(joined.fileName()).isEqualTo("26-13-AE - lot 2 - rempli.docx");
		assertThat(joined.state()).isEqualTo(ResponseService.PieceState.READY);
		// Locked until reopened.
		Long siret = field(validated, "SIRET : …").id();
		assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> service.change(siret, "1", false)))
			.hasMessageContaining("rouvrez-le");
		assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> service.start(piece, "2", true, "Marie")))
			.hasMessageContaining("rouvrez-le");

		assertThat(service.review(piece, FormService.Decision.REOPEN, null, "Paul").status()).isEqualTo("IN_PROGRESS");
		assertThat(responses.view(tenderId).pieces().getFirst().state()).isEqualTo(ResponseService.PieceState.TO_CHECK);
		assertThat(service.change(siret, "99999999900011", false).value()).isEqualTo("99999999900011");
	}

	@Test
	void aRejectionSaysWhyAndTheAiReadsItWhenItFillsAgain() {
		Long piece = piece();
		service.start(piece, null, true, "Marie");
		assertThat(org.assertj.core.api.Assertions
			.catchThrowable(() -> service.review(piece, FormService.Decision.REJECT, " ", "Paul")))
			.hasMessageContaining("pourquoi");

		FormResponse rejected = service.review(piece, FormService.Decision.REJECT, "Le lot est le 3", "Paul");
		assertThat(rejected.status()).isEqualTo("REJECTED");
		assertThat(rejected.reviewComment()).isEqualTo("Le lot est le 3");
		assertThat(org.assertj.core.api.Assertions
			.catchThrowable(() -> service.review(piece, FormService.Decision.VALIDATE, null, "Paul")))
			.hasMessageContaining("refusé");

		assertThat(service.start(piece, "3", true, "Marie").status()).isEqualTo("TO_VALIDATE");
		ArgumentCaptor<ExtractionRequest> sent = ArgumentCaptor.forClass(ExtractionRequest.class);
		verify(client, org.mockito.Mockito.times(2)).complete(sent.capture());
		assertThat(sent.getAllValues().getFirst().documents()).doesNotContain("remarque_du_relecteur");
		assertThat(sent.getValue().documents()).contains("\"remarque_du_relecteur\":\"Le lot est le 3\"");
	}

	@Test
	void aFormFilledByHandIsInProgressAndACorrectionAfterARejectionToo() {
		Long piece = piece();
		FormResponse form = service.start(piece, null, false, "Marie");
		assertThat(form.status()).isEqualTo("IN_PROGRESS");
		assertThat(form.filledByAi()).isFalse();
		assertThat(org.assertj.core.api.Assertions
			.catchThrowable(() -> service.review(piece, FormService.Decision.REJECT, "Non", "Paul")))
			.hasMessageContaining("à valider");

		service.start(piece, null, true, "Marie");
		service.review(piece, FormService.Decision.REJECT, "Adresse fausse", "Paul");
		service.change(field(service.view(piece), "SIRET : …").id(), SIRET, false);

		assertThat(service.view(piece).status()).isEqualTo("IN_PROGRESS");
		assertThat(service.view(piece).filledByAi()).isTrue();
		assertThat(service.review(piece, FormService.Decision.VALIDATE, null, "Paul").status()).isEqualTo("VALIDATED");
	}

	@Test
	void listsTheFormsToFillOfEveryAnswerWithWhereTheyStand() {
		Long form = piece();
		responses.add(tenderId, ResponsePiece.Category.APPLICATION, "Extrait Kbis", "Marie");
		service.start(form, null, true, "Marie");

		assertThat(service.overview(null)).singleElement().satisfies((line) -> {
			assertThat(line.pieceId()).isEqualTo(form);
			assertThat(line.tenderTitle()).isEqualTo("Toiture");
			assertThat(line.status()).isEqualTo("TO_VALIDATE");
			// No e-mail, an unknown key, no validated estimate, the advance to choose.
			assertThat(line.missing()).isEqualTo(4);
			assertThat(line.toCheck()).isEqualTo(1);
		});
		assertThat(service.overview(tenderId + 1)).isEmpty();
		assertThat(mvc.get()
			.uri("/api/administrative-forms?tenderId={id}", tenderId)
			.with(TestUsers.user())).hasStatusOk().bodyJson().extractingPath("$[0].status").isEqualTo("TO_VALIDATE");
		assertThat(mvc.post()
			.uri("/api/response-pieces/{id}/form/review", form)
			.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
			.content("{\"decision\": \"REJECT\", \"comment\": \"Lot faux\"}")
			.with(TestUsers.user())
			.with(csrf())).hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("REJECTED");
	}

	@Test
	void tellsWhatAFillingByTheAiCostAndHowLongSuchACallTakes() {
		Instant before = Instant.now().minusSeconds(5);
		service.start(piece(), null, true, "Marie Durand");

		assertThat(mvc.get()
			.uri("/api/ai-calls/summary?kind=RESPONSE_FORM&since={since}", before.toString())
			.with(TestUsers.user())).hasStatusOk()
			.bodyJson()
			// 100 tokens in at 3 € and 50 out at 15 € a million.
			.isLenientlyEqualTo("{\"calls\": 1, \"inputTokens\": 100, \"outputTokens\": 50, \"cost\": 0.00105}");
		assertThat(mvc.get().uri("/api/ai-calls/summary?kind=RESPONSE_FORM").with(TestUsers.user())).bodyJson()
			.extractingPath("$.typicalSeconds")
			.isEqualTo(0);
		// Another user, or a kind not called: nothing.
		assertThat(mvc.get()
			.uri("/api/ai-calls/summary?kind=ESTIMATE_REVIEW&since={since}", before.toString())
			.with(TestUsers.user())).bodyJson()
			.isLenientlyEqualTo("{\"calls\": 0, \"cost\": null}");
		assertThat(mvc.get().uri("/api/ai-calls/summary?kind=bad kind").with(TestUsers.user()))
			.hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void findsTheOtherFormsInTheFileByTheWordsOfTheirLabel() {
		var honour = document("DCE/05_Declaration_sur_honneur.docx");
		var visit = document("DCE/Attestation de visite.doc");
		var engagement = document("DCE/26-13-AE.docx");
		List<eu.gpefrance.tenders.document.Document> file = List.of(engagement, visit, honour);

		assertThat(FormService.closest("Déclaration sur l'honneur", file)).contains(honour);
		assertThat(FormService.closest("Attestation de visite du site", file)).contains(visit);
		// Not half of its words in any name: nothing rather than a wrong form.
		assertThat(FormService.closest("Fiche de liaison sous-traitance", file)).isEmpty();
		assertThat(FormService.closest("Formulaire", file)).isEmpty();
	}

	private static eu.gpefrance.tenders.document.Document document(String name) {
		var document = org.mockito.Mockito.mock(eu.gpefrance.tenders.document.Document.class);
		given(document.getFileName()).willReturn(name);
		return document;
	}

	@Test
	void recognisesTheFormsItCanFill() {
		assertThat(FormService.fillable("Formulaire DC1")).isTrue();
		assertThat(FormService.fillable("Acte d'engagement")).isTrue();
		assertThat(FormService.fillable("DUME")).isFalse();
		assertThat(FormService.fillable("Extrait Kbis")).isFalse();
		// The other documents of the buyer to fill.
		assertThat(FormService.fillable("Déclaration sur l'honneur")).isTrue();
		assertThat(FormService.fillable("Attestation de visite")).isTrue();
		assertThat(FormService.fillable("Cadre de réponse administratif")).isTrue();
		assertThat(FormService.fillable("Fiche de liaison")).isTrue();
		// Not a form: an official document of the company, the prices, the memoir.
		assertThat(FormService.fillable("Déclaration des moyens humains")).isFalse();
		assertThat(FormService.fillable("Attestation d'assurance décennale")).isFalse();
		assertThat(FormService.fillable("DPGF")).isFalse();
		assertThat(FormService.fillable("Mémoire technique")).isFalse();
		assertThat(FormService.outputName("DCE/26-13-DC2.doc", null)).isEqualTo("26-13-DC2 - rempli.docx");
	}

}
