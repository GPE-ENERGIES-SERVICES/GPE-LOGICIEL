package eu.gpefrance.tenders.document;

import static eu.gpefrance.tenders.document.TestFiles.entries;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import eu.gpefrance.tenders.TestUsers;
import eu.gpefrance.tenders.TestcontainersConfiguration;
import eu.gpefrance.tenders.tender.Tender;
import eu.gpefrance.tenders.tender.TenderDetails;
import eu.gpefrance.tenders.tender.TenderRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DocumentApiTests {

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private TenderRepository tenders;

	@Autowired
	private DossierRepository dossiers;

	@Autowired
	private DocumentService documentService;

	@Autowired
	private JdbcTemplate jdbc;

	private Tender tender;

	@BeforeEach
	void createTender() {
		jdbc.execute("TRUNCATE document_page, document, dossier, tender_notice, lot, tender RESTART IDENTITY CASCADE");
		tender = tenders.save(new Tender("BOAMP", "26-1", new TenderDetails("Travaux d'électricité", null, null, null,
				null, List.of("69"), List.of(), null, List.of(), null, null, null, null, null, List.of()),
				Instant.now()));
	}

	@Test
	void readsAnUploadedArchiveAndReportsEachDocument() throws InterruptedException {
		byte[] archive = TestFiles.zip(entries("RC.pdf", TestFiles.scannedPdf("Date limite : 15/11/2026", ""),
				"DPGF lot 1.xlsx", "tableur", "CCTP.docx", "word"));

		assertThat(upload(new MockMultipartFile("files", "DCE.zip", "application/zip", archive)))
			.hasStatus(HttpStatus.ACCEPTED)
			.bodyJson()
			.isLenientlyEqualTo("{\"version\": 1, \"origin\": \"UPLOAD\", \"uploadedBy\": \"Marie Durand\"}");

		DossierResponse dossier = waitUntilProcessed();
		assertThat(dossier.status()).isEqualTo(DossierStatus.DONE);
		assertThat(dossier.documents()).extracting(DossierResponse.DocumentResponse::fileName)
			.containsExactly("DCE.zip/CCTP.docx", "DCE.zip/DPGF lot 1.xlsx", "DCE.zip/RC.pdf");

		DossierResponse.DocumentResponse cctp = dossier.documents().get(0);
		assertThat(cctp.pieceType()).isEqualTo(PieceType.CCTP);
		assertThat(cctp.state()).isEqualTo(DocumentState.FAILED);
		assertThat(cctp.stateDetail()).contains("LibreOffice introuvable");

		DossierResponse.DocumentResponse dpgf = dossier.documents().get(1);
		assertThat(dpgf.pieceType()).isEqualTo(PieceType.DPGF);
		// Its content is not a spreadsheet, whatever its name.
		assertThat(dpgf.state()).isEqualTo(DocumentState.FAILED);
		assertThat(dpgf.stateDetail()).contains("ne correspond pas à l'extension");

		DossierResponse.DocumentResponse rc = dossier.documents().get(2);
		assertThat(rc.pieceType()).isEqualTo(PieceType.RC);
		assertThat(rc.state()).isEqualTo(DocumentState.SCANNED);
		assertThat(rc.pageCount()).isEqualTo(2);
		assertThat(rc.readable()).isTrue();

		assertThat(mvc.get().uri("/api/documents/{id}/pages/1", rc.id()).with(TestUsers.user())).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("{\"pageNumber\": 1, \"text\": \"Date limite : 15/11/2026\", \"method\": \"NATIVE\"}");
	}

	@Test
	void readsTextAndHtmlAndReportsEveryFileThatCannotBeRead() throws InterruptedException {
		byte[] archive = TestFiles.zip(entries("notes.txt", "Visite obligatoire le 3 novembre.", "avis.html",
				"<html><body><p>Avis : travaux de voirie</p><img src=\"http://127.0.0.1:1/x.png\"></body></html>",
				"RC.pdf", "%PDF-1.4 tronqué", "scan.pdf", TestFiles.pdf("", ""), "plan.xyz", "inconnu"));

		upload(new MockMultipartFile("files", "DCE.zip", "application/zip", archive));
		DossierResponse dossier = waitUntilProcessed();

		assertThat(dossier.documents()).extracting(DossierResponse.DocumentResponse::fileName,
				DossierResponse.DocumentResponse::state)
			.containsExactly(tuple("DCE.zip/avis.html", DocumentState.READ), tuple("DCE.zip/notes.txt", DocumentState.READ),
					tuple("DCE.zip/plan.xyz", DocumentState.NOT_ANALYZED), tuple("DCE.zip/RC.pdf", DocumentState.FAILED),
					tuple("DCE.zip/scan.pdf", DocumentState.SCANNED));
		assertThat(dossier.documents().get(3).stateDetail()).contains("illisible");
		assertThat(dossier.documents().get(2).stateDetail()).isEqualTo("Format non analysé");
		assertThat(dossier.documents().get(4).stateDetail()).contains("OCR");
		assertThat(mvc.get().uri("/api/documents/{id}/pages/1", dossier.documents().get(0).id()).with(TestUsers.user()))
			.bodyJson()
			.isLenientlyEqualTo("{\"text\": \"Avis : travaux de voirie\", \"method\": \"HTML\"}");
		assertThat(mvc.get().uri("/api/documents/{id}/pages/1", dossier.documents().get(1).id()).with(TestUsers.user()))
			.bodyJson()
			.isLenientlyEqualTo("{\"text\": \"Visite obligatoire le 3 novembre.\", \"method\": \"TEXT\"}");
	}

	@Test
	void servesTheReadablePdfInlineAndTheOriginalAsADownload() throws InterruptedException {
		upload(new MockMultipartFile("files", "RC lot 1.pdf", "application/pdf", TestFiles.pdf("Règlement")));
		Long documentId = waitUntilProcessed().documents().getFirst().id();

		MvcTestResult pdf = mvc.get().uri("/api/documents/{id}/pdf", documentId).with(TestUsers.user()).exchange();
		assertThat(pdf).hasStatusOk().hasContentType(MediaType.APPLICATION_PDF);
		assertThat(pdf.getResponse().getHeader("Content-Disposition")).startsWith("inline");

		MvcTestResult original = mvc.get().uri("/api/documents/{id}/file", documentId).with(TestUsers.user()).exchange();
		assertThat(original).hasStatusOk().hasContentType(MediaType.APPLICATION_OCTET_STREAM);
		assertThat(original.getResponse().getHeader("Content-Disposition")).startsWith("attachment")
			.contains("RC lot 1.pdf");
	}

	@Test
	void letsAUserCorrectThePieceType() throws InterruptedException {
		upload(new MockMultipartFile("files", "document.pdf", "application/pdf", TestFiles.pdf("texte")));
		DossierResponse.DocumentResponse document = waitUntilProcessed().documents().getFirst();
		assertThat(document.pieceType()).isEqualTo(PieceType.OTHER);

		assertThat(mvc.put()
			.uri("/api/documents/{id}/piece-type", document.id())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"pieceType\": \"CCAP\"}")
			.with(TestUsers.user())
			.with(csrf())).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("{\"pieceType\": \"CCAP\", \"pieceTypeCorrected\": true}");
	}

	@Test
	void keepsEachUploadAsANewVersion() throws InterruptedException {
		upload(new MockMultipartFile("files", "RC.pdf", "application/pdf", TestFiles.pdf("v1")));
		waitUntilProcessed();
		upload(new MockMultipartFile("files", "RC.pdf", "application/pdf", TestFiles.pdf("v2")));

		assertThat(dossiers.findByTenderIdOrderByVersionDesc(tender.getId())).extracting(Dossier::getVersion)
			.containsExactly(2, 1);
	}

	@Test
	void refusesAnUnreadableArchiveWithAReadableMessage() throws InterruptedException {
		assertThat(upload(new MockMultipartFile("files", "DCE.zip", "application/zip", "not a zip".getBytes())))
			.hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.detail")
			.asString()
			.contains("Archive illisible");
		assertThat(dossiers.count()).isZero();
	}

	@Test
	void answersNotFoundForAnUnknownTender() {
		assertThat(mvc.post()
			.uri("/api/tenders/999/dossiers")
			.multipart()
			.file(new MockMultipartFile("files", "RC.pdf", "application/pdf", TestFiles.pdf("x")))
			.with(TestUsers.user())
			.with(csrf())).hasStatus(HttpStatus.NOT_FOUND);
	}

	private MvcTestResult upload(MockMultipartFile file) {
		return mvc.post()
			.uri("/api/tenders/{id}/dossiers", tender.getId())
			.multipart()
			.file(file)
			.with(TestUsers.user())
			.with(csrf())
			.exchange();
	}

	/**
	 * Documents are read in the background: waits for the latest dossier.
	 */
	private DossierResponse waitUntilProcessed() throws InterruptedException {
		for (int attempt = 0; attempt < 100; attempt++) {
			Dossier latest = dossiers.findByTenderIdOrderByVersionDesc(tender.getId()).getFirst();
			if (latest.getStatus() == DossierStatus.DONE || latest.getStatus() == DossierStatus.FAILED) {
				return documentService.dossier(latest.getId());
			}
			Thread.sleep(100);
		}
		throw new AssertionError("Dossier not processed in time");
	}

}
