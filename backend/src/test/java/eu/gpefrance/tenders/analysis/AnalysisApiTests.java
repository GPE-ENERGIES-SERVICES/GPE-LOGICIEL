package eu.gpefrance.tenders.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
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

@SpringBootTest(properties = { "analysis.input-price-per-million=3", "analysis.output-price-per-million=15" })
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
		given(client.model()).willReturn("test-model");
		given(client.complete(anyString(), anyString())).willAnswer(invocation -> new ModelAnswer("""
				{"fields": [
				  {"key": "deadline", "status": "FOUND", "value": "2026-11-15T12:00",
				   "quote": "Les offres devront parvenir avant le 15/11/2026 à 12h00", "document_id": %s, "page": 1},
				  {"key": "site_visit", "status": "NOT_INDICATED"}
				]}""".formatted(documentId(invocation.getArgument(1))), 1000, 200));
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
					{"configured": true,
					 "latest": {"status": "DONE", "model": "test-model", "inputTokens": 1000, "outputTokens": 200,
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

		verify(client, times(2)).complete(anyString(), anyString());
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
	void refusesADossierWithoutConsultationRules() throws InterruptedException {
		Tender tender = tender(false);
		Dossier dossier = upload(tender, "CCTP.pdf", "Prescriptions techniques");

		assertThat(requestAnalysis(dossier, false)).hasStatus(HttpStatus.CONFLICT)
			.bodyJson()
			.extractingPath("$.detail")
			.asString()
			.contains("Aucun document analysable");
	}

	@Test
	void recordsAnUnreadableAnswerAsAFailureWithItsCost() throws InterruptedException {
		given(client.complete(anyString(), anyString())).willReturn(new ModelAnswer("Désolé.", 500, 10));
		Tender tender = tender(true);
		upload(tender, "RC.pdf", "Règlement");

		Analysis analysis = waitForAnalysis(tender, 1);

		assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.FAILED);
		assertThat(analysis.getErrorMessage()).contains("illisible");
		assertThat(analysis.getInputTokens()).isEqualTo(500);
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
