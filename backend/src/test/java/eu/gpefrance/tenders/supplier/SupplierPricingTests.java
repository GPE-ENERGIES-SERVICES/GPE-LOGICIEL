package eu.gpefrance.tenders.supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import eu.gpefrance.tenders.ClockConfiguration;
import eu.gpefrance.tenders.TestUsers;
import eu.gpefrance.tenders.TestcontainersConfiguration;

/**
 * Prices read from the service of a supplier, simulated: the offer and its
 * history, freshness, a failure changing nothing, the estimates left as they are.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, SupplierPricingTests.FakeConnectorConfiguration.class })
class SupplierPricingTests {

	private static final Instant NOW = Instant.parse("2026-11-04T08:00:00Z");

	private static final String FILE = """
			Référence;Trigramme;Réf. fabricant;Désignation;Conditionnement;Prix net
			ACM1;acm;A-1;Disjoncteur 16A;1;10,00
			ACM2;;A-2;Sans trigramme;1;20,00
			""";

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private FakeConnector connector;

	@Autowired
	private SupplierPricingService pricing;

	@TestBean
	private Clock clock;

	static Clock clock() {
		return Clock.fixed(NOW, ClockConfiguration.ZONE);
	}

	@BeforeEach
	void setUp() {
		jdbc.execute("TRUNCATE supplier, estimate, lot, tender RESTART IDENTITY CASCADE");
		jdbc.update("UPDATE pricing_coefficient SET coefficient = 1.0000");
		connector.reset();
		jdbc.update("INSERT INTO supplier (code, name, created_at) VALUES ('ACME', 'Acme', now())");
		assertThat(mvc.post().uri("/api/suppliers/1/offers?validFrom=2026-01-01").multipart()
			.file(new MockMultipartFile("file", "tarif.csv", "text/csv", FILE.getBytes(StandardCharsets.UTF_8)))
			.with(csrf()).with(TestUsers.admin())).hasStatusOk();
	}

	@Test
	void listsThePricesOfASupplierForAnAdministrator() {
		assertThat(mvc.get().uri("/api/suppliers/1/offers").with(TestUsers.user())).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(mvc.get().uri("/api/suppliers/1/offers").with(TestUsers.admin())).hasStatusOk().bodyJson()
			.isLenientlyEqualTo("""
					[{"designation": "Disjoncteur 16A", "netPrice": 10.0000}, {"designation": "Sans trigramme", "netPrice": 20.0000}]""");
	}

	@Test
	void importReadsTheManufacturerCodeAndStartsTheHistory() {
		assertThat(jdbc.queryForObject("SELECT manufacturer_code FROM supplier_offer WHERE id = 1", String.class))
			.isEqualTo("ACM");
		assertThat(historyCount(1)).isEqualTo(1);
	}

	@Test
	void aPriceReadUpdatesTheOfferAndItsHistoryOnlyWhenItChanges() {
		connector.price("A-1", "12.00", "Remise chantier");

		assertThat(refresh(1, 1)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				[{"offerId": 1, "status": "UPDATED", "priceLabel": "Prix unitaire € HT",
				  "offer": {"netPrice": 12.0000, "manufacturerCode": "ACM",
				  "priceSource": "FAKE", "validFrom": null}}]""");
		assertThat(historyCount(1)).isEqualTo(2);
		assertThat(mvc.get().uri("/api/supplier-offers/1/prices").with(TestUsers.user())).bodyJson()
			.isLenientlyEqualTo("""
					[{"netPrice": 12.0000, "conditionLabel": "Remise chantier", "source": "FAKE", "orderingQuantity": 1},
					 {"netPrice": 10.0000, "source": "IMPORT"}]""");

		// The same price asked for another quantity: read again, nothing new to keep.
		refresh(1, 2);
		assertThat(connector.priceCalls).isEqualTo(2);
		assertThat(historyCount(1)).isEqualTo(2);
	}

	@Test
	void aPriceReadRecentlyIsNotAskedAgainUntilItIsOld() {
		connector.price("A-1", "12.00", null);
		refresh(1, 1);

		assertThat(refresh(1, 1)).bodyJson().isLenientlyEqualTo("[{\"status\": \"FRESH\"}]");
		assertThat(connector.priceCalls).isEqualTo(1);

		jdbc.update("UPDATE supplier_offer SET imported_at = imported_at - interval '25 hours' WHERE id = 1");
		assertThat(refresh(1, 1)).bodyJson().isLenientlyEqualTo("[{\"status\": \"UPDATED\"}]");
		assertThat(connector.priceCalls).isEqualTo(2);
	}

	@Test
	void anOfferWithoutPriceOrManufacturerCodeKeepsWhatItHas() {
		assertThat(mvc.post().uri("/api/supplier-offers/refresh").with(csrf()).with(TestUsers.user())
			.contentType(MediaType.APPLICATION_JSON).content("{\"offerIds\": [1, 2]}"))
			.hasStatusOk().bodyJson().isLenientlyEqualTo("""
					[{"offerId": 1, "status": "NOT_PRICED", "offer": {"netPrice": 10.0000, "priceSource": "IMPORT"}},
					 {"offerId": 2, "status": "NOT_CONNECTED", "offer": {"netPrice": 20.0000}}]""");
		assertThat(historyCount(1)).isEqualTo(1);
	}

	@Test
	void aFailureOfTheSupplierChangesNothing() {
		connector.failure = new SupplierConnectorException(SupplierConnectorException.Kind.TEMPORARY);

		assertThat(refresh(1, 1)).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(jdbc.queryForObject("SELECT net_price FROM supplier_offer WHERE id = 1", BigDecimal.class))
			.isEqualByComparingTo("10");
	}

	@Test
	void aNewFileReplacesAReadPriceAndKeepsTheManufacturerCode() {
		connector.price("A-1", "12.00", null);
		refresh(1, 1);

		mvc.post().uri("/api/suppliers/1/offers").multipart()
			.file(new MockMultipartFile("file", "tarif.csv", "text/csv",
					"Référence;Désignation;Prix net\nACM1;Disjoncteur 16A;15,00\n".getBytes(StandardCharsets.UTF_8)))
			.with(csrf()).with(TestUsers.admin()).exchange();

		assertThat(mvc.get().uri("/api/supplier-offers?q=ACM1").with(TestUsers.user())).bodyJson()
			.isLenientlyEqualTo("""
					[{"netPrice": 15.0000, "priceSource": "IMPORT", "manufacturerCode": "ACM", "manufacturerRef": "A-1"}]""");
		assertThat(historyCount(1)).isEqualTo(3);
	}

	@Test
	void aPriceReadIsCopiedInAnEstimateWhichKeepsItAfterAnotherRead() {
		connector.price("A-1", "12.00", null);
		refresh(1, 1);
		long tenderId = jdbc.queryForObject("""
				INSERT INTO tender (source, external_id, title, status, created_at, updated_at)
				VALUES ('BOAMP', '26-1', 'Éclairage', 'ACTIVE', now(), now()) RETURNING id""", Long.class);
		post("/api/tenders/" + tenderId + "/estimates", "{}").exchange();
		post("/api/estimates/1/items",
				"{\"designation\": \"Disjoncteur\", \"unit\": \"u\", \"quantity\": 2, \"priceMode\": \"COMPOSED\"}").exchange();
		assertThat(post("/api/estimate-items/1/components",
				"{\"type\": \"SUPPLY\", \"label\": \"Disjoncteur\", \"quantity\": 1, \"offerId\": 1}"))
			.hasStatus(HttpStatus.CREATED).bodyJson().isLenientlyEqualTo("""
					{"totalHt": 24.00, "items": [{"components": [{"unitCost": 12.0000, "costDate": "2026-11-04"}]}]}""");

		connector.price("A-1", "13.00", null);
		jdbc.update("UPDATE supplier_offer SET imported_at = imported_at - interval '25 hours' WHERE id = 1");
		refresh(1, 1);

		assertThat(mvc.get().uri("/api/estimates/1").with(TestUsers.user())).bodyJson()
			.isLenientlyEqualTo("{\"totalHt\": 24.00}");
	}

	@Test
	void aDraftComparesItsCostsThenCopiesThePricesOnlyWhenAsked() {
		connector.price("A-1", "12.00", null);
		refresh(1, 1);
		long tenderId = jdbc.queryForObject("""
				INSERT INTO tender (source, external_id, title, status, created_at, updated_at)
				VALUES ('BOAMP', '26-1', 'Éclairage', 'ACTIVE', now(), now()) RETURNING id""", Long.class);
		post("/api/tenders/" + tenderId + "/estimates", "{}").exchange();
		post("/api/estimates/1/items",
				"{\"designation\": \"Disjoncteur\", \"unit\": \"u\", \"quantity\": 2, \"priceMode\": \"COMPOSED\"}").exchange();
		post("/api/estimate-items/1/components", """
				{"type": "SUPPLY", "label": "Disjoncteur", "quantity": 1.5, "wasteRate": 0.1, "offerId": 1}""").exchange();
		post("/api/estimate-items/1/components", """
				{"type": "LABOR", "label": "Pose", "quantity": 1, "unitCost": 30}""").exchange();

		// 2 x 1.5 x 1.1 = 3.3, four whole units asked for; the labour is not a price of a supplier.
		connector.price("A-1", "13.00", null);
		assertThat(post("/api/estimates/1/supplier-prices/check", "")).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				[{"label": "Disjoncteur", "offerId": 1, "quantityToBuy": 4, "currentCost": 12.0000, "newCost": 13.0000,
				  "status": "UPDATED", "priceLabel": "Prix unitaire € HT", "availability": {"branchStock": "7"}}]""");
		assertThat(connector.lastQuantity).isEqualTo(4);
		assertThat(mvc.get().uri("/api/estimates/1").with(TestUsers.user())).bodyJson()
			.isLenientlyEqualTo("{\"items\": [{\"components\": [{\"unitCost\": 12.0000}, {\"unitCost\": 30.0000}]}]}");

		assertThat(post("/api/estimates/1/supplier-prices/copy", "")).hasStatusOk().bodyJson()
			.isLenientlyEqualTo("{\"items\": [{\"components\": [{\"unitCost\": 13.0000, \"costDate\": \"2026-11-04\"}, {\"unitCost\": 30.0000}]}]}");
		assertThat(mvc.get().uri("/api/estimates/1/events").with(TestUsers.user())).bodyText()
			.contains("SUPPLIER_PRICES_COPIED", "1 coût(s) modifié(s) d'après les prix des fournisseurs");

		post("/api/estimates/1/validation", "").exchange();
		assertThat(post("/api/estimates/1/supplier-prices/check", "")).hasStatus(HttpStatus.CONFLICT);
		assertThat(post("/api/estimates/1/supplier-prices/copy", "")).hasStatus(HttpStatus.CONFLICT);
	}

	@Test
	void aPriceOnlyImportedIsNotCopiedInADraft() {
		long tenderId = jdbc.queryForObject("""
				INSERT INTO tender (source, external_id, title, status, created_at, updated_at)
				VALUES ('BOAMP', '26-1', 'Éclairage', 'ACTIVE', now(), now()) RETURNING id""", Long.class);
		post("/api/tenders/" + tenderId + "/estimates", "{}").exchange();
		post("/api/estimates/1/items",
				"{\"designation\": \"X\", \"unit\": \"u\", \"quantity\": 1, \"priceMode\": \"COMPOSED\"}").exchange();
		post("/api/estimate-items/1/components",
				"{\"type\": \"SUPPLY\", \"label\": \"X\", \"quantity\": 1, \"offerId\": 1}").exchange();
		jdbc.update("UPDATE supplier_offer SET net_price = 99 WHERE id = 1");

		assertThat(post("/api/estimates/1/supplier-prices/check", "")).bodyJson()
			.isLenientlyEqualTo("[{\"status\": \"NOT_PRICED\", \"newCost\": null, \"currentCost\": 10.0000}]");
		assertThat(post("/api/estimates/1/supplier-prices/copy", "")).bodyJson()
			.isLenientlyEqualTo("{\"items\": [{\"components\": [{\"unitCost\": 10.0000}]}]}");
	}

	@Test
	void fillsTheCatalogueFromThePurchasesKeepingOnlyWhatIsPriced() throws Exception {
		connector.purchases = new SupplierConnector.Purchases(3, 1,
				List.of(new SupplierConnector.Product("ACM", "NEW-2", 1), new SupplierConnector.Product("ACM", "A-1", 1),
						new SupplierConnector.Product("BAD", "NOPE", 1)));
		connector.price("NEW-2", "40.00", null);
		connector.price("A-1", "11.00", null);

		assertThat(post("/api/suppliers/connectors/ACME/catalogue", "")).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(mvc.post().uri("/api/suppliers/connectors/ACME/catalogue").with(csrf()).with(TestUsers.admin()))
			.hasStatus(HttpStatus.ACCEPTED);
		for (int wait = 0; wait < 100 && Boolean.TRUE.equals(pricing.filling("ACME").running()); wait++) {
			Thread.sleep(100);
		}

		assertThat(mvc.get().uri("/api/suppliers/connectors/ACME/catalogue").with(TestUsers.admin())).bodyJson()
			.isLenientlyEqualTo("""
					{"running": false, "ordersRead": 3, "ordersFailed": 1, "products": 3, "imported": 2, "notPriced": 1, "error": null}""");
		// The new product can be searched by words, the known one is the same offer.
		assertThat(mvc.get().uri("/api/supplier-offers?q=produit new").with(TestUsers.user())).bodyJson()
			.isLenientlyEqualTo("[{\"supplierRef\": \"SUP-NEW-2\", \"manufacturerCode\": \"ACM\", \"netPrice\": 40.0000, \"packQuantity\": 1.0000}]");
		assertThat(jdbc.queryForObject("SELECT net_price FROM supplier_offer WHERE id = 1", BigDecimal.class))
			.isEqualByComparingTo("11");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM supplier_offer", Long.class)).isEqualTo(3);
	}

	@Test
	void theStocksAreKeptAMomentByQuantity() {
		query("/api/supplier-offers/availability", 1, 5);
		assertThat(query("/api/supplier-offers/availability", 1, 5)).hasStatusOk().bodyJson()
			.isLenientlyEqualTo("[{\"offerId\": 1, \"availability\": {\"branchStock\": \"7\"}}]");
		assertThat(connector.stockCalls).isEqualTo(1);

		query("/api/supplier-offers/availability", 1, 6);
		assertThat(connector.stockCalls).isEqualTo(2);
	}

	@Test
	void aReferenceGivenByHandMakesAnOfferOnlyIfTheSupplierPricesIt() {
		String body = """
				{"supplierCode": "acme", "manufacturerCode": "acm", "manufacturerRef": "NEW-1", "packQuantity": 100}""";
		assertThat(post("/api/supplier-offers/lookup", body)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM supplier_offer", Long.class)).isEqualTo(2);

		connector.price("NEW-1", "50.00", null);
		assertThat(post("/api/supplier-offers/lookup", body)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{"supplierRef": "SUP-NEW-1", "manufacturerCode": "ACM", "manufacturerRef": "NEW-1", "packQuantity": 100,
				 "netPrice": 50.0000, "priceSource": "FAKE"}""");

		// Asked again, the same offer is updated.
		connector.price("NEW-1", "55.00", null);
		post("/api/supplier-offers/lookup", body).exchange();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM supplier_offer WHERE manufacturer_ref = 'NEW-1'", Long.class))
			.isEqualTo(1);
	}

	@Test
	void validatesWhatIsSentAndRequiresALogin() {
		assertThat(post("/api/supplier-offers/refresh", "{\"offerIds\": []}")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(post("/api/supplier-offers/refresh", "{\"offerIds\": [1], \"quantity\": 0}"))
			.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(post("/api/supplier-offers/lookup",
				"{\"supplierCode\": \"ACME\", \"manufacturerCode\": \"A B\", \"manufacturerRef\": \"X\"}"))
			.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(mvc.post().uri("/api/supplier-offers/refresh").with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"offerIds\": [1]}")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void showsTheConnectorsWithoutAnySecretAndRexelStaysOff() {
		assertThat(mvc.get().uri("/api/supplier-connectors").with(TestUsers.user())).hasStatusOk()
			.bodyText()
			.contains("\"supplierCode\":\"ACME\",\"supplierName\":\"Acme\",\"available\":true")
			.contains("\"supplierCode\":\"REXEL\",\"supplierName\":\"Rexel\",\"available\":false,\"environment\":null")
			.doesNotContain("secret", "Key");
	}

	private long historyCount(long offerId) {
		return jdbc.queryForObject("SELECT count(*) FROM supplier_offer_price WHERE offer_id = ?", Long.class, offerId);
	}

	private MvcTestResult refresh(long offerId, int quantity) {
		return query("/api/supplier-offers/refresh", offerId, quantity);
	}

	private MvcTestResult query(String uri, long offerId, int quantity) {
		return post(uri, "{\"offerIds\": [" + offerId + "], \"quantity\": " + quantity + "}").exchange();
	}

	private MockMvcTester.MockMvcRequestBuilder post(String uri, String body) {
		return mvc.post().uri(uri).with(csrf()).with(TestUsers.user()).contentType(MediaType.APPLICATION_JSON).content(body);
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class FakeConnectorConfiguration {

		@Bean
		FakeConnector fakeConnector() {
			return new FakeConnector();
		}

	}

	static class FakeConnector implements SupplierConnector {

		private final List<Price> prices = new ArrayList<>();

		int priceCalls;

		int stockCalls;

		int lastQuantity;

		SupplierConnectorException failure;

		Purchases purchases = new Purchases(0, 0, List.of());

		@Override
		public Purchases purchases() {
			return purchases;
		}

		void reset() {
			prices.clear();
			priceCalls = 0;
			stockCalls = 0;
			failure = null;
		}

		void price(String ref, String net, String condition) {
			prices.removeIf(price -> price.manufacturerRef().equals(ref));
			prices.add(new Price("ACM", ref, "SUP-" + ref, "Produit " + ref, null, null, new BigDecimal(net), null,
					condition, "Prix unitaire € HT", null));
		}

		@Override
		public String supplierCode() {
			return "ACME";
		}

		@Override
		public String supplierName() {
			return "Acme";
		}

		@Override
		public boolean available() {
			return true;
		}

		@Override
		public String environment() {
			return "TEST";
		}

		@Override
		public String source() {
			return "FAKE";
		}

		@Override
		public List<Price> prices(List<Product> products, Place place) {
			priceCalls++;
			lastQuantity = products.getFirst().quantity();
			if (failure != null) {
				throw failure;
			}
			return List.copyOf(prices);
		}

		@Override
		public List<Availability> availability(List<Product> products, Place place) {
			stockCalls++;
			return products.stream()
				.map(product -> new Availability(product.manufacturerCode(), product.manufacturerRef(), null, "7", null,
						null, null, null, null, null, null, null, null, null, null, null, null))
				.toList();
		}

	}

}
