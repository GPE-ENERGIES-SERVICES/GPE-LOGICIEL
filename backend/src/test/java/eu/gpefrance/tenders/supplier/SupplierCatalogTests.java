package eu.gpefrance.tenders.supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;

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
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import eu.gpefrance.tenders.ClockConfiguration;
import eu.gpefrance.tenders.TestUsers;
import eu.gpefrance.tenders.TestcontainersConfiguration;

/**
 * The price file of a supplier imported, searched, and a price copied in an
 * estimate, which a new import leaves as it is.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SupplierCatalogTests {

	private static final Instant NOW = Instant.parse("2026-10-31T08:00:00Z");

	static final String PRICES = """
			Référence;Réf. fabricant;Désignation;Unité;Conditionnement;Prix tarif;Remise;Prix net
			LEG077121;077121;Prise 2P+T 16A Mosaic blanc;U;1;8,00;60;
			NEX123;R2V3G25;Câble R2V 3G2,5 couronne 100 m;M;100;150,00;;90,00
			SANSPRIX;X;Article sans prix;U;1;;;
			;;Sans référence;U;1;2;;
			""";

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@TestBean
	private Clock clock;

	static Clock clock() {
		return Clock.fixed(NOW, ClockConfiguration.ZONE);
	}

	@BeforeEach
	void cleanDatabase() {
		jdbc.execute("TRUNCATE supplier, estimate, lot, tender RESTART IDENTITY CASCADE");
		jdbc.update("UPDATE pricing_coefficient SET coefficient = 1.0000");
	}

	@Test
	void importsAPriceFileAndCopiesAPriceInAnEstimate() {
		assertThat(post("/api/suppliers", "{\"code\": \"REXEL\", \"name\": \"Rexel\"}", TestUsers.user()))
			.hasStatus(HttpStatus.FORBIDDEN);
		assertThat(post("/api/suppliers", "{\"code\": \"rexel\", \"name\": \"Rexel\"}", TestUsers.admin()))
			.hasStatus(HttpStatus.CREATED)
			.bodyJson()
			.isLenientlyEqualTo("{\"id\": 1, \"code\": \"REXEL\"}");

		assertThat(upload(PRICES, TestUsers.user())).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(upload(PRICES, TestUsers.admin())).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{"imported": 2, "rejected": 2, "columns": {"SUPPLIER_REF": "Référence",
					 "MANUFACTURER_REF": "Réf. fabricant", "PACK": "Conditionnement", "LIST_PRICE": "Prix tarif",
					 "DISCOUNT": "Remise", "NET_PRICE": "Prix net"}}""");

		assertThat(mvc.get().uri("/api/supplier-offers?q=prise").with(TestUsers.user())).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					[{"supplierName": "Rexel", "supplierRef": "LEG077121", "listPrice": 8.0000, "discountRate": 0.6000,
					  "netPrice": 3.2000, "validFrom": "2026-10-01"}]""");
		assertThat(mvc.get().uri("/api/supplier-offers?q=R2V3G").with(TestUsers.user())).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("[{\"id\": 2, \"supplierRef\": \"NEX123\", \"packQuantity\": 100.0000}]");
		assertThat(mvc.get().uri("/api/suppliers").with(TestUsers.user())).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("[{\"code\": \"REXEL\", \"offers\": 2}]");

		long tenderId = jdbc.queryForObject("""
				INSERT INTO tender (source, external_id, title, status, created_at, updated_at)
				VALUES ('BOAMP', '26-1', 'Éclairage', 'ACTIVE', now(), now()) RETURNING id""", Long.class);
		post("/api/tenders/" + tenderId + "/estimates", "{}", TestUsers.user()).exchange();
		post("/api/estimates/1/items", "{\"designation\": \"Câble\", \"unit\": \"m\", \"quantity\": 200, "
				+ "\"priceMode\": \"COMPOSED\"}", TestUsers.user()).exchange();
		assertThat(post("/api/estimate-items/1/components", """
				{"type": "OTHER", "label": "Câble R2V", "quantity": 1.05, "unitCost": 99, "offerId": 2}""",
				TestUsers.user()))
			.hasStatus(HttpStatus.CREATED)
			.bodyJson()
			.isLenientlyEqualTo("""
					{"totalHt": 190.00, "items": [{"components": [{"type": "SUPPLY", "unitCost": 0.9000,
					 "unit": "M", "supplierName": "Rexel", "supplierRef": "NEX123", "manufacturerRef": "R2V3G25",
					 "costDate": "2026-10-01", "offerId": 2}]}]}""");

		// A new price file changes the catalogue, not the estimate.
		upload(PRICES.replace("150,00;;90,00", "150,00;;95,00"), TestUsers.admin());
		assertThat(mvc.get().uri("/api/supplier-offers?q=NEX123").with(TestUsers.user())).bodyJson()
			.isLenientlyEqualTo("[{\"netPrice\": 95.0000}]");
		assertThat(mvc.get().uri("/api/estimates/1").with(TestUsers.user())).bodyJson()
			.isLenientlyEqualTo("{\"totalHt\": 190.00}");
	}

	@Test
	void refusesAFileWithoutRecognisedColumns() {
		post("/api/suppliers", "{\"code\": \"REXEL\", \"name\": \"Rexel\"}", TestUsers.admin()).exchange();

		assertThat(upload("Nom;Ville\nDupont;Lyon\n", TestUsers.admin())).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(mvc.post().uri("/api/suppliers/1/offers").multipart().file(new MockMultipartFile("file", "prix.pdf",
				"application/pdf", new byte[] { 1 })).with(csrf()).with(TestUsers.admin()))
			.hasStatus(HttpStatus.BAD_REQUEST);
	}

	private MvcTestResult upload(String csv, RequestPostProcessor user) {
		return mvc.post()
			.uri("/api/suppliers/1/offers?validFrom=2026-10-01")
			.multipart()
			.file(new MockMultipartFile("file", "tarif.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
			.with(csrf())
			.with(user)
			.exchange();
	}

	private MockMvcTester.MockMvcRequestBuilder post(String uri, String body, RequestPostProcessor user) {
		return mvc.post().uri(uri).with(csrf()).with(user).contentType(MediaType.APPLICATION_JSON).content(body);
	}

}
