package eu.gpefrance.tenders.estimate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
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
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import eu.gpefrance.tenders.ClockConfiguration;
import eu.gpefrance.tenders.TestUsers;
import eu.gpefrance.tenders.TestcontainersConfiguration;
import eu.gpefrance.tenders.document.TestSpreadsheets;

/**
 * An estimate from its creation with the settings of GPE to its validation:
 * rates copied, price schedule imported, validation refused while something
 * is missing, new version, export.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EstimateWorkflowTests {

	private static final Instant NOW = Instant.parse("2026-10-31T08:00:00Z");

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@TestBean
	private Clock clock;

	static Clock clock() {
		return Clock.fixed(NOW, ClockConfiguration.ZONE);
	}

	private long tenderId;

	@BeforeEach
	void cleanDatabase() {
		jdbc.execute("TRUNCATE estimate, pricing_rate, document_page, document, dossier, lot, tender "
				+ "RESTART IDENTITY CASCADE");
		jdbc.update("UPDATE pricing_settings SET vat_rate = 0.2000, agency_fee_rate = 0.05, overhead_rate = 0.05, "
				+ "service_fee_rate = 0.05");
		jdbc.update("UPDATE pricing_coefficient SET coefficient = 1.0000");
		tenderId = jdbc.queryForObject("""
				INSERT INTO tender (source, external_id, title, status, created_at, updated_at)
				VALUES ('BOAMP', '26-1', 'Rénovation électrique école', 'ACTIVE', now(), now()) RETURNING id""",
				Long.class);
	}

	@Test
	void theSettingsOfGpeAreChangedByAnAdministratorOnly() {
		assertThat(put("/api/pricing-settings", "{\"vatRate\": 0.10}", TestUsers.user())).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(post("/api/pricing-rates", rate("ELEC", "45"), TestUsers.user())).hasStatus(HttpStatus.FORBIDDEN);

		assertThat(put("/api/pricing-settings",
				"{\"vatRate\": 0.10, \"coefficients\": {\"LABOR\": 1.45}, \"overheadRate\": 0.07}", TestUsers.admin()))
			.hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("{\"vatRate\": 0.10, \"coefficients\": {\"LABOR\": 1.45, \"SUPPLY\": 1.0000}, "
					+ "\"agencyFeeRate\": 0.0500, \"overheadRate\": 0.07, \"updatedBy\": \"Admin GPE\"}");
		assertThat(put("/api/pricing-settings", "{\"serviceFeeRate\": 1}", TestUsers.admin()))
			.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(post("/api/pricing-rates", rate("elec", "45"), TestUsers.admin())).hasStatus(HttpStatus.CREATED)
			.bodyJson()
			.isLenientlyEqualTo("{\"id\": 1, \"code\": \"ELEC\", \"cost\": 45}");
		assertThat(post("/api/pricing-rates", rate("ELEC", "50"), TestUsers.admin())).hasStatus(HttpStatus.CONFLICT);
		assertThat(mvc.get().uri("/api/pricing-rates").with(TestUsers.user())).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("[{\"code\": \"ELEC\", \"type\": \"LABOR\", \"unit\": \"h\"}]");

		// A new estimate takes the settings of GPE.
		assertThat(post("/api/tenders/" + tenderId + "/estimates", "{}", TestUsers.user())).hasStatus(HttpStatus.CREATED)
			.bodyJson()
			.isLenientlyEqualTo("{\"vatRate\": 0.1000, \"coefficients\": {\"LABOR\": 1.4500, \"SUPPLY\": 1.0000}}");
	}

	@Test
	void aRateIsCopiedOnceAndOnlyCopiedAgainOnRequest() {
		post("/api/pricing-rates", rate("ELEC", "45"), TestUsers.admin()).exchange();
		send("/api/tenders/" + tenderId + "/estimates", "{}");
		send("/api/estimates/1/items", "{\"designation\": \"Prise\", \"unit\": \"u\", \"quantity\": 10, "
				+ "\"priceMode\": \"COMPOSED\"}");
		assertThat(post("/api/estimate-items/1/components", """
				{"type": "OTHER", "label": "Électricien", "quantity": 0.5, "unitCost": 1, "rateCode": "elec"}""",
				TestUsers.user()))
			.hasStatus(HttpStatus.CREATED)
			.bodyJson()
			.isLenientlyEqualTo("""
					{"totalHt": 225.00, "items": [{"components": [{"type": "LABOR", "unit": "h", "unitCost": 45.0000,
					 "rateCode": "ELEC"}]}]}""");

		put("/api/pricing-rates/1", rate("ELEC", "48"), TestUsers.admin()).exchange();
		assertThat(mvc.get().uri("/api/estimates/1").with(TestUsers.user())).bodyJson()
			.isLenientlyEqualTo("{\"totalHt\": 225.00}");

		assertThat(post("/api/estimates/1/rates-copy", "", TestUsers.user())).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("{\"totalHt\": 240.00}");
		assertThat(mvc.get().uri("/api/estimates/1/events").with(TestUsers.user())).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					[{"kind": "RATES_COPIED", "detail": "1 coût(s) recopié(s) depuis les taux actuels"},
					 {"kind": "CREATED", "createdBy": "Marie Durand"}]""");
		assertThat(post("/api/estimate-items/1/components", """
				{"type": "LABOR", "label": "Chef", "quantity": 1, "rateCode": "CHEF"}""", TestUsers.user()))
			.hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void validatesOnceNothingIsMissingThenChangesThroughANewVersion() {
		send("/api/tenders/" + tenderId + "/estimates", "{}");
		assertThat(post("/api/estimates/1/validation", "", TestUsers.user())).hasStatus(HttpStatus.BAD_REQUEST);
		send("/api/estimates/1/items", "{\"designation\": \"Prise\", \"unit\": \"u\", \"quantity\": 10, "
				+ "\"priceMode\": \"COMPOSED\"}");
		assertThat(post("/api/estimates/1/validation", "", TestUsers.user())).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.detail")
			.asString()
			.contains("1 poste(s) sans montant");
		send("/api/estimate-items/1/components", """
				{"type": "SUPPLY", "label": "Prise", "quantity": 1, "unitCost": 3.20}""");

		assertThat(post("/api/estimates/1/validation", "", TestUsers.user())).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("{\"status\": \"VALIDATED\", \"validatedBy\": \"Marie Durand\", \"totalHt\": 32.00}");
		assertThat(post("/api/estimates/1/items", "{\"designation\": \"x\", \"priceMode\": \"COMPOSED\"}",
				TestUsers.user())).hasStatus(HttpStatus.CONFLICT);

		assertThat(post("/api/estimates/1/versions", "", TestUsers.user())).hasStatus(HttpStatus.CREATED)
			.bodyJson()
			.isLenientlyEqualTo("""
					{"id": 2, "version": 2, "status": "DRAFT", "totalHt": 32.00, "validatedBy": null,
					 "items": [{"designation": "Prise", "components": [{"unitCost": 3.2000}]}]}""");
		assertThat(post("/api/estimates/1/versions", "", TestUsers.user())).hasStatus(HttpStatus.CONFLICT);
		put("/api/estimate-components/2", """
				{"type": "SUPPLY", "label": "Prise", "quantity": 1, "unitCost": 3.50}""", TestUsers.user()).exchange();
		post("/api/estimates/2/validation", "", TestUsers.user()).exchange();

		assertThat(mvc.get().uri("/api/tenders/" + tenderId + "/estimates").with(TestUsers.user())).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					[{"id": 2, "status": "VALIDATED", "totalHt": 35.00},
					 {"id": 1, "status": "SUPERSEDED", "totalHt": 32.00}]""");
		assertThat(mvc.get().uri("/api/estimates/2/events").with(TestUsers.user())).bodyJson()
			.isLenientlyEqualTo("[{\"kind\": \"VALIDATED\"}, {\"kind\": \"NEW_VERSION\", \"detail\": \"Copie de la version 1\"}]");
	}

	@Test
	void importsTheLinesOfWorkOfAPriceSchedule() throws IOException {
		Path stored = Path.of("target/test-storage/estimate-tests/dpgf.xlsx");
		Files.createDirectories(stored.getParent());
		Files.write(stored, TestSpreadsheets.xlsx());
		jdbc.update("INSERT INTO dossier (tender_id, version, status, uploaded_at) VALUES (?, 1, 'DONE', now())",
				tenderId);
		jdbc.update("""
				INSERT INTO document (dossier_id, file_name, storage_path, size_bytes, sha256, piece_type, state)
				VALUES (1, 'DPGF.xlsx', 'estimate-tests/dpgf.xlsx', 1, 'x', 'DPGF', 'READ')""");
		send("/api/tenders/" + tenderId + "/estimates", "{}");

		assertThat(post("/api/estimates/1/imports", "{\"documentId\": 1}", TestUsers.user())).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{"incompleteItems": 5, "itemsToCheck": 0, "items": [
					 {"position": 1, "reference": "1.1", "designation": "Décapage de terre végétale", "unit": "m2",
					  "quantity": 1200.000, "priceMode": "FIXED", "sourceDocumentId": 1, "sourceSheet": "DPGF Lot 1",
					  "sourceRow": 4, "unitPrice": null},
					 {"reference": "1.2", "quantity": 350.000},
					 {"reference": "1.3", "designation": "Évacuation des déblais"},
					 {"designation": "Révision", "quantity": null},
					 {"reference": "A01", "designation": "Enrobé BBSG 0/10 ép. 6 cm", "unit": "t", "quantity": null,
					  "sourceSheet": "BPU"},
					 {"reference": "A02", "priceMode": "TEXT", "textPrice": "PM", "amount": 0.00}]}""");
		assertThat(mvc.get().uri("/api/estimates/1/events").with(TestUsers.user())).bodyJson()
			.isLenientlyEqualTo("[{\"kind\": \"IMPORTED\", \"detail\": \"6 poste(s) importé(s) de « DPGF.xlsx »\"}, "
					+ "{\"kind\": \"CREATED\"}]");

		long other = jdbc.queryForObject("""
				INSERT INTO tender (source, external_id, title, status, created_at, updated_at)
				VALUES ('BOAMP', '26-2', 'Autre', 'ACTIVE', now(), now()) RETURNING id""", Long.class);
		send("/api/tenders/" + other + "/estimates", "{}");
		assertThat(post("/api/estimates/2/imports", "{\"documentId\": 1}", TestUsers.user()))
			.hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void exportsTheFiguresOfTheApplication() throws IOException {
		send("/api/tenders/" + tenderId + "/estimates", "{\"label\": \"Offre de base\"}");
		send("/api/estimates/1/items", "{\"reference\": \"2.1\", \"designation\": \"Prise\", \"unit\": \"u\", "
				+ "\"quantity\": 10, \"priceMode\": \"COMPOSED\"}");
		send("/api/estimate-items/1/components", """
				{"type": "SUPPLY", "label": "Prise Legrand", "quantity": 1, "unitCost": 3.20, "coefficient": 1.25,
				"supplierRef": "LEG1"}""");
		send("/api/estimate-items/1/components", """
				{"type": "LABOR", "label": "Pose", "quantity": 0.5, "unit": "h", "unitCost": 40, "coefficient": 1.5}""");

		byte[] file = mvc.get().uri("/api/estimates/1/export").with(TestUsers.user()).exchange().getResponse()
			.getContentAsByteArray();

		// Unit: supply 3.20 sold 4.00, labour 0.5 h x 40 = 20 sold 30: price 34, x 10 = 340.
		// Fees 3 x 5 % of 34 = 5.10: net margin 34 - 23.20 - 5.10 = 5.70 per unit, 57 in all.
		try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
			Sheet prices = workbook.getSheet("Calcul P.V.");
			assertThat(prices.getRow(0).getCell(1).getStringCellValue()).isEqualTo("Rénovation électrique école");
			Row prise = prices.getRow(5);
			assertThat(prise.getCell(1).getStringCellValue()).isEqualTo("Prise");
			assertThat(prise.getCell(4).getNumericCellValue()).isEqualTo(34.0);
			assertThat(prise.getCell(5).getNumericCellValue()).isEqualTo(340.0);
			assertThat(prise.getCell(7).getNumericCellValue()).isEqualTo(3.2);
			assertThat(prise.getCell(8).getNumericCellValue()).isEqualTo(0.25);
			assertThat(prise.getCell(10).getNumericCellValue()).isEqualTo(32.0);
			assertThat(prise.getCell(15).getNumericCellValue()).isEqualTo(40.0);
			assertThat(prise.getCell(16).getNumericCellValue()).isEqualTo(0.5);
			assertThat(prise.getCell(19).getNumericCellValue()).isEqualTo(30.0);
			assertThat(prise.getCell(20).getNumericCellValue()).isEqualTo(5.0);
			assertThat(prise.getCell(21).getNumericCellValue()).isEqualTo(200.0);
			assertThat(prise.getCell(23).getNumericCellValue()).isEqualTo(1.7);
			assertThat(prise.getCell(26).getNumericCellValue()).isEqualTo(5.7);
			assertThat(prices.getRow(7).getCell(4).getStringCellValue()).isEqualTo("Total HT");
			assertThat(prices.getRow(9).getCell(5).getNumericCellValue()).isEqualTo(408.0);

			Sheet budget = workbook.getSheet("Fiche budget");
			assertThat(budget.getRow(5).getCell(1).getNumericCellValue()).isEqualTo(340.0);
			assertThat(budget.getRow(12).getCell(1).getNumericCellValue()).isEqualTo(308.0);
			assertThat(budget.getRow(18).getCell(1).getNumericCellValue()).isEqualTo(108.0);
			assertThat(budget.getRow(24).getCell(0).getStringCellValue()).isEqualTo("MARGE NETTE (B)");
			assertThat(budget.getRow(24).getCell(1).getNumericCellValue()).isEqualTo(57.0);
			assertThat(workbook.getSheet("Sous-détail").getRow(1).getCell(10).getStringCellValue()).isEqualTo("LEG1");
		}
	}

	/** The AI is off by default: nothing is sent anywhere. */
	@Test
	void theAssistanceIsOffUntilTheAiIsEnabled() {
		send("/api/tenders/" + tenderId + "/estimates", "{}");
		send("/api/estimates/1/items", "{\"designation\": \"Prise\", \"priceMode\": \"COMPOSED\"}");

		assertThat(mvc.get().uri("/api/estimate-assistant").with(TestUsers.user())).bodyJson()
			.isLenientlyEqualTo("{\"available\": false}");
		assertThat(post("/api/estimates/1/review", "", TestUsers.user())).hasStatus(HttpStatus.CONFLICT);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_call", Long.class)).isZero();
	}

	private static String rate(String code, String cost) {
		return "{\"code\": \"%s\", \"label\": \"Électricien\", \"type\": \"LABOR\", \"unit\": \"h\", \"cost\": %s}"
			.formatted(code, cost);
	}

	private void send(String uri, String body) {
		post(uri, body, TestUsers.user()).exchange();
	}

	private MockMvcTester.MockMvcRequestBuilder post(String uri, String body, RequestPostProcessor user) {
		return mvc.post().uri(uri).with(csrf()).with(user).contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private MockMvcTester.MockMvcRequestBuilder put(String uri, String body, RequestPostProcessor user) {
		return mvc.put().uri(uri).with(csrf()).with(user).contentType(MediaType.APPLICATION_JSON).content(body);
	}

}
