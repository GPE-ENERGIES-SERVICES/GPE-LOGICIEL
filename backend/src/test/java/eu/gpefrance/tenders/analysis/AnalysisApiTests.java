package eu.gpefrance.tenders.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import eu.gpefrance.tenders.TestUsers;
import eu.gpefrance.tenders.TestcontainersConfiguration;
import eu.gpefrance.tenders.analysis.ExtractionClient.ModelAnswer;
import eu.gpefrance.tenders.document.Dossier;
import eu.gpefrance.tenders.document.DossierRepository;
import eu.gpefrance.tenders.document.DossierStatus;
import eu.gpefrance.tenders.document.TestFiles;
import eu.gpefrance.tenders.tender.Tender;
import eu.gpefrance.tenders.tender.TenderDetails;
import eu.gpefrance.tenders.tender.TenderRepository;

@SpringBootTest(properties = { "analysis.enabled=true", "analysis.provider=test",
		"analysis.external-transmission-allowed=true", "analysis.input-price-per-million=3",
		"analysis.output-price-per-million=15", "analysis.max-attempts=2", "analysis.max-documents-per-tender=2" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AnalysisApiTests {

	private static final Pattern DOCUMENT_ID = Pattern.compile("<document id=\"(\\d+)\"");

	/** 15/11/2026 at 12:00, Paris time. */
	private static final Instant NOTICE_DEADLINE = Instant.parse("2026-11-15T11:00:00Z");

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private TenderRepository tenders;

	@Autowired
	private DossierRepository dossiers;

	@Autowired
	private AnalysisRepository analyses;

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
		given(client.complete(any(ExtractionRequest.class))).willAnswer(invocation -> new ModelAnswer("""
				{"fields": [
				  {"key": "deadline", "status": "FOUND", "value": "2026-11-15T12:00",
				   "quote": "Les offres devront parvenir avant le 15/11/2026 à 12h00", "document_id": %s, "page": 1},
				  {"key": "site_visit", "status": "NOT_INDICATED"}
				]}""".formatted(documentId(invocation.<ExtractionRequest>getArgument(0).documents())), 1000, 200));
	}

	@Test
	void analysesARetainedTenderOnceItsDossierIsRead() throws InterruptedException {
		Tender tender = tender(true);
		upload(tender, "RC.pdf", "Règlement", "Les offres devront parvenir avant le 15/11/2026 à 12h00.");

		Analysis analysis = waitForAnalysis(tender, 1);

		assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.DONE);
		assertThat(mvc.get().uri("/api/tenders/{id}/analysis", tender.getId()).with(TestUsers.user()))
			.hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{"configured": true, "unavailableReason": null,
					 "latest": {"status": "DONE", "provider": "test", "model": "test-model", "attempts": 1, "inputTokens": 1000, "outputTokens": 200,
					   "cost": 0.006, "noticeDeadline": "2026-11-15T11:00:00Z",
					   "documents": [{"fileName": "RC.pdf", "analysed": true, "readable": true}]}}""");
		assertThat(analysis.getFields()).filteredOn(field -> field.getFieldKey().equals("deadline"))
			.singleElement()
			.satisfies(field -> {
				assertThat(field.getState()).isEqualTo(FieldState.VERIFIED);
				assertThat(field.getPageNumber()).isEqualTo(2);
			});
		assertThat(analysis.getFields()).filteredOn(field -> field.getFieldKey().equals("site_visit"))
			.extracting(ExtractedField::getState)
			.containsExactly(FieldState.NOT_INDICATED);
	}

	@Test
	void doesNotAnalyseTheSameContentTwiceUnlessAsked() throws InterruptedException {
		Tender tender = tender(true);
		Dossier dossier = upload(tender, "RC.pdf", "Les offres devront parvenir avant le 15/11/2026 à 12h00.");
		Analysis first = waitForAnalysis(tender, 1);

		assertThat(requestAnalysis(dossier, false)).hasStatus(HttpStatus.ACCEPTED)
			.bodyJson()
			.extractingPath("$.id")
			.isEqualTo(first.getId().intValue());
		assertThat(requestAnalysis(dossier, true)).hasStatus(HttpStatus.ACCEPTED);
		waitForAnalysis(tender, 2);

		verify(client, times(2)).complete(any(ExtractionRequest.class));
	}

	@Test
	void startsNothingByItselfForATenderSetAsideByTheFilter() throws InterruptedException {
		Tender tender = tender(false);
		Dossier dossier = upload(tender, "RC.pdf", "Les offres devront parvenir avant le 15/11/2026 à 12h00.");
		Thread.sleep(500);
		assertThat(analyses.count()).isZero();

		assertThat(requestAnalysis(dossier, false)).hasStatus(HttpStatus.ACCEPTED);
		assertThat(waitForAnalysis(tender, 1).getStatus()).isEqualTo(AnalysisStatus.DONE);
	}

	@Test
	void refusesADossierWithoutAnyReadablePiece() throws InterruptedException {
		Tender tender = tender(false);
		Dossier dossier = upload(tender, "DPGF.pdf", "Prix unitaires");

		assertThat(requestAnalysis(dossier, false)).hasStatus(HttpStatus.CONFLICT)
			.bodyJson()
			.extractingPath("$.detail")
			.asString()
			.contains("Aucun document analysable");
	}

	@Test
	void recordsAnUnreadableAnswerAsAFailureWithItsCost() throws InterruptedException {
		given(client.complete(any(ExtractionRequest.class))).willReturn(new ModelAnswer("Désolé.", 500, 10));
		Tender tender = tender(true);
		upload(tender, "RC.pdf", "Règlement");

		Analysis analysis = waitForAnalysis(tender, 1);

		assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.FAILED);
		assertThat(analysis.getErrorMessage()).contains("illisible");
		assertThat(analysis.getErrorKind()).isEqualTo(FailureKind.INVALID_RESPONSE);
		assertThat(analysis.getAttempts()).isEqualTo(1);
		assertThat(analysis.getInputTokens()).isEqualTo(500);
	}

	@Test
	void keepsTheValueOfTheModelWhenAUserCorrectsIt() throws InterruptedException {
		Tender tender = tender(true);
		upload(tender, "RC.pdf", "Les offres devront parvenir avant le 15/11/2026 à 12h00.");
		Analysis analysis = waitForAnalysis(tender, 1);
		Long deadlineId = analysis.getFields()
			.stream()
			.filter(field -> field.getFieldKey().equals("deadline"))
			.findFirst()
			.orElseThrow()
			.getId();

		assertThat(decide(deadlineId, "{\"decision\": \"CORRECTED\", \"value\": \"\"}"))
			.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(decide(deadlineId, "{\"decision\": \"CORRECTED\", \"value\": \"2026-11-16T12:00\"}"))
			.hasStatusOk();
		assertThat(mvc.post()
			.uri("/api/analyses/{id}/validation", analysis.getId())
			.with(TestUsers.user())
			.with(csrf())).hasStatusOk()
			.bodyJson()
			.extractingPath("$.latest.validatedBy")
			.isEqualTo("Marie Durand");

		ExtractedField deadline = analyses.findWithFieldsById(analysis.getId())
			.orElseThrow()
			.getFields()
			.stream()
			.filter(field -> field.getId().equals(deadlineId))
			.findFirst()
			.orElseThrow();
		assertThat(deadline.getValue()).isEqualTo("2026-11-15T12:00");
		assertThat(deadline.getDecision()).isEqualTo(FieldDecision.CORRECTED);
		assertThat(deadline.getCorrectedValue()).isEqualTo("2026-11-16T12:00");
		assertThat(deadline.getDecidedBy()).isEqualTo("Marie Durand");

		assertThat(decide(deadlineId, "{\"decision\": \"VALIDATED\"}")).hasStatusOk()
			.bodyJson()
			.extractingPath("$.latest.validatedBy")
			.isNull();
	}

	@Test
	void reportsTheCostAndTheDecisionsToTheAdministrator() throws InterruptedException {
		Tender tender = tender(true);
		upload(tender, "RC.pdf", "Les offres devront parvenir avant le 15/11/2026 à 12h00.");
		Analysis analysis = waitForAnalysis(tender, 1);
		Long deadlineId = analysis.getFields()
			.stream()
			.filter(field -> field.getFieldKey().equals("deadline"))
			.findFirst()
			.orElseThrow()
			.getId();
		decide(deadlineId, "{\"decision\": \"VALIDATED\"}");

		assertThat(mvc.get().uri("/api/analyses").with(TestUsers.user())).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(mvc.get().uri("/api/analyses").with(TestUsers.admin())).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					[{"tenderTitle": "Rénovation de l'école", "dossierVersion": 1, "status": "DONE",
					  "model": "test-model", "inputTokens": 1000, "outputTokens": 200, "cost": 0.006}]""");
		assertThat(mvc.get().uri("/api/analyses/quality").with(TestUsers.admin())).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{"done": 1, "failed": 0, "totalCost": 0.006, "averageCost": 0.006}""");
		assertThat(mvc.get().uri("/api/analyses/quality").with(TestUsers.admin()))
			.bodyJson()
			.extractingPath("$.fields[0]")
			.isEqualTo(Map.of("key", "deadline", "label", "Date et heure limite de remise des offres",
					"answers", 1, "verified", 1, "notIndicated", 0, "validated", 1, "corrected", 0, "rejected", 0));
	}

	private MvcTestResult decide(Long fieldId, String body) {
		return mvc.put()
			.uri("/api/extracted-fields/{id}/decision", fieldId)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.with(TestUsers.user())
			.with(csrf())
			.exchange();
	}

	private Tender tender(boolean retained) {
		Tender tender = new Tender("BOAMP", "26-1", new TenderDetails("Rénovation de l'école", null, null, null, null,
				List.of("69"), List.of(), null, List.of(), null, null, NOTICE_DEADLINE, null, null, List.of()),
				Instant.now());
		tender.applyFilter(retained, retained ? "Retenu" : "Écarté", Instant.now());
		return tenders.save(tender);
	}

	private Dossier upload(Tender tender, String fileName, String... pages) throws InterruptedException {
		assertThat(mvc.post()
			.uri("/api/tenders/{id}/dossiers", tender.getId())
			.multipart()
			.file(new MockMultipartFile("files", fileName, "application/pdf", TestFiles.pdf(pages)))
			.with(TestUsers.user())
			.with(csrf())).hasStatus(HttpStatus.ACCEPTED);
		for (int attempt = 0; attempt < 100; attempt++) {
			Dossier dossier = dossiers.findByTenderIdOrderByVersionDesc(tender.getId()).getFirst();
			if (dossier.getStatus() == DossierStatus.DONE) {
				return dossier;
			}
			Thread.sleep(100);
		}
		throw new AssertionError("Dossier not read in time");
	}

	private MvcTestResult requestAnalysis(Dossier dossier, boolean force) {
		return mvc.post()
			.uri("/api/dossiers/{id}/analyses?force={force}", dossier.getId(), force)
			.with(TestUsers.user())
			.with(csrf())
			.exchange();
	}

	/**
	 * Analyses run in the background: waits until the expected number of
	 * analyses of the tender are finished and returns the latest.
	 */
	private Analysis waitForAnalysis(Tender tender, int count) throws InterruptedException {
		for (int attempt = 0; attempt < 100; attempt++) {
			List<Analysis> all = analyses.findByTenderIdOrderByIdDesc(tender.getId());
			if (all.size() == count && all.stream()
				.allMatch(analysis -> analysis.getStatus() == AnalysisStatus.DONE
						|| analysis.getStatus() == AnalysisStatus.FAILED)) {
				return analyses.findWithFieldsById(all.getFirst().getId()).orElseThrow();
			}
			Thread.sleep(100);
		}
		throw new AssertionError("Analysis not finished in time");
	}

	private static String documentId(String documents) {
		Matcher matcher = DOCUMENT_ID.matcher(documents);
		return matcher.find() ? matcher.group(1) : "0";
	}

}
