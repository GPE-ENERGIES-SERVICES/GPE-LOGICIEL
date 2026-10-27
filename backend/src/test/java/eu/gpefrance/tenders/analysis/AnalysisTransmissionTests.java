package eu.gpefrance.tenders.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.time.Instant;
import java.util.List;

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

import eu.gpefrance.tenders.TestUsers;
import eu.gpefrance.tenders.TestcontainersConfiguration;
import eu.gpefrance.tenders.document.DossierRepository;
import eu.gpefrance.tenders.document.DossierStatus;
import eu.gpefrance.tenders.document.TestFiles;
import eu.gpefrance.tenders.tender.Tender;
import eu.gpefrance.tenders.tender.TenderDetails;
import eu.gpefrance.tenders.tender.TenderRepository;

/**
 * A provider with its key and the analysis enabled, but the transmission of
 * the documents not allowed: a key alone never sends anything, not even for
 * a retained tender whose dossier has just been read.
 */
@SpringBootTest(properties = { "analysis.enabled=true", "analysis.provider=test" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AnalysisTransmissionTests {

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
	}

	@Test
	void sendsNothingWithoutExplicitPermission() throws InterruptedException {
		Tender tender = new Tender("BOAMP", "26-1", new TenderDetails("Travaux", null, null, null, null, List.of(),
				List.of(), null, List.of(), null, null, null, null, null, List.of()), Instant.now());
		tender.applyFilter(true, "Retenu", Instant.now());
		tender = tenders.save(tender);
		assertThat(mvc.post()
			.uri("/api/tenders/{id}/dossiers", tender.getId())
			.multipart()
			.file(new MockMultipartFile("files", "RC.pdf", "application/pdf", TestFiles.pdf("Règlement")))
			.with(TestUsers.user())
			.with(csrf())).hasStatus(HttpStatus.ACCEPTED);
		Long dossierId = waitUntilRead(tender);

		assertThat(mvc.post().uri("/api/dossiers/{id}/analyses", dossierId).with(TestUsers.user()).with(csrf()))
			.hasStatus(HttpStatus.CONFLICT)
			.bodyJson()
			.extractingPath("$.detail")
			.asString()
			.contains("n'est pas autorisé");
		assertThat(mvc.get().uri("/api/analyses/configuration").with(TestUsers.admin())).bodyJson()
			.isLenientlyEqualTo("{\"state\": \"TRANSMISSION_NOT_ALLOWED\", \"externalTransmissionAllowed\": false}");
		assertThat(analyses.count()).isZero();
		verify(client, never()).complete(any(ExtractionRequest.class));
	}

	private Long waitUntilRead(Tender tender) throws InterruptedException {
		for (int attempt = 0; attempt < 100; attempt++) {
			var dossier = dossiers.findByTenderIdOrderByVersionDesc(tender.getId()).getFirst();
			if (dossier.getStatus() == DossierStatus.DONE) {
				return dossier.getId();
			}
			Thread.sleep(100);
		}
		throw new AssertionError("Dossier not read in time");
	}

}
