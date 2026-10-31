package eu.gpefrance.tenders.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import eu.gpefrance.tenders.analysis.ExtractionClient.ModelAnswer;
import eu.gpefrance.tenders.document.DocumentState;
import eu.gpefrance.tenders.document.Dossier;
import eu.gpefrance.tenders.document.DossierRepository;
import eu.gpefrance.tenders.document.DossierStatus;
import eu.gpefrance.tenders.document.TestFiles;
import eu.gpefrance.tenders.document.TestPdfTables;
import eu.gpefrance.tenders.document.TestSpreadsheets;
import eu.gpefrance.tenders.tender.Tender;
import eu.gpefrance.tenders.tender.TenderDetails;
import eu.gpefrance.tenders.tender.TenderRepository;

/**
 * The analysis of the price schedules: the spreadsheets read cell by cell,
 * checked by code, then sent with their checks only when a user asks. The
 * provider is a mock: no call leaves the tests.
 */
@SpringBootTest(properties = { "analysis.enabled=true", "analysis.provider=test",
		"analysis.external-transmission-allowed=true", "analysis.poll-interval=1h" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FinancialAnalysisTests {

	private static final Pattern DPGF_ID = Pattern.compile("<document id=\"(\\d+)\" nom=\"DPGF");

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private TenderRepository tenders;

	@Autowired
	private DossierRepository dossiers;

	@Autowired
	private AnalysisRepository analyses;

	@Autowired
	private AnalysisService service;

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoBean
	private ExtractionClient client;

	@BeforeEach
	void cleanDatabase() {
		jdbc.execute("TRUNCATE extracted_field, analysis_document, analysis, document_page, document, dossier, "
				+ "tender_notice, lot, tender RESTART IDENTITY CASCADE");
		given(client.provider()).willReturn("test");
		given(client.model()).willReturn("test-model");
		given(client.complete(any(ExtractionRequest.class))).willAnswer(invocation -> answer(invocation.getArgument(0)));
	}

	@Test
	void readsTheSpreadsheetCellByCellAndKeepsItOutOfTheSummary() throws InterruptedException {
		Tender tender = tender(false);
		Dossier dossier = upload(tender);

		assertThat(jdbc.queryForList("SELECT state FROM document WHERE file_name = 'DPGF Lot 1.xlsx'", String.class))
			.containsExactly(DocumentState.READ.name());
		assertThat(jdbc.queryForList("SELECT DISTINCT p.method FROM document_page p JOIN document d "
				+ "ON d.id = p.document_id WHERE d.file_name = 'DPGF Lot 1.xlsx'", String.class))
			.containsExactly("SHEET");

		// The summary of the consultation file is not changed: the spreadsheet stays out of it.
		Analysis summary = waitFor(service.request(dossier.getId(), false, "Marie Durand"), AnalysisStatus.DONE);

		ArgumentCaptor<ExtractionRequest> sent = ArgumentCaptor.forClass(ExtractionRequest.class);
		verify(client, times(1)).complete(sent.capture());
		assertThat(sent.getValue().analysisType()).isEqualTo(ExtractionRequest.DCE_SUMMARY);
		assertThat(sent.getValue().documents()).contains("RC.pdf").doesNotContain("DPGF", "Feuille «");
		assertThat(summary.getKind()).isEqualTo(AnalysisKind.DCE_SUMMARY);
		assertThat(summary.getDocumentIds()).hasSize(1);
	}

	@Test
	void neverStartsTheAnalysisOfThePricesByItself() throws InterruptedException {
		Tender tender = tender(true);
		upload(tender);

		// The summary starts by itself for a tender kept by the filter, the prices never.
		for (int attempt = 0; attempt < 100 && summaries(tender).isEmpty(); attempt++) {
			Thread.sleep(100);
		}
		assertThat(summaries(tender)).hasSize(1);
		assertThat(analyses.findByTenderIdAndKindOrderByIdDesc(tender.getId(), AnalysisKind.FINANCIAL)).isEmpty();
		assertThat(mvc.get().uri("/api/tenders/{id}/financial-analysis", tender.getId()).with(TestUsers.user()))
			.bodyJson()
			.extractingPath("$.latest")
			.isNull();
	}

	@Test
	void analysesThePricesOnRequestWithTheChecksOfTheCodeAndTheirSources() throws InterruptedException {
		Tender tender = tender(false);
		Dossier dossier = upload(tender);

		var response = assertThat(mvc.post()
			.uri("/api/dossiers/{id}/financial-analyses", dossier.getId())
			.with(TestUsers.user())
			.with(csrf()));
		response.hasStatus(HttpStatus.ACCEPTED);
		Long id = analyses.findByTenderIdAndKindOrderByIdDesc(tender.getId(), AnalysisKind.FINANCIAL)
			.getFirst()
			.getId();
		Analysis analysis = waitFor(id, AnalysisStatus.DONE);

		ArgumentCaptor<ExtractionRequest> sent = ArgumentCaptor.forClass(ExtractionRequest.class);
		verify(client, times(1)).complete(sent.capture());
		ExtractionRequest request = sent.getValue();
		assertThat(request.analysisType()).isEqualTo(ExtractionRequest.FINANCIAL);
		assertThat(request.promptVersion()).isEqualTo(FinancialPrompt.VERSION);
		assertThat(request.documents()).startsWith("<controles>")
			.contains("somme des montants calculée par le logiciel : 13740.00 HT",
					"LINE_MISMATCH F6 : Quantité × prix unitaire = 4200,00, montant écrit 4300,00",
					"Feuille « DPGF Lot 1 », lignes 1 à 14", "prix vides : cadre à remplir par les candidats")
			.doesNotContain("Date limite de réception");
		assertThat(analysis.getKind()).isEqualTo(AnalysisKind.FINANCIAL);

		for (int view = 0; view < 2; view++) {
			var json = assertThat(mvc.get().uri("/api/tenders/{id}/financial-analysis", tender.getId())
				.with(TestUsers.user())).bodyJson();
			json.extractingPath("$.latest.status").isEqualTo("DONE");
			json.extractingPath("$.pieces[0].fileName").isEqualTo("DPGF Lot 1.xlsx");
			json.extractingPath("$.pieces[0].analysed").isEqualTo(true);
			json.extractingPath("$.result.checks[0].computedTotal").isEqualTo(13740);
			json.extractingPath("$.result.checks[0].issues[0].kind").isEqualTo("LINE_MISMATCH");
			json.extractingPath("$.result.items[0].sheet").isEqualTo("DPGF Lot 1");
			json.extractingPath("$.result.items[0].row").isEqualTo(4);
			json.extractingPath("$.result.items[0].verified").isEqualTo(true);
			json.extractingPath("$.result.items[1].verified").isEqualTo(false);
			json.extractingPath("$.result.items[1].quote").isNull();
		}
		// The summary of the consultation file does not show the analysis of the prices.
		assertThat(mvc.get().uri("/api/tenders/{id}/analysis", tender.getId()).with(TestUsers.user()))
			.bodyJson()
			.extractingPath("$.latest")
			.isNull();
		// Asked again without force: the same analysis, no new call.
		assertThat(service.requestFinancial(dossier.getId(), false, "Marie Durand")).isEqualTo(id);
		verify(client, times(1)).complete(any(ExtractionRequest.class));
	}

	@Test
	void rebuildsTheTableOfADigitalPdfScheduleAndChecksItLikeASpreadsheet() throws InterruptedException {
		Tender tender = tender(false);
		var upload = mvc.post().uri("/api/tenders/{id}/dossiers", tender.getId()).multipart();
		upload = upload.file(new MockMultipartFile("files", "DPGF Lot 2.pdf", "application/pdf", TestPdfTables.dpgf()));
		assertThat(upload.with(TestUsers.user()).with(csrf())).hasStatus(HttpStatus.ACCEPTED);
		Dossier dossier = waitUntilRead(tender);

		Long id = service.requestFinancial(dossier.getId(), false, "Marie Durand");
		waitFor(id, AnalysisStatus.DONE);

		ArgumentCaptor<ExtractionRequest> sent = ArgumentCaptor.forClass(ExtractionRequest.class);
		verify(client, times(1)).complete(sent.capture());
		assertThat(sent.getValue().documents())
			.contains("Tableau 1 (p. 1)", "somme des montants calculée par le logiciel : 13740.00 HT",
					"LINE_MISMATCH F4 : Quantité × prix unitaire = 4200,00, montant écrit 4300,00 (page 1)",
					"<tableaux_pdf>", "fiabilite=\"fiable\"",
					"| D2 \"1 200\" | E2 \"2,50\" | F2 \"3 000,00\"")
			// The text of the page is still sent as it is, for the citations.
			.contains("<page numero=\"1\">");
		var json = assertThat(mvc.get().uri("/api/tenders/{id}/financial-analysis", tender.getId())
			.with(TestUsers.user())).bodyJson();
		json.extractingPath("$.result.checks[0].sheet").isEqualTo("Tableau 1 (p. 1)");
		json.extractingPath("$.result.checks[0].computedTotal").asNumber().matches(total -> total.doubleValue() == 13740);
		json.extractingPath("$.result.checks[0].totals[0].consistent").isEqualTo(true);
		json.extractingPath("$.result.items[0].verified").isEqualTo(true);
		json.extractingPath("$.result.items[0].page").isEqualTo(1);
	}

	@Test
	void sendsEachUsefulDocumentOnceNeverADrawingAndFollowsTheChoiceOfTheUser() throws InterruptedException {
		Tender tender = tender(false);
		byte[] rc = TestFiles.pdf("Règlement de la consultation : date limite de réception des offres le 15/11/2026.");
		var upload = mvc.post().uri("/api/tenders/{id}/dossiers", tender.getId()).multipart();
		upload = upload.file(new MockMultipartFile("files", "RC.pdf", "application/pdf", rc))
			.file(new MockMultipartFile("files", "RC copie.pdf", "application/pdf", rc))
			.file(new MockMultipartFile("files", "Det Bureau.pdf", "application/pdf", TestFiles.pdf("Det 1")))
			.file(new MockMultipartFile("files", "Diagnostic charpente.pdf", "application/pdf",
					TestFiles.pdf("Diagnostic de la charpente : portance insuffisante des pannes, renforts à prévoir.")));
		assertThat(upload.with(TestUsers.user()).with(csrf())).hasStatus(HttpStatus.ACCEPTED);
		Dossier dossier = waitUntilRead(tender);

		var documents = assertThat(mvc.get().uri("/api/tenders/{id}/dossiers", tender.getId()).with(TestUsers.user()))
			.bodyJson();
		documents.extractingPath("$[0].documents[?(@.fileName == 'RC copie.pdf')].aiReason").asArray()
			.containsExactly("Doublon de « RC »");
		documents.extractingPath("$[0].documents[?(@.fileName == 'Det Bureau.pdf')].aiUse").asArray()
			.containsExactly("EXCLUDED");
		documents.extractingPath("$[0].documents[?(@.fileName == 'Diagnostic charpente.pdf')].aiUse").asArray()
			.containsExactly("SUMMARY");

		waitFor(service.request(dossier.getId(), false, "Marie Durand"), AnalysisStatus.DONE);
		ArgumentCaptor<ExtractionRequest> sent = ArgumentCaptor.forClass(ExtractionRequest.class);
		verify(client, times(1)).complete(sent.capture());
		assertThat(sent.getValue().documents()).contains("nom=\"RC.pdf\"", "nom=\"Diagnostic charpente.pdf\"")
			.doesNotContain("RC copie.pdf", "Det Bureau.pdf");

		Long diagnostic = jdbc.queryForObject("SELECT id FROM document WHERE file_name = 'Diagnostic charpente.pdf'",
				Long.class);
		assertThat(mvc.put().uri("/api/documents/{id}/ai-choice", diagnostic).with(TestUsers.user()).with(csrf())
			.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
			.content("{\"choice\": \"EXCLUDE\"}")).bodyJson()
			.isLenientlyEqualTo("{\"aiUse\": \"EXCLUDED\", \"aiReason\": \"Exclu à la main\", \"aiChoice\": \"EXCLUDE\"}");
		// Another selection: a new analysis, asked for, without the diagnostic.
		waitFor(service.request(dossier.getId(), false, "Marie Durand"), AnalysisStatus.DONE);
		verify(client, times(2)).complete(sent.capture());
		assertThat(sent.getValue().documents()).doesNotContain("Diagnostic charpente.pdf");
	}

	@Test
	void refusesTheAnalysisOfThePricesWithoutReadableFinancialPiece() throws InterruptedException {
		Tender tender = tender(false);
		var request = mvc.post().uri("/api/tenders/{id}/dossiers", tender.getId()).multipart();
		request = request.file(new MockMultipartFile("files", "RC.pdf", "application/pdf",
				TestFiles.pdf("Date limite de réception des offres : 15/11/2026 à 12h00.")));
		assertThat(request.with(TestUsers.user()).with(csrf())).hasStatus(HttpStatus.ACCEPTED);
		Dossier dossier = waitUntilRead(tender);

		assertThat(mvc.post().uri("/api/dossiers/{id}/financial-analyses", dossier.getId()).with(TestUsers.user())
			.with(csrf())).hasStatus(HttpStatus.CONFLICT)
			.bodyJson()
			.extractingPath("$.detail")
			.asString()
			.startsWith("Aucune pièce financière lisible");
		// Only the analysis of the prices matters here: a summary of another test may still be running.
		verify(client, never()).complete(argThat(sent -> sent.analysisType().equals(ExtractionRequest.FINANCIAL)));
	}

	private static ModelAnswer answer(ExtractionRequest request) {
		if (!request.analysisType().equals(ExtractionRequest.FINANCIAL)) {
			return new ModelAnswer("{\"fields\": []}", 1000, 200);
		}
		Matcher matcher = DPGF_ID.matcher(request.documents());
		String documentId = matcher.find() ? matcher.group(1) : "0";
		return new ModelAnswer("""
				{"overview": "Un DPGF de terrassement, prix remplis ; un BPU à compléter.",
				 "items": [
				  {"text": "Décapage de terre végétale : 1200 m2", "document_id": %1$s, "page": 1, "row": 4,
				   "quote": "Décapage de terre végétale"},
				  {"text": "Poste absent du fichier", "document_id": %1$s, "page": 1, "row": 4,
				   "quote": "Fondations spéciales"}],
				 "points": [{"text": "Évacuation : montant différent de la quantité × prix", "document_id": %1$s,
				   "page": 1, "row": 6, "quote": "Évacuation des déblais"}],
				 "limits": ["Aucun prix n'est proposé pour le BPU."]}""".formatted(documentId), 3000, 400);
	}

	private List<Analysis> summaries(Tender tender) {
		return analyses.findByTenderIdAndKindOrderByIdDesc(tender.getId(), AnalysisKind.DCE_SUMMARY);
	}

	private Tender tender(boolean retained) {
		Tender tender = new Tender("BOAMP", "Terrassement", new TenderDetails("Terrassement", null, null, null, null,
				List.of("69"), List.of(), null, List.of(), null, null, null, null, null, List.of()), Instant.now());
		tender.applyFilter(retained, retained ? "Retenu" : "Écarté", Instant.now());
		return tenders.save(tender);
	}

	private Dossier upload(Tender tender) throws InterruptedException {
		var request = mvc.post().uri("/api/tenders/{id}/dossiers", tender.getId()).multipart();
		request = request
			.file(new MockMultipartFile("files", "RC.pdf", "application/pdf",
					TestFiles.pdf("Date limite de réception des offres : 15/11/2026 à 12h00.")))
			.file(new MockMultipartFile("files", "DPGF Lot 1.xlsx",
					"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", TestSpreadsheets.xlsx()));
		assertThat(request.with(TestUsers.user()).with(csrf())).hasStatus(HttpStatus.ACCEPTED);
		return waitUntilRead(tender);
	}

	private Dossier waitUntilRead(Tender tender) throws InterruptedException {
		for (int attempt = 0; attempt < 100; attempt++) {
			Dossier dossier = dossiers.findByTenderIdOrderByVersionDesc(tender.getId()).getFirst();
			if (dossier.getStatus() == DossierStatus.DONE) {
				return dossiers.findById(dossier.getId()).orElseThrow();
			}
			Thread.sleep(100);
		}
		throw new AssertionError("Dossier not read in time");
	}

	private Analysis waitFor(Long id, AnalysisStatus status) throws InterruptedException {
		for (int attempt = 0; attempt < 100; attempt++) {
			Analysis analysis = analyses.findWithFieldsById(id).orElseThrow();
			if (analysis.getStatus() == status) {
				return analysis;
			}
			Thread.sleep(100);
		}
		throw new AssertionError("Analysis " + id + " not " + status + " in time: "
				+ analyses.findById(id).orElseThrow().getStatus());
	}

}
