package eu.gpefrance.tenders.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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
 * Scanned pages read by OCR, with a script answering like Tesseract: the
 * text and words are stored with the page, a plan is never read, and the
 * table rebuilt from the words reaches the analysis of the prices as read by
 * OCR. No real OCR nor AI is called.
 */
@SpringBootTest(properties = { "analysis.enabled=true", "analysis.provider=test",
		"analysis.external-transmission-allowed=true" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OcrDossierTests {

	/** A header and one line of a schedule, as Tesseract gives them at 300 dpi. */
	private static final String TSV = """
			level\tpage_num\tblock_num\tpar_num\tline_num\tword_num\tleft\ttop\twidth\theight\tconf\ttext
			5\t1\t1\t1\t1\t1\t292\t417\t250\t38\t96\tDésignation
			5\t1\t1\t1\t1\t2\t1250\t417\t40\t38\t95\tU
			5\t1\t1\t1\t1\t3\t1483\t417\t58\t38\t95\tQté
			5\t1\t1\t1\t1\t4\t1950\t417\t258\t38\t94\tMontant HT
			5\t1\t1\t1\t2\t1\t292\t492\t300\t38\t92\tDéblais en pleine masse
			5\t1\t1\t1\t2\t2\t1250\t492\t50\t38\t90\tm3
			5\t1\t1\t1\t2\t3\t1483\t492\t58\t38\t91\t350
			5\t1\t1\t1\t2\t4\t2050\t492\t158\t38\t90\t6 440,00
			""";

	private static final Path SCRIPT = fakeTesseract();

	@DynamicPropertySource
	static void ocr(DynamicPropertyRegistry registry) {
		registry.add("documents.ocr.command", SCRIPT::toString);
	}

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private TenderRepository tenders;

	@Autowired
	private DossierRepository dossiers;

	@Autowired
	private AnalysisService service;

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
		given(client.complete(any(ExtractionRequest.class)))
			.willReturn(new ModelAnswer("{\"overview\": \"o\", \"items\": [], \"points\": [], \"limits\": []}", 10, 5));
	}

	@Test
	void readsAScannedScheduleByOcrButNeverAPlanAndChecksItsTableAsReadByOcr() throws InterruptedException {
		Tender tender = tenders.save(new Tender("BOAMP", "Terrassement", new TenderDetails("Terrassement", null, null,
				null, null, List.of("69"), List.of(), null, List.of(), null, null, null, null, null, List.of()),
				Instant.now()));
		var upload = mvc.post().uri("/api/tenders/{id}/dossiers", tender.getId()).multipart();
		upload = upload.file(new MockMultipartFile("files", "DPGF scanné.pdf", "application/pdf", TestFiles.scannedPdf("")))
			.file(new MockMultipartFile("files", "Plan masse.pdf", "application/pdf", TestFiles.pdf("")))
			.file(new MockMultipartFile("files", "RC.pdf", "application/pdf", TestFiles.pdf("Règlement", "")));
		assertThat(upload.with(TestUsers.user()).with(csrf())).hasStatus(HttpStatus.ACCEPTED);
		Dossier dossier = waitUntilRead(tender);

		assertThat(jdbc.queryForList("SELECT d.file_name || ':' || d.state || ':' || coalesce(p.method, '') || ':' "
				+ "|| (p.words IS NOT NULL) FROM document d JOIN document_page p ON p.document_id = d.id ORDER BY d.file_name",
				String.class))
			// A blank page is no page left to read: the RC is read in full.
			.containsExactly("DPGF scanné.pdf:READ:OCR:true", "RC.pdf:READ:NATIVE:false", "RC.pdf:READ:NATIVE:false");
		// A plan is not read at all: its text never goes to the AI.
		assertThat(jdbc.queryForObject("SELECT state || ':' || (pdf_path IS NOT NULL) FROM document WHERE file_name = "
				+ "'Plan masse.pdf'", String.class)).isEqualTo("NOT_ANALYZED:true");
		assertThat(jdbc.queryForObject("SELECT state_detail FROM document WHERE file_name = 'DPGF scanné.pdf'",
				String.class)).isEqualTo("1 page(s) lue(s) par reconnaissance de caractères (OCR), confiance moyenne 93 % "
						+ ": texte à vérifier");
		assertThat(jdbc.queryForObject("SELECT text FROM document_page p JOIN document d ON d.id = p.document_id "
				+ "WHERE d.file_name = 'DPGF scanné.pdf'", String.class))
			.isEqualTo("Désignation U Qté Montant HT\nDéblais en pleine masse m3 350 6 440,00");

		Long id = service.requestFinancial(dossier.getId(), false, "Marie Durand");
		for (int attempt = 0; attempt < 100
				&& analyses.findById(id).orElseThrow().getStatus() != AnalysisStatus.DONE; attempt++) {
			Thread.sleep(100);
		}
		ArgumentCaptor<ExtractionRequest> sent = ArgumentCaptor.forClass(ExtractionRequest.class);
		verify(client, times(1)).complete(sent.capture());
		assertThat(sent.getValue().documents())
			.contains("structure ambiguë", "fiabilite=\"ambigue\"",
					"! Page(s) [1] lue(s) par reconnaissance de caractères (OCR)",
					"| A2 \"Déblais en pleine masse\" | B2 \"m3\" | C2 \"350\" | D2 \"6 440,00\"",
					"AMBIGUOUS_LAYOUT : Structure du tableau ambiguë, contrôles à confirmer");
	}

	private Dossier waitUntilRead(Tender tender) throws InterruptedException {
		for (int attempt = 0; attempt < 100; attempt++) {
			Dossier dossier = dossiers.findByTenderIdOrderByVersionDesc(tender.getId()).getFirst();
			if (dossier.getStatus() == DossierStatus.DONE) {
				return dossier;
			}
			Thread.sleep(100);
		}
		throw new AssertionError("Dossier not read in time");
	}

	private static Path fakeTesseract() {
		try {
			Path script = Files.createTempDirectory("ocr").resolve("fake-tesseract.sh");
			Files.writeString(script, "#!/bin/sh\ncat <<'EOF'\n" + TSV + "EOF\n");
			script.toFile().setExecutable(true);
			return script;
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
