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
	void saysWhenTheFileHoldsNoSuchForm() {
		Long piece = responses.add(tenderId, ResponsePiece.Category.APPLICATION, "Formulaire DC1", "Marie").pieces().getFirst().id();

		FormResponse form = service.view(piece);

		assertThat(form.documentName()).isNull();
		assertThat(form.problem()).contains("Aucun formulaire Word");
		assertThat(mvc.post()
			.uri("/api/response-pieces/{id}/form", piece)
			.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
			.content("{\"useAi\": false}")
			.with(TestUsers.user())
			.with(csrf())).hasStatus(HttpStatus.CONFLICT);
	}

	@Test
	void recognisesTheFormsItCanFill() {
		assertThat(FormService.fillable("Formulaire DC1")).isTrue();
		assertThat(FormService.fillable("Acte d'engagement")).isTrue();
		assertThat(FormService.fillable("DUME")).isFalse();
		assertThat(FormService.fillable("Extrait Kbis")).isFalse();
		assertThat(FormService.outputName("DCE/26-13-DC2.doc", null)).isEqualTo("26-13-DC2 - rempli.docx");
	}

}
