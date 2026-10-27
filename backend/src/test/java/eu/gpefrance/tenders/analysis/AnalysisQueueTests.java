package eu.gpefrance.tenders.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
import eu.gpefrance.tenders.document.Dossier;
import eu.gpefrance.tenders.document.DossierRepository;
import eu.gpefrance.tenders.document.DossierStatus;
import eu.gpefrance.tenders.document.TestFiles;
import eu.gpefrance.tenders.tender.Tender;
import eu.gpefrance.tenders.tender.TenderDetails;
import eu.gpefrance.tenders.tender.TenderRepository;

/**
 * The queue of the analyses with a simulated provider: attempts, typed
 * errors, concurrency, restart, cancellation, versions and separation of the
 * tenders. Same configuration as {@link AnalysisApiTests}, so the same
 * context. No real provider is ever called.
 */
@SpringBootTest(properties = { "analysis.enabled=true", "analysis.provider=test",
		"analysis.external-transmission-allowed=true", "analysis.input-price-per-million=3",
		"analysis.output-price-per-million=15", "analysis.max-attempts=2", "analysis.max-documents-per-tender=2" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class AnalysisQueueTests {

	private static final Pattern DOCUMENT_ID = Pattern.compile("<document id=\"(\\d+)\"");

	private static final String SECRET = "sk-live-abcdefghijklmnopqrstuvwxyz";

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
	private AnalysisWorker worker;

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
	void triesATemporaryErrorAgainLaterThenStops() throws InterruptedException {
		given(client.complete(any(ExtractionRequest.class)))
			.willThrow(new ProviderException(FailureKind.TEMPORARY, "HTTP 503"));
		Dossier dossier = upload(tender("Travaux"), "RC.pdf");
		Instant before = Instant.now();

		Long id = service.request(dossier.getId(), false, "Marie Durand");
		Analysis waiting = waitFor(id, AnalysisStatus.RETRY_WAITING);

		assertThat(waiting.getAttempts()).isEqualTo(1);
		assertThat(waiting.getErrorKind()).isEqualTo(FailureKind.TEMPORARY);
		// 2 minutes, give or take the jitter: never at once.
		assertThat(waiting.getNextAttemptAt()).isAfter(before.plusSeconds(80));
		worker.poll();
		verify(client, times(1)).complete(any(ExtractionRequest.class));

		jdbc.update("UPDATE analysis SET next_attempt_at = now() - interval '1 second' WHERE id = ?", id);
		worker.poll();
		Analysis failed = waitFor(id, AnalysisStatus.FAILED);

		assertThat(failed.getAttempts()).isEqualTo(2);
		assertThat(failed.getErrorMessage()).contains("après 2 tentatives");
		verify(client, times(2)).complete(any(ExtractionRequest.class));
	}

	@Test
	void neverTriesARefusedKeyAgainNorShowsIt(CapturedOutput output) throws InterruptedException {
		given(client.complete(any(ExtractionRequest.class)))
			.willThrow(new ProviderException(FailureKind.AUTHENTICATION, "HTTP 401, x-api-key: " + SECRET));
		Tender tender = tender("Travaux");
		Dossier dossier = upload(tender, "RC.pdf");

		Analysis failed = waitFor(service.request(dossier.getId(), false, "Marie Durand"), AnalysisStatus.FAILED);
		worker.poll();

		assertThat(failed.getAttempts()).isEqualTo(1);
		assertThat(failed.getErrorKind()).isEqualTo(FailureKind.AUTHENTICATION);
		assertThat(failed.getErrorMessage()).contains("Clé du fournisseur d'IA refusée", SecretRedactor.MASK)
			.doesNotContain(SECRET);
		verify(client, times(1)).complete(any(ExtractionRequest.class));
		assertThat(mvc.get().uri("/api/tenders/{id}/analysis", tender.getId()).with(TestUsers.user())).hasStatusOk()
			.bodyText()
			.doesNotContain(SECRET);
		assertThat(mvc.get().uri("/api/analyses").with(TestUsers.admin())).hasStatusOk()
			.bodyText()
			.doesNotContain(SECRET);
		assertThat(output.getAll()).doesNotContain(SECRET);
	}

	@Test
	void sendsOneRequestAtATime() throws InterruptedException {
		CountDownLatch started = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		given(client.complete(any(ExtractionRequest.class))).willAnswer(invocation -> {
			started.countDown();
			release.await(10, TimeUnit.SECONDS);
			return answer(invocation.getArgument(0));
		});
		Dossier first = upload(tender("Travaux A"), "RC.pdf");
		Dossier second = upload(tender("Travaux B"), "RC.pdf");

		Long firstId = service.request(first.getId(), false, "Marie Durand");
		assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
		Long secondId = service.request(second.getId(), false, "Marie Durand");
		worker.poll();
		Thread.sleep(300);

		verify(client, times(1)).complete(any(ExtractionRequest.class));
		assertThat(analyses.findById(secondId).orElseThrow().getStatus()).isEqualTo(AnalysisStatus.PENDING);

		release.countDown();
		waitFor(firstId, AnalysisStatus.DONE);
		worker.poll();
		waitFor(secondId, AnalysisStatus.DONE);
		verify(client, times(2)).complete(any(ExtractionRequest.class));
	}

	@Test
	void resumesAnAnalysisInterruptedByAStopWithoutCallingAtStart() throws InterruptedException {
		Dossier dossier = upload(tender("Travaux"), "RC.pdf");
		Analysis analysis = queued(dossier, "a".repeat(64));
		jdbc.update("UPDATE analysis SET status = 'PROCESSING', locked_by = 'stopped-worker', locked_at = now(), "
				+ "attempts = 1 WHERE id = ?", analysis.getId());

		worker.recoverInterrupted();

		assertThat(analyses.findById(analysis.getId()).orElseThrow().getStatus()).isEqualTo(AnalysisStatus.PENDING);
		verify(client, never()).complete(any(ExtractionRequest.class));

		worker.poll();
		assertThat(waitFor(analysis.getId(), AnalysisStatus.DONE).getAttempts()).isEqualTo(2);
	}

	@Test
	void cancelsAnAnalysisNotSentYet() {
		Analysis analysis = queued(dossierWithoutFiles(tender("Travaux")), "a".repeat(64));

		assertThat(mvc.post().uri("/api/analyses/{id}/cancel", analysis.getId()).with(TestUsers.user()))
			.hasStatus(HttpStatus.FORBIDDEN);
		assertThat(mvc.post().uri("/api/analyses/{id}/cancel", analysis.getId()).with(TestUsers.user()).with(csrf()))
			.hasStatusOk()
			.bodyJson()
			.extractingPath("$.latest.status")
			.isEqualTo("CANCELLED");
		worker.poll();

		verify(client, never()).complete(any(ExtractionRequest.class));
		assertThat(mvc.post().uri("/api/analyses/{id}/cancel", analysis.getId()).with(TestUsers.user()).with(csrf()))
			.hasStatus(HttpStatus.CONFLICT);
	}

	@Test
	void analysesAgainWhenTheGridOrTheInstructionsChange() throws InterruptedException {
		Dossier dossier = upload(tender("Travaux"), "RC.pdf");
		Analysis older = new Analysis(dossier.getTenderId(), dossier.getId(), "test", "test-model", "grid-1/prompt-1",
				documentIds(dossier), "b".repeat(64), null, Instant.now());
		older.finish(100, 10, Instant.now());
		analyses.save(older);

		Long id = service.request(dossier.getId(), false, "Marie Durand");

		assertThat(id).isNotEqualTo(older.getId());
		assertThat(waitFor(id, AnalysisStatus.DONE).getPromptVersion()).isEqualTo(ExtractionPrompt.VERSION);
		verify(client, times(1)).complete(any(ExtractionRequest.class));
		assertThat(service.request(dossier.getId(), false, "Marie Durand")).isEqualTo(id);
		verify(client, times(1)).complete(any(ExtractionRequest.class));
	}

	@Test
	void keepsTheResultsOfEachTenderApartEvenForTheSameFile() throws InterruptedException {
		Dossier first = upload(tender("Travaux A"), "RC.pdf");
		Dossier second = upload(tender("Travaux B"), "RC.pdf");

		Analysis a = waitFor(service.request(first.getId(), false, "Marie Durand"), AnalysisStatus.DONE);
		Analysis b = waitFor(service.request(second.getId(), false, "Marie Durand"), AnalysisStatus.DONE);

		assertThat(a.getId()).isNotEqualTo(b.getId());
		assertThat(a.getDocumentIds()).isEqualTo(documentIds(first));
		assertThat(b.getDocumentIds()).isEqualTo(documentIds(second));
		assertThat(a.getFields()).extracting(ExtractedField::getDocumentId)
			.filteredOn(documentId -> documentId != null)
			.isNotEmpty()
			.allSatisfy(documentId -> assertThat(documentIds(first)).contains(documentId));
		assertThat(mvc.get().uri("/api/tenders/{id}/analysis", first.getTenderId()).with(TestUsers.user()))
			.bodyJson()
			.extractingPath("$.history.length()")
			.isEqualTo(1);
	}

	@Test
	void sendsNoMoreDocumentsThanAllowed() throws InterruptedException {
		Tender tender = tender("Travaux");
		Dossier dossier = upload(tender, "RC.pdf", "CCAP.pdf", "CCTP.pdf");

		Analysis analysis = waitFor(service.request(dossier.getId(), false, "Marie Durand"), AnalysisStatus.DONE);

		assertThat(analysis.getDocumentIds()).hasSize(2);
		assertThat(mvc.get().uri("/api/tenders/{id}/analysis", tender.getId()).with(TestUsers.user()))
			.bodyJson()
			.extractingPath("$.latest.documents[?(@.analysed == false)].fileName")
			.asArray()
			.containsExactly("CCTP.pdf");
	}

	@Test
	void checksTheRightsOnEachEndpointAndNeverReturnsASecret() {
		assertThat(mvc.get().uri("/api/tenders/1/analysis")).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.get().uri("/api/analyses/1")).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.post().uri("/api/dossiers/1/analyses").with(csrf())).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.post().uri("/api/dossiers/1/analyses").with(TestUsers.user())).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(mvc.get().uri("/api/analyses/configuration").with(TestUsers.user()))
			.hasStatus(HttpStatus.FORBIDDEN);
		assertThat(mvc.get().uri("/api/analyses/configuration").with(TestUsers.admin())).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{"state": "READY", "enabled": true, "provider": "test", "model": "test-model",
					 "externalTransmissionAllowed": true, "availableProviders": ["test"], "problems": [],
					 "limits": {"maxDocumentsPerTender": 2, "maxConcurrentRequests": 1, "maxAttempts": 2,
					   "pricesSet": true}}""");
		assertThat(mvc.get().uri("/api/analyses/configuration").with(TestUsers.admin())).bodyText()
			.doesNotContainIgnoringCase("apiKey")
			.doesNotContainIgnoringCase("secret")
			.doesNotContainIgnoringCase("authorization");
	}

	private static ModelAnswer answer(ExtractionRequest request) {
		Matcher matcher = DOCUMENT_ID.matcher(request.documents());
		String documentId = matcher.find() ? matcher.group(1) : "0";
		return new ModelAnswer("""
				{"fields": [
				  {"key": "deadline", "status": "FOUND", "value": "2026-11-15T12:00",
				   "quote": "Les offres devront parvenir avant le 15/11/2026 à 12h00", "document_id": %s, "page": 1}
				]}""".formatted(documentId), 1000, 200);
	}

	private Tender tender(String title) {
		Tender tender = new Tender("BOAMP", title, new TenderDetails(title, null, null, null, null, List.of("69"),
				List.of(), null, List.of(), null, null, null, null, null, List.of()), Instant.now());
		// Set aside by the filter: nothing starts by itself, each test asks.
		tender.applyFilter(false, "Écarté", Instant.now());
		return tenders.save(tender);
	}

	private Dossier upload(Tender tender, String... fileNames) throws InterruptedException {
		var request = mvc.post().uri("/api/tenders/{id}/dossiers", tender.getId()).multipart();
		for (String fileName : fileNames) {
			request = request.file(new MockMultipartFile("files", fileName, "application/pdf",
					TestFiles.pdf("Les offres devront parvenir avant le 15/11/2026 à 12h00. " + fileName)));
		}
		assertThat(request.with(TestUsers.user()).with(csrf())).hasStatus(HttpStatus.ACCEPTED);
		for (int attempt = 0; attempt < 100; attempt++) {
			Dossier dossier = dossiers.findByTenderIdOrderByVersionDesc(tender.getId()).getFirst();
			if (dossier.getStatus() == DossierStatus.DONE) {
				return dossiers.findById(dossier.getId()).orElseThrow();
			}
			Thread.sleep(100);
		}
		throw new AssertionError("Dossier not read in time");
	}

	private Dossier dossierWithoutFiles(Tender tender) {
		return dossiers.save(new Dossier(tender.getId(), 1, "Marie Durand", Instant.now()));
	}

	/**
	 * An analysis left in the queue by an earlier run, due in an hour: no
	 * poll takes it by itself.
	 */
	private Analysis queued(Dossier dossier, String fingerprint) {
		Analysis analysis = analyses.save(new Analysis(dossier.getTenderId(), dossier.getId(), "test", "test-model",
				ExtractionPrompt.VERSION, documentIds(dossier), fingerprint, "Marie Durand", Instant.now()));
		jdbc.update("UPDATE analysis SET next_attempt_at = ? WHERE id = ?",
				Timestamp.from(Instant.now().plus(1, ChronoUnit.HOURS)), analysis.getId());
		return analysis;
	}

	private Set<Long> documentIds(Dossier dossier) {
		return Set.copyOf(
				jdbc.queryForList("SELECT id FROM document WHERE dossier_id = ?", Long.class, dossier.getId()));
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
