package eu.gpefrance.tenders.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.nio.file.Path;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import eu.gpefrance.tenders.TestUsers;
import eu.gpefrance.tenders.TestcontainersConfiguration;
import eu.gpefrance.tenders.analysis.Analysis;
import eu.gpefrance.tenders.analysis.AnalysisRepository;
import eu.gpefrance.tenders.analysis.AnalysisService;
import eu.gpefrance.tenders.analysis.AnalysisStatus;
import eu.gpefrance.tenders.analysis.ExtractionClient;
import eu.gpefrance.tenders.analysis.ExtractionClient.ModelAnswer;
import eu.gpefrance.tenders.analysis.ExtractionRequest;
import eu.gpefrance.tenders.tender.Tender;
import eu.gpefrance.tenders.tender.TenderDetails;
import eu.gpefrance.tenders.tender.TenderRepository;

/**
 * A consultation file analysed without any tender goes the whole way of a
 * dossier of a tender: upload, reading, analysis on request, decisions, journal.
 * Same configuration as AnalysisQueueTests, so the same context.
 */
@SpringBootTest(properties = { "analysis.enabled=true", "analysis.provider=test",
		"analysis.external-transmission-allowed=true", "analysis.input-price-per-million=3",
		"analysis.output-price-per-million=15", "analysis.max-attempts=2", "analysis.max-documents-per-tender=2" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DceReviewTests {

	private static final Pattern DOCUMENT_ID = Pattern.compile("<document id=\"(\\d+)\"");

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private DceReviewRepository reviews;

	@Autowired
	private DossierRepository dossiers;

	@Autowired
	private AnalysisRepository analyses;

	@Autowired
	private AnalysisService service;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private FileStorage storage;

	@Autowired
	private TenderRepository tenders;

	@MockitoBean
	private ExtractionClient client;

	@BeforeEach
	void cleanDatabase() {
		jdbc.execute("TRUNCATE extracted_field, analysis_document, analysis, document_page, document, dossier, dce_review, "
				+ "ai_call, tender_notice, lot, tender RESTART IDENTITY CASCADE");
		given(client.provider()).willReturn("test");
		given(client.model()).willReturn("test-model");
		given(client.complete(any(ExtractionRequest.class))).willAnswer(invocation -> {
			ExtractionRequest request = invocation.getArgument(0);
			Matcher matcher = DOCUMENT_ID.matcher(request.documents());
			String documentId = matcher.find() ? matcher.group(1) : "0";
			return new ModelAnswer("""
					{"fields": [
					  {"key": "deadline", "status": "FOUND", "value": "2026-11-15T12:00",
					   "quote": "Les offres devront parvenir avant le 15/11/2026 à 12h00", "document_id": %s, "page": 1}
					]}""".formatted(documentId), 1000, 200);
		});
	}

	@Test
	void analysesAFileWithoutAnyTender() throws InterruptedException {
		Long reviewId = create("Toiture de la mairie");
		Dossier dossier = upload(reviewId);

		assertThat(dossier.getTenderId()).isNull();
		assertThat(dossier.getVersion()).isEqualTo(1);
		// Read, but nothing is sent to the AI until a user asks.
		assertThat(analyses.findAll()).isEmpty();

		Long analysisId = service.request(dossier.getId(), false, "Marie Durand");
		Analysis done = waitForDone(analysisId);

		assertThat(done.getTenderId()).isNull();
		assertThat(done.getReviewId()).isEqualTo(reviewId);
		assertThat(mvc.get().uri("/api/dce-reviews/{id}/analysis", reviewId).with(TestUsers.user()))
			.hasStatusOk()
			.bodyJson()
			.extractingPath("$.latest.id")
			.isEqualTo(analysisId.intValue());
		assertThat(mvc.get().uri("/api/dce-reviews/{id}/financial-analysis", reviewId).with(TestUsers.user()))
			.hasStatusOk();
		// The same content is not analysed twice.
		assertThat(service.request(dossier.getId(), false, "Marie Durand")).isEqualTo(analysisId);
		// The routes that answer with the sheet find it without any tender.
		assertThat(mvc.post()
			.uri("/api/analyses/{id}/validation", analysisId)
			.with(TestUsers.user())
			.with(csrf())).hasStatusOk().bodyJson().extractingPath("$.latest.validatedBy").isNotNull();
		assertThat(mvc.get().uri("/api/analyses").with(TestUsers.admin()))
			.hasStatusOk()
			.bodyJson()
			.extractingPath("$[0].tenderTitle")
			.isEqualTo("Toiture de la mairie");
	}

	@Test
	void eachUploadIsANewVersion() throws InterruptedException {
		Long reviewId = create("Gymnase");
		upload(reviewId);

		assertThat(upload(reviewId).getVersion()).isEqualTo(2);
	}

	@Test
	void anAutomaticStartIsIgnored() throws InterruptedException {
		Dossier dossier = upload(create("Gymnase"));
		jdbc.update("UPDATE dossier SET auto_analysis = true WHERE id = ?", dossier.getId());

		service.onDossierRead(new DossierReadEvent(dossier.getId()));

		assertThat(analyses.findAll()).isEmpty();
	}

	@Test
	void renames() {
		Long reviewId = create("Gymnase");

		assertThat(mvc.put()
			.uri("/api/dce-reviews/{id}", reviewId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\": \"Gymnase de Bron\"}")
			.with(TestUsers.user())
			.with(csrf())).hasStatusOk();

		assertThat(reviews.findById(reviewId).orElseThrow().getName()).isEqualTo("Gymnase de Bron");
	}

	@Test
	void deletingRemovesEverythingButTheTokensStayCounted() throws InterruptedException {
		Long reviewId = create("Gymnase");
		Dossier dossier = upload(reviewId);
		waitForDone(service.request(dossier.getId(), false, "Marie Durand"));
		long before = analyses.sumTokensSince(Instant.EPOCH).getInput();
		Path stored = storage.resolve("reviews/" + reviewId);
		assertThat(stored).exists();

		assertThat(mvc.delete().uri("/api/dce-reviews/{id}", reviewId).with(TestUsers.user()).with(csrf()))
			.hasStatus(HttpStatus.NO_CONTENT);

		assertThat(reviews.existsById(reviewId)).isFalse();
		assertThat(dossiers.findById(dossier.getId())).isEmpty();
		assertThat(analyses.findAll()).isEmpty();
		assertThat(stored).doesNotExist();
		assertThat(analyses.sumTokensSince(Instant.EPOCH).getInput()).isEqualTo(before);
	}

	@Test
	void nothingIsDeletedNorAttachedWhileAnAnalysisRuns() throws InterruptedException {
		Long reviewId = create("Gymnase");
		Dossier dossier = upload(reviewId);
		waitForDone(service.request(dossier.getId(), false, "Marie Durand"));
		jdbc.update("UPDATE analysis SET status = 'RETRY_WAITING', next_attempt_at = now() + interval '1 hour'");

		assertThat(mvc.delete().uri("/api/dce-reviews/{id}", reviewId).with(TestUsers.user()).with(csrf()))
			.hasStatus(HttpStatus.CONFLICT);
		assertThat(attach(reviewId, tender("Gymnase").getId())).hasStatus(HttpStatus.CONFLICT);

		assertThat(reviews.existsById(reviewId)).isTrue();
	}

	@Test
	void attachingGivesTheFileToTheTenderAsItsLatestVersion() throws InterruptedException {
		Tender tender = tender("Gymnase de Bron");
		dossiers.save(new Dossier(tender.getId(), 1, "Marie Durand", Instant.now()));
		Long reviewId = create("Gymnase");
		Dossier dossier = upload(reviewId);
		Long analysisId = service.request(dossier.getId(), false, "Marie Durand");
		waitForDone(analysisId);

		assertThat(attach(reviewId, tender.getId())).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(reviews.existsById(reviewId)).isFalse();
		Dossier attached = dossiers.findByTenderIdOrderByVersionDesc(tender.getId()).getFirst();
		assertThat(attached.getId()).isEqualTo(dossier.getId());
		assertThat(attached.getVersion()).isEqualTo(2);
		assertThat(attached.getReviewId()).isNull();
		assertThat(mvc.get().uri("/api/tenders/{id}/analysis", tender.getId()).with(TestUsers.user()))
			.hasStatusOk()
			.bodyJson()
			.extractingPath("$.latest.id")
			.isEqualTo(analysisId.intValue());
	}

	@Test
	void attachingRefusesAnUnknownOrMergedTender() {
		Long reviewId = create("Gymnase");
		Tender merged = tender("Fusionné");
		jdbc.update("UPDATE tender SET merged_into_id = ? WHERE id = ?", tender("Conservé").getId(), merged.getId());

		assertThat(attach(reviewId, 999_999L)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(attach(reviewId, merged.getId())).hasStatus(HttpStatus.CONFLICT);

		assertThat(reviews.existsById(reviewId)).isTrue();
	}

	@Test
	void refusesAnEmptyNameAndAnUnknownReview() {
		assertThat(mvc.post()
			.uri("/api/dce-reviews")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\": \"  \"}")
			.with(TestUsers.user())
			.with(csrf())).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(mvc.get().uri("/api/dce-reviews/999").with(TestUsers.user())).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(mvc.get().uri("/api/dce-reviews/999/analysis").with(TestUsers.user()))
			.hasStatus(HttpStatus.NOT_FOUND);
	}

	private MvcTestResult attach(Long reviewId, Long tenderId) {
		return mvc.post()
			.uri("/api/dce-reviews/{id}/attachment", reviewId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"tenderId\": " + tenderId + "}")
			.with(TestUsers.user())
			.with(csrf())
			.exchange();
	}

	private Tender tender(String title) {
		Tender tender = new Tender("BOAMP", title, new TenderDetails(title, null, null, null, null, List.of("69"),
				List.of(), null, List.of(), null, null, null, null, null, List.of()), Instant.now());
		tender.applyFilter(false, "Écarté", Instant.now());
		return tenders.save(tender);
	}

	private Long create(String name) {
		assertThat(mvc.post()
			.uri("/api/dce-reviews")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\": \"" + name + "\"}")
			.with(TestUsers.user())
			.with(csrf())).hasStatus(HttpStatus.CREATED);
		return reviews.findAllByOrderByIdDesc().getFirst().getId();
	}

	private Dossier upload(Long reviewId) throws InterruptedException {
		int before = dossiers.findByReviewIdOrderByVersionDesc(reviewId).size();
		assertThat(mvc.post()
			.uri("/api/dce-reviews/{id}/dossiers", reviewId)
			.multipart()
			.file(new MockMultipartFile("files", "RC.pdf", "application/pdf",
					TestFiles.pdf("Les offres devront parvenir avant le 15/11/2026 à 12h00.")))
			.with(TestUsers.user())
			.with(csrf())).hasStatus(HttpStatus.ACCEPTED);
		for (int attempt = 0; attempt < 100; attempt++) {
			var found = dossiers.findByReviewIdOrderByVersionDesc(reviewId);
			if (found.size() > before && found.getFirst().getStatus() == DossierStatus.DONE) {
				return found.getFirst();
			}
			Thread.sleep(100);
		}
		throw new AssertionError("Dossier not read in time");
	}

	private Analysis waitForDone(Long id) throws InterruptedException {
		for (int attempt = 0; attempt < 100; attempt++) {
			Analysis analysis = analyses.findById(id).orElseThrow();
			if (analysis.getStatus() == AnalysisStatus.DONE) {
				return analysis;
			}
			Thread.sleep(100);
		}
		throw new AssertionError("Analysis " + id + " not done in time");
	}

}
