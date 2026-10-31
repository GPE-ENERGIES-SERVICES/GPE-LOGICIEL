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
import java.util.Map;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import eu.gpefrance.tenders.document.TestImages;
import eu.gpefrance.tenders.tender.Tender;
import eu.gpefrance.tenders.tender.TenderDetails;
import eu.gpefrance.tenders.tender.TenderRepository;

/**
 * Images of a consultation file read with a script answering like Tesseract:
 * the pieces are turned into a PDF and read by OCR, photos and plans stay
 * aside unless a user asks for them, damaged images and HEIC photos without
 * decoder are said so, PDFs are read as before, and the text read by OCR
 * reaches the AI marked as such. No real OCR nor AI is called.
 */
@SpringBootTest(properties = { "analysis.enabled=true", "analysis.provider=test",
		"analysis.external-transmission-allowed=true" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ImageDossierTests {

	/** A header and one line of a schedule; its amount read with doubt. */
	private static final String TSV = """
			level\tpage_num\tblock_num\tpar_num\tline_num\tword_num\tleft\ttop\twidth\theight\tconf\ttext
			5\t1\t1\t1\t1\t1\t292\t417\t250\t38\t96\tDésignation
			5\t1\t1\t1\t1\t2\t1250\t417\t40\t38\t95\tU
			5\t1\t1\t1\t1\t3\t1483\t417\t58\t38\t95\tQté
			5\t1\t1\t1\t1\t4\t1950\t417\t258\t38\t94\tMontant HT
			5\t1\t1\t1\t2\t1\t292\t492\t300\t38\t92\tDéblais en pleine masse
			5\t1\t1\t1\t2\t2\t1250\t492\t50\t38\t90\tm3
			5\t1\t1\t1\t2\t3\t1483\t492\t58\t38\t91\t350
			5\t1\t1\t1\t2\t4\t2050\t492\t158\t38\t61\t6 440,00
			""";

	private static final Path LOG = folder().resolve("calls.log");

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
	void cleanDatabase() throws IOException {
		jdbc.execute("TRUNCATE extracted_field, analysis_document, analysis, document_page, document, dossier, "
				+ "tender_notice, lot, tender RESTART IDENTITY CASCADE");
		Files.deleteIfExists(LOG);
		given(client.provider()).willReturn("test");
		given(client.model()).willReturn("test-model");
		given(client.complete(any(ExtractionRequest.class)))
			.willReturn(new ModelAnswer("{\"fields\": [], \"overview\": \"o\", \"items\": [], \"points\": [], \"limits\": []}", 10, 5));
	}

	@Test
	void readsTheImagesThatArePiecesKeepsTheOthersAsideAndMarksTheirTextForTheAi() throws Exception {
		Tender tender = tenders.save(new Tender("BOAMP", "Terrassement", new TenderDetails("Terrassement", null, null,
				null, null, List.of("69"), List.of(), null, List.of(), null, null, null, null, null, List.of()),
				Instant.now()));
		byte[] photo = TestImages.write(TestImages.marked(2000, 1500), "jpg");
		var upload = mvc.post().uri("/api/tenders/{id}/dossiers", tender.getId()).multipart();
		upload = upload.file(file("CCTP photo.jpg", photo))
			.file(file("DPGF lot 1.png", TestImages.write(TestImages.marked(1800, 2400), "png")))
			.file(file("IMG_2034.jpg", TestImages.write(TestImages.marked(1700, 1300), "jpg")))
			.file(file("Photo chantier.jpg", TestImages.write(TestImages.marked(1600, 1200), "jpg")))
			.file(file("Plan RDC.png", TestImages.write(TestImages.marked(1600, 1200), "png")))
			.file(file("CCAP scan.heic", new byte[] { 0, 0, 0, 24, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c' }))
			.file(file("CCTP annexe.png", "pas une image".getBytes()))
			.file(file("RC.pdf", TestFiles.pdf("Règlement de la consultation : date limite le 15/11/2026.")))
			.file(file("AE.pdf", TestFiles.scannedPdf("Acte d'engagement du marché", "")));
		assertThat(upload.with(TestUsers.user()).with(csrf())).hasStatus(HttpStatus.ACCEPTED);
		Dossier dossier = waitUntilRead(tender);

		Map<String, String> states = states();
		assertThat(states).containsEntry("CCTP photo.jpg", "READ")
			.containsEntry("IMG_2034.jpg", "READ")
			.containsEntry("DPGF lot 1.png", "READ")
			.containsEntry("Photo chantier.jpg", "NOT_ANALYZED")
			.containsEntry("Plan RDC.png", "NOT_ANALYZED")
			.containsEntry("CCAP scan.heic", "NOT_ANALYZED")
			.containsEntry("CCTP annexe.png", "FAILED")
			.containsEntry("RC.pdf", "READ")
			.containsEntry("AE.pdf", "READ");
		assertThat(detail("Photo chantier.jpg")).startsWith("Image conservée, non lue");
		assertThat(detail("Plan RDC.png")).startsWith("Image conservée, non lue");
		assertThat(detail("CCAP scan.heic")).isEqualTo("Photo HEIC : décodeur absent du serveur (libheif), non analysée");
		assertThat(detail("CCTP annexe.png")).startsWith("Image illisible");
		assertThat(detail("CCTP photo.jpg"))
			.isEqualTo("1 page(s) lue(s) par reconnaissance de caractères (OCR), confiance moyenne 89 % : texte à vérifier");
		// Where each text comes from: the file, its page, how it was read, the words with their place.
		assertThat(jdbc.queryForList("SELECT d.file_name || ':' || p.page_number || ':' || p.method || ':' "
				+ "|| (p.words IS NOT NULL) || ':' || (d.pdf_path IS NOT NULL) FROM document d "
				+ "JOIN document_page p ON p.document_id = d.id ORDER BY d.file_name, p.page_number", String.class))
			.containsExactly("AE.pdf:1:NATIVE:false:true", "AE.pdf:2:OCR:true:true", "CCTP photo.jpg:1:OCR:true:true",
					"DPGF lot 1.png:1:OCR:true:true", "IMG_2034.jpg:1:OCR:true:true", "RC.pdf:1:NATIVE:false:true");
		// The file uploaded is kept as it is; the PDF read is beside it.
		String stored = jdbc.queryForObject("SELECT storage_path FROM document WHERE file_name = 'CCTP photo.jpg'",
				String.class);
		assertThat(jdbc.queryForObject("SELECT pdf_path FROM document WHERE file_name = 'CCTP photo.jpg'", String.class))
			.endsWith("CCTP photo.pdf")
			.isNotEqualTo(stored);
		// Neither the photo nor the plan were given to Tesseract: three images and one scanned page; the image
		// whose name says nothing was read as Tesseract found letters in it.
		assertThat(calls()).containsExactlyInAnyOrder("osd", "osd", "osd", "tsv", "tsv", "tsv", "tsv");

		// A user wants the photo: it is read.
		Long photoId = jdbc.queryForObject("SELECT id FROM document WHERE file_name = 'Photo chantier.jpg'", Long.class);
		assertThat(mvc.put().uri("/api/documents/{id}/ai-choice", photoId).with(TestUsers.user()).with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"choice\": \"INCLUDE\"}")).hasStatusOk();
		waitFor(() -> "READ".equals(states().get("Photo chantier.jpg")));
		assertThat(calls()).hasSize(9);

		Long summary = service.request(dossier.getId(), false, "Marie Durand");
		waitFor(() -> analyses.findById(summary).orElseThrow().getStatus() == AnalysisStatus.DONE);
		Long financial = service.requestFinancial(dossier.getId(), false, "Marie Durand");
		waitFor(() -> analyses.findById(financial).orElseThrow().getStatus() == AnalysisStatus.DONE);
		ArgumentCaptor<ExtractionRequest> sent = ArgumentCaptor.forClass(ExtractionRequest.class);
		verify(client, times(2)).complete(sent.capture());
		String sentForSummary = sent.getAllValues().getFirst().documents();
		assertThat(sentForSummary).contains("nom=\"CCTP photo.jpg\"", "nom=\"Photo chantier.jpg\"", "nom=\"RC.pdf\"")
			.contains("<page numero=\"1\">\n" + AnalysisService.OCR_PAGE + "\nDésignation U Qté Montant HT")
			.contains("<page numero=\"1\">\nRèglement de la consultation")
			.contains("<page numero=\"2\">\n" + AnalysisService.OCR_PAGE)
			.doesNotContain("Plan RDC", "CCAP scan");
		// The amount read with doubt stays in doubt.
		assertThat(sent.getAllValues().getLast().documents()).contains("DPGF lot 1.png", "fiabilite=\"ambigue\"",
				"! Page(s) [1] lue(s) par reconnaissance de caractères (OCR)");
	}

	@Test
	void takesTheReadingOfFilesAlreadyReadAndReadsAPlanOnlyWhenAsked() throws Exception {
		Tender tender = tenders.save(new Tender("BOAMP", "Voirie", new TenderDetails("Voirie", null, null, null, null,
				List.of("69"), List.of(), null, List.of(), null, null, null, null, null, List.of()), Instant.now()));
		byte[] cctp = TestFiles.scannedPdf("Cahier des clauses techniques", "");
		for (int version = 1; version <= 2; version++) {
			var upload = mvc.post().uri("/api/tenders/{id}/dossiers", tender.getId()).multipart();
			upload = upload.file(file("CCTP.pdf", cctp))
				.file(file("Annexes - CCTP.pdf", cctp))
				.file(file("Plan RDC.pdf", TestFiles.pdf("Cuisine 12 m2")));
			assertThat(upload.with(TestUsers.user()).with(csrf())).hasStatus(HttpStatus.ACCEPTED);
			waitUntilRead(tender);
		}

		// The scanned page was read once: the copy in the archive and the file deposited again took its reading.
		assertThat(calls()).containsExactly("tsv");
		assertThat(jdbc.queryForList("SELECT d.state || ':' || count(p.id) || ':' || (d.pdf_path IS NOT NULL) "
				+ "FROM document d LEFT JOIN document_page p ON p.document_id = d.id WHERE d.file_name LIKE '%CCTP.pdf' "
				+ "GROUP BY d.id ORDER BY d.id", String.class))
			.containsExactly("READ:2:true", "READ:2:true", "READ:2:true", "READ:2:true");
		assertThat(jdbc.queryForList("SELECT p.method FROM document_page p JOIN document d ON d.id = p.document_id "
				+ "WHERE d.file_name = 'Annexes - CCTP.pdf' ORDER BY d.id, p.page_number", String.class))
			.containsExactly("NATIVE", "OCR", "NATIVE", "OCR");
		// A plan is not read: its text never goes to the AI. It still opens, and is read when a user asks.
		Long plan = jdbc.queryForObject("SELECT max(id) FROM document WHERE file_name = 'Plan RDC.pdf'", Long.class);
		assertThat(states().get("Plan RDC.pdf")).isEqualTo("NOT_ANALYZED");
		assertThat(jdbc.queryForObject("SELECT state_detail FROM document WHERE id = ?", String.class, plan))
			.startsWith("Plan ou dessin (d'après son nom) : texte non lu");
		assertThat(mvc.put().uri("/api/documents/{id}/ai-choice", plan).with(TestUsers.user()).with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"choice\": \"INCLUDE\"}")).hasStatusOk();
		waitFor(() -> "READ".equals(jdbc.queryForObject("SELECT state FROM document WHERE id = ?", String.class, plan)));
		assertThat(jdbc.queryForObject("SELECT text FROM document_page WHERE document_id = ?", String.class, plan))
			.contains("Cuisine 12 m2");
	}

	private Map<String, String> states() {
		Map<String, String> states = new java.util.HashMap<>();
		for (Map<String, Object> row : jdbc.queryForList("SELECT file_name, state FROM document")) {
			states.put((String) row.get("file_name"), (String) row.get("state"));
		}
		return states;
	}

	private String detail(String fileName) {
		return jdbc.queryForObject("SELECT state_detail FROM document WHERE file_name = ?", String.class, fileName);
	}

	private static List<String> calls() throws IOException {
		return Files.exists(LOG) ? Files.readAllLines(LOG) : List.of();
	}

	private static MockMultipartFile file(String name, byte[] content) {
		return new MockMultipartFile("files", name, "application/octet-stream", content);
	}

	private Dossier waitUntilRead(Tender tender) throws InterruptedException {
		for (int attempt = 0; attempt < 200; attempt++) {
			Dossier dossier = dossiers.findByTenderIdOrderByVersionDesc(tender.getId()).getFirst();
			if (dossier.getStatus() == DossierStatus.DONE) {
				return dossier;
			}
			Thread.sleep(100);
		}
		throw new AssertionError("Dossier not read in time");
	}

	private static void waitFor(Supplier<Boolean> condition) throws InterruptedException {
		for (int attempt = 0; attempt < 200 && !condition.get(); attempt++) {
			Thread.sleep(100);
		}
		assertThat(condition.get()).isTrue();
	}

	private static Path folder() {
		try {
			return Files.createTempDirectory("image-ocr");
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/**
	 * Answers like Tesseract: its orientation detection finds the text upright,
	 * its reading gives the schedule. Each call is written down.
	 */
	private static Path fakeTesseract() {
		try {
			Path script = LOG.resolveSibling("fake-tesseract.sh");
			Files.writeString(script, "#!/bin/sh\ncase \"$*\" in\n*\"--psm 0\"*)\necho osd >> '" + LOG + "'\n"
					+ "printf 'Rotate: 0\\nOrientation confidence: 9.1\\n'\n;;\n*)\necho tsv >> '" + LOG + "'\n"
					+ "cat <<'EOF'\n" + TSV + "EOF\n;;\nesac\n");
			script.toFile().setExecutable(true);
			return script;
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
