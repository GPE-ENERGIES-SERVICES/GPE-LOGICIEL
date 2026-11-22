package eu.gpefrance.tenders.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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

import eu.gpefrance.tenders.TestUsers;
import eu.gpefrance.tenders.company.CompanyDocument;
import eu.gpefrance.tenders.TestcontainersConfiguration;
import eu.gpefrance.tenders.analysis.Analysis;
import eu.gpefrance.tenders.analysis.AnalysisKind;
import eu.gpefrance.tenders.analysis.AnalysisRepository;
import eu.gpefrance.tenders.analysis.ExtractedField;
import eu.gpefrance.tenders.analysis.FieldDecision;
import eu.gpefrance.tenders.analysis.FieldState;
import eu.gpefrance.tenders.document.Dossier;
import eu.gpefrance.tenders.document.DossierRepository;
import eu.gpefrance.tenders.document.FileStorage;
import eu.gpefrance.tenders.document.TestFiles;
import eu.gpefrance.tenders.response.ResponseService.PieceResponse;
import eu.gpefrance.tenders.response.ResponseService.PieceState;
import eu.gpefrance.tenders.tender.Tender;
import eu.gpefrance.tenders.tender.TenderDetails;
import eu.gpefrance.tenders.tender.TenderRepository;

/**
 * The analysis and the company propose, a user decides: what a user chose
 * survives a new preparation, and the archive holds what is joined.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ResponseTests {

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private ResponseService service;

	@Autowired
	private TenderRepository tenders;

	@Autowired
	private DossierRepository dossiers;

	@Autowired
	private AnalysisRepository analyses;

	@Autowired
	private FileStorage storage;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void cleanDatabase() {
		jdbc.execute("TRUNCATE response_piece, extracted_field, analysis_document, analysis, document_page, document, "
				+ "dossier, company_file, company_document, tender_notice, lot, tender RESTART IDENTITY CASCADE");
	}

	@Test
	void takesThePiecesOfTheAnalysisAndProposesTheDocumentsOfTheCompany() throws IOException {
		Long tender = tender();
		Long kbis = document("KBIS", "Extrait Kbis", LocalDate.now().plusMonths(2), "kbis.pdf");
		document("KBIS", "Ancien Kbis", LocalDate.now().minusDays(1), "ancien.pdf");
		analysis(tender, "application_documents", "Extrait Kbis", null, null);
		analysis(tender, "application_documents", "Pièce rejetée", FieldDecision.REJECTED, null);
		analysis(tender, "application_documents", "Atestation", FieldDecision.CORRECTED, "Attestation de vigilance");
		analysis(tender, "offer_documents", "DPGF", null, null);
		analysis(tender, "site_visit", "obligatoire", null, null);

		List<PieceResponse> pieces = service.prepare(tender, "Marie").pieces();

		assertThat(pieces).extracting(PieceResponse::label)
			.containsExactlyInAnyOrder("Extrait Kbis", "Attestation de vigilance", "DPGF");
		PieceResponse proposed = piece(pieces, "Extrait Kbis");
		assertThat(proposed.companyDocumentId()).isEqualTo(kbis);
		assertThat(proposed.state()).isEqualTo(PieceState.TO_CHECK);
		assertThat(piece(pieces, "Attestation de vigilance").state()).isEqualTo(PieceState.MISSING);
		assertThat(piece(pieces, "DPGF").detail()).contains("Aucun chiffrage");
	}

	@Test
	void aNewPreparationKeepsWhatAUserDecided() throws IOException {
		Long tender = tender();
		document("KBIS", "Extrait Kbis", null, "kbis.pdf");
		analysis(tender, "application_documents", "Extrait Kbis", null, null);
		PieceResponse piece = service.prepare(tender, "Marie").pieces().getFirst();
		service.check(piece.id(), true, "Marie");
		service.change(piece.id(), "Kbis de moins de 3 mois", false, null, "Marie");
		service.change(piece.id(), "Kbis de moins de 3 mois", false, piece.companyDocumentId(), "Marie");
		service.check(piece.id(), true, "Marie");

		analysis(tender, "offer_documents", "Mémoire technique", null, null);
		List<PieceResponse> again = service.prepare(tender, "Paul").pieces();

		assertThat(again).hasSize(2);
		PieceResponse kept = piece(again, "Kbis de moins de 3 mois");
		assertThat(kept.state()).isEqualTo(PieceState.READY);
		assertThat(kept.checkedBy()).isEqualTo("Marie");
	}

	@Test
	void changingWhatIsJoinedRemovesTheCheck() throws IOException {
		Long tender = tender();
		Long kbis = document("KBIS", "Extrait Kbis", null, "kbis.pdf");
		Long other = document("KBIS", "Autre Kbis", null, "autre.pdf");
		PieceResponse piece = service.add(tender, ResponsePiece.Category.APPLICATION, "Extrait Kbis", "Marie").pieces().getFirst();
		service.change(piece.id(), piece.label(), false, kbis, "Marie");
		assertThat(service.check(piece.id(), true, "Marie").pieces().getFirst().state()).isEqualTo(PieceState.READY);

		PieceResponse changed = service.change(piece.id(), piece.label(), false, other, "Marie").pieces().getFirst();

		assertThat(changed.state()).isEqualTo(PieceState.TO_CHECK);
		assertThat(changed.checkedBy()).isNull();
	}

	@Test
	void anExpiredDocumentIsShownAndAPieceWithoutFileCannotBeChecked() throws IOException {
		Long tender = tender();
		Long expired = document("TAX", "Attestation fiscale", LocalDate.now().minusDays(1), "fiscale.pdf");
		PieceResponse piece = service.add(tender, ResponsePiece.Category.APPLICATION, "Attestation fiscale", "Marie").pieces().getFirst();
		assertThat(piece.companyDocumentId()).as("an expired document is not proposed").isNull();
		assertThat(mvc.put()
			.uri("/api/response-pieces/{id}/check", piece.id())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"checked\": true}")
			.with(TestUsers.user())
			.with(csrf())).hasStatus(HttpStatus.CONFLICT);

		assertThat(service.change(piece.id(), piece.label(), false, expired, "Marie").pieces().getFirst().state())
			.isEqualTo(PieceState.EXPIRED);
	}

	@Test
	void theFileOfAUserGoesInTheArchiveUnderItsPart() throws IOException {
		Long tender = tender();
		document("KBIS", "Extrait Kbis", null, "kbis.pdf");
		service.add(tender, ResponsePiece.Category.APPLICATION, "Extrait Kbis", "Marie");
		PieceResponse offer = service.add(tender, ResponsePiece.Category.OFFER, "DPGF", "Marie").pieces().getLast();
		PieceResponse notNeeded = service.add(tender, ResponsePiece.Category.OFFER, "Planning", "Marie").pieces().getLast();
		service.change(notNeeded.id(), notNeeded.label(), true, null, "Marie");

		assertThat(mvc.post()
			.uri("/api/response-pieces/{id}/file", offer.id())
			.multipart()
			.file(new MockMultipartFile("file", "DPGF rempli.pdf", "application/pdf", TestFiles.pdf("Prix")))
			.with(TestUsers.user())
			.with(csrf())).hasStatusOk();

		var result = mvc.get().uri("/api/tenders/{id}/response/archive", tender).with(TestUsers.user()).exchange();
		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		// Each envelope holds the folders of GPE; one envelope when the consultation asks it.
		assertThat(names(result.getResponse().getContentAsByteArray()))
			.containsExactlyInAnyOrder("1-Candidature/02-Situation GPE/kbis.pdf", "2-Offre/01-Pieces principales/DPGF rempli.pdf");
		var single = mvc.get().uri("/api/tenders/{id}/response/archive?single=true", tender).with(TestUsers.user()).exchange();
		assertThat(names(single.getResponse().getContentAsByteArray()))
			.containsExactlyInAnyOrder("02-Situation GPE/kbis.pdf", "01-Pieces principales/DPGF rempli.pdf");
	}

	@Test
	void proposesThePowersTheFileNamesForTheTechnicalChoices() {
		Long tender = tender();
		analysis(tender, "attention_points", "Puissance indiquée de 179,4 kWc sur le plan réseaux, contre environ 162,84 kWc au CCTP",
				null, null);

		// Both, with their sentence: the file contradicts itself, a user decides.
		assertThat(mvc.get().uri("/api/tenders/{id}/technical-choices", tender).with(TestUsers.user())).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{"hasFile": true, "saved": false, "powers": [{"kwc": 179.4}, {"kwc": 162.84}]}""");
	}

	@Test
	void theAnalysisOfTheFileProposesTheTechnicalChoicesWhereNothingIsWritten() {
		Long tender = tender();
		analysis(tender, "tech_valorisation", "autoconsommation avec revente du surplus", null, null);
		analysis(tender, "tech_roof", "Bac acier Nervesco 3-45-1000, deux pans", null, null);
		analysis(tender, "tech_power", "162,84 kWc", null, null);
		analysis(tender, "tech_power", "179 kWc", null, null);
		analysis(tender, "tech_requirements", "Modules : puissance unitaire minimale 460 Wc", null, null);
		analysis(tender, "tech_quantities", "Câble AC coffret – TGBT : 5 ml", null, null);
		analysis(tender, "tech_planes", "Pan Sud : 210 modules, orientation sud, inclinaison 10°", null, null);
		analysis(tender, "tech_yield", "1352 kWh/kWc/an", null, null);
		// Written by a user before: kept.
		jdbc.update("INSERT INTO technical_choice (tender_id, roof, updated_by, updated_at) VALUES (?, 'Ma toiture', 'Paul', now())",
				tender);

		assertThat(mvc.post().uri("/api/tenders/{id}/technical-choices/prefill", tender).with(TestUsers.user()).with(csrf()))
			.hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{"added": 6, "view": {"choices": {"valorisation": "SELF_SURPLUS", "roof": "Ma toiture",
					  "requestedKwc": null, "yieldKwhKwc": 1352},
					 "requirements": [{"text": "Modules : puissance unitaire minimale 460 Wc", "status": "TO_CHECK", "origin": "AI"}],
					 "questions": [{"text": "Puissance de la centrale : 162,84 kWc ou 179 kWc ?", "status": "TO_ASK"}],
					 "quantities": [{"text": "Câble AC coffret – TGBT", "value": 5, "unit": "ml"}],
					 "planes": [{"text": "Pan Sud", "moduleCount": 210, "orientation": "sud", "tilt": 10}]}}""");
		assertThat(mvc.get().uri("/api/tenders/{id}/technical-choices", tender).with(TestUsers.user())).bodyJson()
			.extractingPath("$.aiSources.valorisation")
			.isNotNull();
		// Again: nothing twice.
		assertThat(mvc.post().uri("/api/tenders/{id}/technical-choices/prefill", tender).with(TestUsers.user()).with(csrf()))
			.bodyJson()
			.extractingPath("$.added")
			.isEqualTo(0);
		// A value changed by a user loses its AI source.
		assertThat(mvc.put()
			.uri("/api/tenders/{id}/technical-choices", tender)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"valorisation\": \"SELF_TOTAL\", \"roof\": \"Ma toiture\", \"yieldKwhKwc\": 1352}")
			.with(TestUsers.user())
			.with(csrf())).bodyJson()
			.extractingPath("$.aiSources")
			.asMap()
			.containsOnlyKeys("yieldKwhKwc");
	}

	@Test
	void filesEachPieceInTheFolderOfGpe() {
		var application = ResponsePiece.Category.APPLICATION;
		var offer = ResponsePiece.Category.OFFER;
		assertThat(PieceMatcher.section(application, "Formulaire DC2")).isEqualTo("01-Formulaires");
		assertThat(PieceMatcher.section(application, "Extrait Kbis")).isEqualTo("02-Situation GPE");
		assertThat(PieceMatcher.section(application, "Déclaration sur l'honneur")).isEqualTo("02-Situation GPE");
		assertThat(PieceMatcher.section(application, "Attestations d'assurance RC et décennale")).isEqualTo("03-Assurance et RIB");
		assertThat(PieceMatcher.section(offer, "RIB")).isEqualTo("03-Assurance et RIB");
		assertThat(PieceMatcher.section(application, "Liste des travaux des 5 dernières années")).isEqualTo("04-Références");
		assertThat(PieceMatcher.section(application, "Références de travaux similaires")).isEqualTo("04-Références");
		assertThat(PieceMatcher.section(application, "Certificat de qualification RGE")).isEqualTo("05-Qualification");
		assertThat(PieceMatcher.section(offer, "Acte d'engagement")).isEqualTo("01-Pieces principales");
		assertThat(PieceMatcher.section(offer, "DPGF datée et tamponnée")).isEqualTo("01-Pieces principales");
		assertThat(PieceMatcher.section(offer, "Mémoire justificatif")).isEqualTo("01-Pieces principales");
		assertThat(PieceMatcher.section(offer, "Planning des travaux optimisé")).isEqualTo("01-Pieces principales");
		assertThat(PieceMatcher.section(offer, "Documentation technique du matériel et certifications")).isEqualTo("06-Fiches techniques");
		assertThat(PieceMatcher.section(offer, "Attestation de garantie modules et onduleurs")).isEqualTo("06-Fiches techniques");
		assertThat(PieceMatcher.section(offer, "Plan de calepinage sommaire")).isEqualTo("07-Plans");
		assertThat(PieceMatcher.section(offer, "Schéma électrique de principe")).isEqualTo("07-Plans");
		assertThat(PieceMatcher.section(offer, "Contribution au bilan carbone")).isEqualTo("01-Pieces principales");
		// Neither the accounts proposed for a carbon footprint, nor « contribution » taken for a RIB.
		assertThat(PieceMatcher.category("Annexe bilan carbone simplifié")).isNull();
		assertThat(PieceMatcher.category("Bilan des trois derniers exercices")).isEqualTo(CompanyDocument.Category.FINANCIAL);
	}

	@Test
	void refusesToDeleteAPieceAskedByTheBuyerAndAnEmptyArchive() {
		Long tender = tender();
		analysis(tender, "application_documents", "DC1", null, null);
		PieceResponse piece = service.prepare(tender, "Marie").pieces().getFirst();

		assertThat(mvc.delete().uri("/api/response-pieces/{id}", piece.id()).with(TestUsers.user()).with(csrf()))
			.hasStatus(HttpStatus.CONFLICT);
		assertThat(mvc.get().uri("/api/tenders/{id}/response/archive", tender).with(TestUsers.user()))
			.hasStatus(HttpStatus.CONFLICT);
	}

	@Test
	void withoutAnalysisThePreparationIsRefusedButAPieceCanBeAdded() {
		Long tender = tender();

		assertThat(mvc.post()
			.uri("/api/tenders/{id}/response/preparation", tender)
			.with(TestUsers.user())
			.with(csrf())).hasStatus(HttpStatus.CONFLICT);
		assertThat(service.add(tender, ResponsePiece.Category.OFFER, "Planning", "Marie").pieces()).hasSize(1);
		assertThat(service.list()).singleElement().satisfies(summary -> assertThat(summary.total()).isEqualTo(1));
	}

	@Test
	void recognisesWhatAPieceIs() {
		assertThat(PieceMatcher.kind("Formulaire DC1")).isEqualTo(PieceMatcher.Kind.FORM);
		assertThat(PieceMatcher.kind("Acte d'engagement (AE)")).isEqualTo(PieceMatcher.Kind.ENGAGEMENT);
		assertThat(PieceMatcher.kind("Décomposition du prix global")).isEqualTo(PieceMatcher.Kind.PRICES);
		assertThat(PieceMatcher.kind("Mémoire technique")).isEqualTo(PieceMatcher.Kind.MEMOIR);
		assertThat(PieceMatcher.category("Attestation de vigilance URSSAF").name()).isEqualTo("VIGILANCE");
		assertThat(PieceMatcher.category("Assurance décennale").name()).isEqualTo("INSURANCE");
		assertThat(PieceMatcher.category("Charte de sécurité").name()).isEqualTo("SAFETY");
		assertThat(PieceMatcher.category("Déclaration des moyens humains").name()).isEqualTo("STAFF");
		assertThat(PieceMatcher.category("Liste du matériel").name()).isEqualTo("EQUIPMENT");
		assertThat(PieceMatcher.category("Références de travaux similaires").name()).isEqualTo("REFERENCES");
		assertThat(PieceMatcher.category("PPSPS")).isNull();
		assertThat(PieceMatcher.category("Planning")).isNull();
		assertThat(PieceMatcher.category("Mémoire sur l'assurance qualité")).isNull();
	}

	private static PieceResponse piece(List<PieceResponse> pieces, String label) {
		return pieces.stream().filter(piece -> piece.label().equals(label)).findFirst().orElseThrow();
	}

	private static List<String> names(byte[] zip) throws IOException {
		List<String> names = new ArrayList<>();
		try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
			for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
				names.add(entry.getName());
			}
		}
		return names;
	}

	private Long tender() {
		return tenders.save(Tender.manual(new TenderDetails("Toiture", null, null, null, null, List.of(), List.of(), null,
				List.of(), null, null, null, null, null, List.of()), "Marie", Instant.now())).getId();
	}

	private void analysis(Long tender, String key, String value, FieldDecision decision, String corrected) {
		Dossier dossier = dossiers.save(new Dossier(tender, dossiers.findLastVersion(tender) + 1, "Marie", Instant.now()));
		Analysis analysis = new Analysis(AnalysisKind.DCE_SUMMARY, tender, dossier.getId(), "test", "m", "v", List.of(),
				"f" + dossier.getId(), "Marie", Instant.now());
		ExtractedField field = analysis.addField(key, value, null, null, null, FieldState.VERIFIED);
		analysis.finish(1, 1, Instant.now());
		analyses.save(analysis);
		if (decision != null) {
			jdbc.update("UPDATE extracted_field SET decision = ?, corrected_value = ? WHERE id = ?", decision.name(), corrected,
					field.getId());
		}
		// The fields of every analysis of the test belong to the latest one read.
		jdbc.update("UPDATE extracted_field SET analysis_id = (SELECT max(id) FROM analysis)");
	}

	private Long document(String category, String name, LocalDate expiresOn, String fileName) throws IOException {
		Long id = jdbc.queryForObject("""
				INSERT INTO company_document (category, name, expires_on, updated_at)
				VALUES (?, ?, ?, now()) RETURNING id""", Long.class, category, name,
				expiresOn == null ? null : java.sql.Date.valueOf(expiresOn));
		Path file = storage.store(new ByteArrayInputStream(TestFiles.pdf(name)), storage.resolve("company/document/" + id),
				fileName);
		jdbc.update("""
				INSERT INTO company_file (owner_type, owner_id, file_name, storage_path, size_bytes, uploaded_at)
				VALUES ('DOCUMENT', ?, ?, ?, ?, now())""", id, fileName, storage.relativize(file), Files.size(file));
		return id;
	}

}
