package eu.gpefrance.tenders.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import eu.gpefrance.tenders.TestUsers;
import eu.gpefrance.tenders.TestcontainersConfiguration;
import eu.gpefrance.tenders.analysis.ExtractionClient.ModelAnswer;
import eu.gpefrance.tenders.document.Dossier;
import eu.gpefrance.tenders.document.DossierReadEvent;
import eu.gpefrance.tenders.document.DossierRepository;
import eu.gpefrance.tenders.document.DossierStatus;
import eu.gpefrance.tenders.document.TestFiles;
import eu.gpefrance.tenders.tender.Tender;
import eu.gpefrance.tenders.tender.TenderDetails;
import eu.gpefrance.tenders.tender.TenderRepository;

/**
 * An analysis started by itself once a dossier is read waits before being
 * sent, so that a user can still cancel it, and does not start at all when a
 * user stopped it while the dossier was read. No AI is called.
 */
@SpringBootTest(properties = { "analysis.enabled=true", "analysis.provider=test",
		"analysis.external-transmission-allowed=true", "analysis.auto-start-delay=10m" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AutoAnalysisTests {

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
		given(client.complete(any(ExtractionRequest.class))).willReturn(new ModelAnswer("{\"fields\": []}", 10, 5));
	}

	@Test
	void waitsBeforeSendingAnAnalysisStartedByItselfAndLetsTheUserCancelIt() throws InterruptedException {
		Tender tender = tender();
		Dossier dossier = upload(tender);

		Analysis queued = waitForAnalysis(tender);

		assertThat(queued.getStatus()).isEqualTo(AnalysisStatus.PENDING);
		assertThat(queued.getRequestedBy()).isNull();
		assertThat(queued.getNextAttemptAt()).isCloseTo(Instant.now().plus(Duration.ofMinutes(10)),
				within(1, ChronoUnit.MINUTES));
		assertThat(mvc.get().uri("/api/tenders/{id}/analysis", tender.getId()).with(TestUsers.user()))
			.bodyJson()
			.extractingPath("$.latest.status")
			.isEqualTo("PENDING");
		assertThat(mvc.post().uri("/api/analyses/{id}/cancel", queued.getId()).with(TestUsers.user()).with(csrf()))
			.hasStatusOk();
		assertThat(analyses.findById(queued.getId()).orElseThrow().getStatus()).isEqualTo(AnalysisStatus.CANCELLED);
		verify(client, never()).complete(any(ExtractionRequest.class));
		assertThat(dossier.isAutoAnalysis()).isTrue();
	}

	@Test
	void sendsAtOnceAnAnalysisWaitingWhenTheUserDoesNotWantToWait() throws InterruptedException {
		Tender tender = tender();
		upload(tender);
		Analysis queued = waitForAnalysis(tender);

		assertThat(mvc.post().uri("/api/analyses/{id}/send-now", queued.getId()).with(TestUsers.user()).with(csrf()))
			.hasStatusOk();

		for (int attempt = 0; attempt < 100
				&& analyses.findById(queued.getId()).orElseThrow().getStatus() != AnalysisStatus.DONE; attempt++) {
			Thread.sleep(100);
		}
		assertThat(analyses.findById(queued.getId()).orElseThrow().getStatus()).isEqualTo(AnalysisStatus.DONE);
		// Once done, it cannot be sent again this way.
		assertThat(mvc.post().uri("/api/analyses/{id}/send-now", queued.getId()).with(TestUsers.user()).with(csrf()))
			.hasStatus(HttpStatus.CONFLICT);
	}

	@Test
	void neverStartsByItselfOnceAUserStoppedIt() throws InterruptedException {
		Tender tender = tender();
		Dossier dossier = upload(tender);
		waitForAnalysis(tender);
		jdbc.update("DELETE FROM analysis_document");
		jdbc.update("DELETE FROM analysis");

		assertThat(mvc.put().uri("/api/dossiers/{id}/auto-analysis", dossier.getId()).with(TestUsers.user())
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"enabled\": false}")).bodyJson()
			.extractingPath("$.autoAnalysis")
			.isEqualTo(false);
		service.onDossierRead(new DossierReadEvent(dossier.getId()));

		assertThat(analyses.findByTenderIdOrderByIdDesc(tender.getId())).isEmpty();
		// A user can still ask for it: it is sent at once.
		Long asked = service.request(dossier.getId(), false, "Marie Durand");
		assertThat(analyses.findById(asked).orElseThrow().getNextAttemptAt())
			.isBefore(Instant.now().plus(Duration.ofMinutes(1)));
	}

	private Tender tender() {
		Tender tender = new Tender("BOAMP", "Voirie", new TenderDetails("Voirie", null, null, null, null, List.of("69"),
				List.of(), null, List.of(), null, null, null, null, null, List.of()), Instant.now());
		tender.applyFilter(true, "Retenu", Instant.now());
		return tenders.save(tender);
	}

	private Dossier upload(Tender tender) throws InterruptedException {
		var request = mvc.post().uri("/api/tenders/{id}/dossiers", tender.getId()).multipart();
		request = request.file(new MockMultipartFile("files", "RC.pdf", "application/pdf",
				TestFiles.pdf("Règlement de la consultation : date limite le 15/11/2026.")));
		assertThat(request.with(TestUsers.user()).with(csrf())).hasStatus(HttpStatus.ACCEPTED);
		for (int attempt = 0; attempt < 100; attempt++) {
			Dossier dossier = dossiers.findByTenderIdOrderByVersionDesc(tender.getId()).getFirst();
			if (dossier.getStatus() == DossierStatus.DONE) {
				return dossier;
			}
			Thread.sleep(100);
		}
		throw new AssertionError("Dossier not read in time");
	}

	private Analysis waitForAnalysis(Tender tender) throws InterruptedException {
		for (int attempt = 0; attempt < 100; attempt++) {
			List<Analysis> found = analyses.findByTenderIdOrderByIdDesc(tender.getId());
			if (!found.isEmpty()) {
				return found.getFirst();
			}
			Thread.sleep(100);
		}
		throw new AssertionError("No analysis started by itself");
	}

}
