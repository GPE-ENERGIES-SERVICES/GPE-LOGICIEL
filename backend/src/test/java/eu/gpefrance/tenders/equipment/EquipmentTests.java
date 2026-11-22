package eu.gpefrance.tenders.equipment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import eu.gpefrance.tenders.TestUsers;
import eu.gpefrance.tenders.TestcontainersConfiguration;
import eu.gpefrance.tenders.document.TestFiles;
import eu.gpefrance.tenders.tender.Tender;
import eu.gpefrance.tenders.tender.TenderDetails;
import eu.gpefrance.tenders.tender.TenderRepository;

/**
 * The technical sheets kept once, what they are guessed from their names, the
 * equipment chosen for a tender: its table and its sheets in the offer.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EquipmentTests {

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private TenderRepository tenders;

	// Made once: a PDF made again differs by its date of creation.
	private static final byte[] INVERTER_SHEET = TestFiles.pdf("Onduleur SUN2000");

	private Long tenderId;

	@BeforeEach
	void cleanDatabase() {
		jdbc.execute("TRUNCATE equipment_sheet, technical_line, technical_choice, response_piece, tender RESTART IDENTITY CASCADE");
		tenderId = tenders.save(Tender.manual(new TenderDetails("Centrale PV Eyragues", null, null, null, null, List.of(),
				List.of(), null, List.of(), null, null, null, null, null, List.of()), "Marie", Instant.now())).getId();
	}

	@Test
	void guessesWhatASheetIsFromItsFileName() {
		assertThat(EquipmentService.guess("DMEGC DM460G12RT-B48HBT – 460 Wc.pdf"))
			.isEqualTo(new EquipmentService.Guess("MODULE", "DMEGC", "DMEGC DM460G12RT-B48HBT – 460 Wc"));
		assertThat(EquipmentService.guess("HWE-SUN2000-50KTL-M3.pdf").category()).isEqualTo("INVERTER");
		assertThat(EquipmentService.guess("HWE-SUN2000-50KTL-M3.pdf").brand()).isEqualTo("Huawei");
		assertThat(EquipmentService.guess("FT Logiciel FusionSolar.pdf"))
			.isEqualTo(new EquipmentService.Guess("MONITORING", "Huawei", "Logiciel FusionSolar"));
		assertThat(EquipmentService.guess("SOPRASOLAR FIX EVO TILT.pdf").category()).isEqualTo("MOUNTING");
		assertThat(EquipmentService.guess("dome-solar-helios-b2-brochure.pdf").brand()).isEqualTo("Dome Solar");
		assertThat(EquipmentService.guess("K2 DOME 6.pdf").brand()).isEqualTo("K2 Systems");
		assertThat(EquipmentService.guess("Cables DC H1Z2Z2-K.pdf").category()).isEqualTo("CABLE_DC");
		assertThat(EquipmentService.guess("Cables U1000R2V.pdf").category()).isEqualTo("CABLE_AC");
		assertThat(EquipmentService.guess("STAUBLI CONNECTEUR MC4.pdf").category()).isEqualTo("CONNECTOR");
		assertThat(EquipmentService.guess("Coffret AC 100kW.pdf").category()).isEqualTo("ELECTRICAL_BOX");
		assertThat(EquipmentService.guess("Dispositifs d'arrêt d'urgence bris de glace.pdf").category()).isEqualTo("SAFETY");
		assertThat(EquipmentService.guess("Annexe 3 – FT- SUN2000-115KTL-M2.pdf").reference()).isEqualTo("SUN2000-115KTL-M2");
		assertThat(EquipmentService.guess("HWE3152972_FICHE_1.pdf"))
			.isEqualTo(new EquipmentService.Guess("INVERTER", "Huawei", "HWE3152972 FICHE 1"));
		assertThat(EquipmentService.guess("Notice de montage.pdf").category()).isEqualTo("OTHER");
	}

	@Test
	void keepsASheetOnceAndLetsAUserCorrectIt() {
		assertThat(upload("SUN2000-50KTL-M3.pdf", TestUsers.user())).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(upload("SUN2000-50KTL-M3.pdf", TestUsers.admin())).hasStatus(HttpStatus.CREATED)
			.bodyJson()
			.isLenientlyEqualTo("{\"id\": 1, \"category\": \"INVERTER\", \"brand\": \"Huawei\", \"reference\": \"SUN2000-50KTL-M3\"}");
		// The same file again: the sheet already kept.
		assertThat(upload("copie.pdf", TestUsers.admin())).bodyJson().extractingPath("$.id").isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM equipment_sheet", Long.class)).isEqualTo(1);

		assertThat(mvc.put()
			.uri("/api/equipment-sheets/1")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"category\": \"INVERTER\", \"brand\": \"Huawei\", \"reference\": \"SUN2000-50KTL-M3 (x2)\"}")
			.with(TestUsers.admin())
			.with(csrf())).hasStatusOk().bodyJson().extractingPath("$.reference").isEqualTo("SUN2000-50KTL-M3 (x2)");
		assertThat(mvc.put()
			.uri("/api/equipment-sheets/1")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"category\": \"ROBOT\", \"reference\": \"X\"}")
			.with(TestUsers.admin())
			.with(csrf())).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(mvc.get().uri("/api/equipment-sheets/1/file").with(TestUsers.user())).hasStatusOk();
		assertThat(mvc.post()
			.uri("/api/equipment-sheets")
			.multipart()
			.file(new MockMultipartFile("file", "fiche.txt", "text/plain", "pas un pdf".getBytes()))
			.with(TestUsers.admin())
			.with(csrf())).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void theEquipmentOfATenderMakesItsTableAndGoesWithTheOffer() throws IOException {
		upload("SUN2000-50KTL-M3.pdf", TestUsers.admin());
		mvc.post()
			.uri("/api/equipment-sheets")
			.multipart()
			.file(new MockMultipartFile("file", "DMEGC 465.pdf", "application/pdf", TestFiles.pdf("Module 465 Wc")))
			.with(TestUsers.admin())
			.with(csrf())
			.exchange();

		assertThat(mvc.put()
			.uri("/api/tenders/{id}/equipment", tenderId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"sheetIds\": [1, 2]}")
			.with(TestUsers.user())
			.with(csrf())).hasStatusOk()
			.bodyJson()
			// In the order of the table of GPE: the modules first.
			.isLenientlyEqualTo("[{\"category\": \"MODULE\"}, {\"category\": \"INVERTER\"}]");

		var table = mvc.get().uri("/api/tenders/{id}/equipment/table", tenderId).with(TestUsers.user()).exchange();
		try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(table.getResponse().getContentAsByteArray()))) {
			var sheet = workbook.getSheetAt(0);
			assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("Matériel");
			assertThat(sheet.getRow(5).getCell(0).getStringCellValue()).isEqualTo("Panneaux photovoltaïques");
			assertThat(sheet.getRow(5).getCell(1).getStringCellValue()).isEqualTo("DMEGC");
			assertThat(sheet.getRow(6).getCell(2).getStringCellValue()).isEqualTo("SUN2000-50KTL-M3");
		}

		// The sheets go in the offer, in the folder of GPE, beside the pieces.
		assertThat(mvc.post()
			.uri("/api/tenders/{id}/response/pieces", tenderId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"category\": \"OFFER\", \"label\": \"DPGF\"}")
			.with(TestUsers.user())
			.with(csrf())).hasStatus2xxSuccessful();
		var archive = mvc.get().uri("/api/tenders/{id}/response/archive", tenderId).with(TestUsers.user()).exchange();
		assertThat(names(archive.getResponse().getContentAsByteArray())).containsExactlyInAnyOrder(
				"2-Offre/06-Fiches techniques/DMEGC 465.pdf", "2-Offre/06-Fiches techniques/SUN2000-50KTL-M3.pdf");

		// A sheet deleted leaves the tender.
		assertThat(mvc.delete().uri("/api/equipment-sheets/2").with(TestUsers.admin()).with(csrf()))
			.hasStatus(HttpStatus.NO_CONTENT);
		assertThat(mvc.get().uri("/api/tenders/{id}/equipment", tenderId).with(TestUsers.user())).bodyJson()
			.extractingPath("$.length()")
			.isEqualTo(1);
	}

	@Test
	void technicalChoicesAreEnteredByHandWithoutFileAndSizedFromTheEquipment() {
		// Nothing entered, no consultation file: everything to write by hand.
		assertThat(mvc.get().uri("/api/tenders/{id}/technical-choices", tenderId).with(TestUsers.user())).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("{\"saved\": false, \"hasFile\": false, \"analysisDone\": false, \"powers\": []}");

		sheet("DMEGC DM460G12RT-B48HBT – 460 Wc.pdf");
		sheet("SUN2000-100KTL.pdf");
		sheet("SUN2000-30KTL-M3.pdf");
		mvc.put()
			.uri("/api/tenders/{id}/equipment", tenderId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"sheetIds\": [1, 2, 3]}")
			.with(TestUsers.user())
			.with(csrf())
			.exchange();
		// The powers read in the references; two models of inverter.
		assertThat(mvc.get().uri("/api/tenders/{id}/technical-choices", tenderId).with(TestUsers.user())).bodyJson()
			.isLenientlyEqualTo("""
					{"equipment": [{"sheet": {"id": 1}, "unitPower": 460, "powerGuessed": true},
					               {"sheet": {"id": 2}, "unitPower": 100}, {"sheet": {"id": 3}, "unitPower": 30}],
					 "sizing": {"installedKwc": null}}""");
		detail(1, "{\"quantity\": 390}");
		detail(2, "{\"quantity\": 1}");
		detail(3, "{\"quantity\": 1, \"note\": \"pan Nord\"}");

		assertThat(put("""
				{"valorisation": "SELF_SURPLUS", "roof": "Bac acier", "requestedKwc": 162.84, "safety": "Ligne de vie au faîtage"}"""))
			.hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{"saved": true, "choices": {"valorisation": "SELF_SURPLUS", "safety": "Ligne de vie au faîtage"},
					 "sizing": {"modules": 390, "installedKwc": 179.4, "gapPercent": 10.2, "inverterKva": 130, "dcAcRatio": 1.38},
					 "updatedBy": "Marie Durand"}""");

		// Lines: the roof planes against the modules, a requirement answered, a question.
		line("PLANE", "{\"text\": \"Pan Sud\", \"moduleCount\": 200, \"orientation\": \"Sud\", \"tilt\": 10}");
		assertThat(line("PLANE", "{\"text\": \"Pan Nord\", \"moduleCount\": 180}")).bodyJson()
			.extractingPath("$.sizing.warnings[2]")
			.isEqualTo("Les pans comptent 380 modules, le matériel retenu 390.");
		assertThat(line("REQUIREMENT", "{\"text\": \"Modules : 460 Wc minimum\"}")).bodyJson()
			.isLenientlyEqualTo("{\"requirements\": [{\"status\": \"TO_CHECK\", \"origin\": \"USER\"}]}");
		Long requirement = jdbc.queryForObject("SELECT id FROM technical_line WHERE kind = 'REQUIREMENT'", Long.class);
		assertThat(mvc.put()
			.uri("/api/tenders/{id}/technical-lines/{line}", tenderId, requirement)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"text\": \"Modules : 460 Wc minimum\", \"status\": \"COMPLIANT\", \"answer\": \"DMEGC 460 Wc\"}")
			.with(TestUsers.user())
			.with(csrf())).bodyJson()
			.isLenientlyEqualTo("{\"requirements\": [{\"status\": \"COMPLIANT\", \"answer\": \"DMEGC 460 Wc\"}]}");
		line("QUESTION", "{\"text\": \"Puissance : 162,84 ou 179,4 kWc ?\"}");

		assertThat(mvc.post().uri("/api/tenders/{id}/technical-choices/validation", tenderId).with(TestUsers.user()).with(csrf()))
			.bodyJson()
			.extractingPath("$.validatedBy")
			.isEqualTo("Marie Durand");
		// Any change after: to validate again.
		Long question = jdbc.queryForObject("SELECT id FROM technical_line WHERE kind = 'QUESTION'", Long.class);
		assertThat(mvc.delete()
			.uri("/api/tenders/{id}/technical-lines/{line}", tenderId, question)
			.with(TestUsers.user())
			.with(csrf())).bodyJson()
			.isLenientlyEqualTo("{\"validatedBy\": null, \"questions\": []}");

		assertThat(put("{\"valorisation\": \"VENTE\"}")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(line("ROBOT", "{\"text\": \"x\"}")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(line("QUESTION", "{\"text\": \"x\", \"status\": \"COMPLIANT\"}")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(line("QUESTION", "{\"text\": \" \"}")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(mvc.get().uri("/api/tenders/999/technical-choices").with(TestUsers.user())).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void readsTheUnitPowerOfAModuleOrAnInverterInItsReference() {
		assertThat(EquipmentService.unitPower("MODULE", "DMEGC DM460G12RT-B48HBT – 460 Wc")).isEqualByComparingTo("460");
		assertThat(EquipmentService.unitPower("MODULE", "Tiger Neo : JKM450N-54HL4R")).isEqualByComparingTo("450");
		assertThat(EquipmentService.unitPower("MODULE", "DMEGC 465")).isEqualByComparingTo("465");
		assertThat(EquipmentService.unitPower("MODULE", "Module verre-verre")).isNull();
		assertThat(EquipmentService.unitPower("INVERTER", "Huawei SUN2000-100KTL")).isEqualByComparingTo("100");
		assertThat(EquipmentService.unitPower("INVERTER", "SUN2000-30/36/40KTL-M3")).isEqualByComparingTo("30");
		assertThat(EquipmentService.unitPower("INVERTER", "SUN2000-15K-MB0")).isEqualByComparingTo("15");
		assertThat(EquipmentService.unitPower("CABLE_AC", "U1000R2V 5G16")).isNull();
	}

	private void sheet(String name) {
		mvc.post()
			.uri("/api/equipment-sheets")
			.multipart()
			.file(new MockMultipartFile("file", name, "application/pdf", TestFiles.pdf(name)))
			.with(TestUsers.admin())
			.with(csrf())
			.exchange();
	}

	private void detail(long sheetId, String json) {
		assertThat(mvc.put()
			.uri("/api/tenders/{id}/equipment/{sheet}", tenderId, sheetId)
			.contentType(MediaType.APPLICATION_JSON)
			.content(json)
			.with(TestUsers.user())
			.with(csrf())).hasStatusOk();
	}

	private MvcTestResult line(String kind, String json) {
		return mvc.post()
			.uri("/api/tenders/{id}/technical-lines/{kind}", tenderId, kind)
			.contentType(MediaType.APPLICATION_JSON)
			.content(json)
			.with(TestUsers.user())
			.with(csrf())
			.exchange();
	}

	private MvcTestResult put(String json) {
		return mvc.put()
			.uri("/api/tenders/{id}/technical-choices", tenderId)
			.contentType(MediaType.APPLICATION_JSON)
			.content(json)
			.with(TestUsers.user())
			.with(csrf())
			.exchange();
	}

	private MvcTestResult upload(String name, RequestPostProcessor user) {
		return mvc.post()
			.uri("/api/equipment-sheets")
			.multipart()
			.file(new MockMultipartFile("file", name, "application/pdf", INVERTER_SHEET))
			.with(user)
			.with(csrf())
			.exchange();
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

}
