package eu.gpefrance.tenders.supplier.rexel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import eu.gpefrance.tenders.supplier.SupplierConnector.Availability;
import eu.gpefrance.tenders.supplier.SupplierConnector.Place;
import eu.gpefrance.tenders.supplier.SupplierConnector.Price;
import eu.gpefrance.tenders.supplier.SupplierConnector.Product;
import eu.gpefrance.tenders.supplier.SupplierConnectorException;
import eu.gpefrance.tenders.supplier.SupplierConnectorException.Kind;

/** The client of Rexel against a simulated server: no test ever calls Rexel. */
class RexelClientTests {

	private static final String TOKEN_V2 = "https://login.test/tenant/oauth2/v2.0/token";

	private static final String TOKEN_V1 = "https://login.test/tenant/oauth2/token";

	private static final String PRICES = "https://apidev.rexel.fr/external/productprices/productSalePrices";

	private static final String STOCKS = "https://apidev.rexel.fr/external/stocks/positions";

	private static final Place NOWHERE = new Place(null, null, null);

	private static final List<Product> ONE = List.of(new Product("SCH", "A9F", 10));

	private MockRestServiceServer server;

	private RexelConnector connector;

	@BeforeEach
	void setUp() {
		connector = connector(properties(RexelEnvironment.TEST, TOKEN_V2, 20));
	}

	private RexelConnector connector(RexelProperties properties) {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		Clock clock = Clock.fixed(Instant.parse("2026-11-04T08:00:00Z"), ZoneOffset.UTC);
		RexelHttpClient client = new RexelHttpClient(builder, properties,
				new RexelTokenService(builder, properties, clock));
		return new RexelConnector(properties, new RexelPriceService(client), new RexelStockService(client),
				new RexelOrderService(client, properties));
	}

	private static RexelProperties properties(RexelEnvironment environment, String tokenUrl, int batchSize) {
		return new RexelProperties(true, environment, tokenUrl, "the-client", "the-secret", "the-key", "2120478",
				batchSize, Duration.ZERO);
	}

	private void expectToken(String value) {
		server.expect(requestTo(TOKEN_V2))
			.andRespond(withSuccess("{\"access_token\": \"" + value + "\", \"expires_in\": \"3599\"}",
					MediaType.APPLICATION_JSON));
	}

	private static String prices(String lines) {
		return "{\"data\": {\"productSalePricesExt\": {\"idCustomer\": \"2120478\", \"productDetails\": [" + lines + "]}}}";
	}

	private static String priceLine(String ref, String net) {
		return """
				{"supplierCode": "SCH", "supplierComRef": "%s", "rexelRef": "SCH%s", "itemLabel": "Disjoncteur",
				 "clientBasePrice": "100,00", "clientNetPrice": "%s", "hasD3E": "true", "D3ECost": "0.05", "conditionLabel": "Remise",
				 "priceLabel": "Net", "salesAgreement": "123"}""".formatted(ref, ref, net);
	}

	@Test
	void asksATokenWithAScopeThenSendsBothSecurityHeadersAndTheProducts() {
		server.expect(requestTo(TOKEN_V2))
			.andExpect(method(HttpMethod.POST))
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
			.andExpect(content().string(containsString("grant_type=client_credentials")))
			.andExpect(content().string(containsString("scope=" + RexelEnvironment.TEST.resource() + "%2F.default")))
			.andRespond(withSuccess("{\"access_token\": \"jwt-1\", \"expires_in\": 3599}", MediaType.APPLICATION_JSON));
		server.expect(requestTo(PRICES))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header("Authorization", "Bearer jwt-1"))
			.andExpect(header("Ocp-Apim-Subscription-Key", "the-key"))
			.andExpect(jsonPath("$.getProductSalePricesExt.idCodOrigin").value("GPE"))
			.andExpect(jsonPath("$.getProductSalePricesExt.idNumVersion").value("1"))
			.andExpect(jsonPath("$.getProductSalePricesExt.idCustomer").value("2120478"))
			.andExpect(jsonPath("$.getProductSalePricesExt.productDetails[0].supplierCode").value("SCH"))
			.andExpect(jsonPath("$.getProductSalePricesExt.productDetails[0].supplierComRef").value("A9F"))
			.andExpect(jsonPath("$.getProductSalePricesExt.productDetails[0].orderingQty").value(10))
			.andExpect(jsonPath("$.getProductSalePricesExt.zipCode").value("85500"))
			.andExpect(jsonPath("$.getProductSalePricesExt.agenceCode").doesNotExist())
			.andRespond(withSuccess(prices(priceLine("A9F", "1 234,50")), MediaType.APPLICATION_JSON));

		List<Price> prices = connector.prices(ONE, new Place(null, "85500", null));

		server.verify();
		assertThat(prices).singleElement().satisfies(price -> {
			assertThat(price.netPrice()).isEqualByComparingTo("1234.50");
			assertThat(price.listPrice()).isEqualByComparingTo("100");
			assertThat(price.ecoTax()).isEqualByComparingTo("0.05");
			assertThat(price.supplierRef()).isEqualTo("SCHA9F");
			assertThat(price.conditionLabel()).isEqualTo("Remise");
		});
	}

	@Test
	void asksATokenWithAResourceOnTheUrlOfTheDocumentationAndOnTheHostOfProduction() {
		connector = connector(properties(RexelEnvironment.PRODUCTION, TOKEN_V1, 20));
		server.expect(requestTo(TOKEN_V1))
			.andExpect(content().string(containsString("resource=" + RexelEnvironment.PRODUCTION.resource())))
			.andExpect(content().string(not(containsString("scope="))))
			.andRespond(withSuccess("{\"access_token\": \"jwt\", \"expires_in\": 3599}", MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://api.rexel.fr/external/productprices/productSalePrices"))
			.andRespond(withSuccess(prices(""), MediaType.APPLICATION_JSON));

		assertThat(connector.prices(ONE, NOWHERE)).isEmpty();
		server.verify();
	}

	/** Rexel refuses fields out of their sequence: the exact text, not only the values. */
	@Test
	void sendsTheFieldsInTheOrderRexelRequires() {
		expectToken("jwt");
		server.expect(requestTo(PRICES))
			.andExpect(content().string("""
					{"getProductSalePricesExt":{"idCodOrigin":"GPE","idNumVersion":"1","idCustomer":"2120478",\
					"productDetails":[{"supplierCode":"SCH","supplierComRef":"A9F","orderingQty":10}],\
					"agenceCode":"8501","zipCode":"85500","city":"Fontenay"}}"""))
			.andRespond(withSuccess(prices(""), MediaType.APPLICATION_JSON));

		connector.prices(ONE, new Place("8501", "85500", "Fontenay"));
		server.verify();
	}

	/** The shape answered by the test environment of Rexel, with other figures: a dot after the code, a warning. */
	@Test
	void readsTheAnswerOfRexelWithAnUnknownReferenceLeftOut() {
		expectToken("jwt");
		server.expect(requestTo(PRICES)).andRespond(withSuccess("""
				{"data": {"productSalePricesExt": {"idCustomer": "2120478", "productDetails": [
				  {"zipCode": "00000", "idProduct": "1", "rexelRef": "SCHA9F", "supplierComRef": "A9F", "supplierCode": "SCH.",
				   "itemLabel": "DISJONCTEUR      00", "clientBasePrice": "200.00000", "clientNetPrice": "120.12345",
				   "hasD3E": "false", "D3EQuantity": "0", "D3ECost": "0", "salesAgreement": null, "conditionLabel": "GP 39,94",
				   "totalPrice": "144.14814", "isVATInclusive": "false", "priceLabel": "Prix unitaire € HT",
				   "netLineAmount": "1201.23", "totalLineAmount": "1441.48"}],
				 "FuncErrors": {"WSFaultInfos": {"errorCode": "08", "errorMessage": "not found", "errorType": "WARNING"}}}}}""",
				MediaType.APPLICATION_JSON));

		List<Price> prices = connector.prices(List.of(new Product("SCH", "A9F", 10), new Product("SCH", "UNKNOWN", 1)), NOWHERE);

		assertThat(prices).singleElement().satisfies(price -> {
			assertThat(price.key()).isEqualTo(ONE.getFirst().key());
			assertThat(price.netPrice()).isEqualByComparingTo("120.1235");
			assertThat(price.discountRate()).isEqualByComparingTo("0.3994");
			assertThat(price.ecoTax()).isNull();
			assertThat(price.salesAgreement()).isNull();
			assertThat(price.priceLabel()).isEqualTo("Prix unitaire € HT");
		});
	}

	@Test
	void findsThePurchasedProductsInThePastOrdersSkippingAnOrderItDoesNotGive() {
		String orders = "https://apidev.rexel.fr/orders/";
		expectToken("jwt");
		server.expect(requestTo(orders + "ordersListByCustomerId/2120478"))
			.andExpect(method(HttpMethod.GET))
			.andExpect(header("Ocp-Apim-Subscription-Key", "the-key"))
			.andRespond(withSuccess("""
					{"name": "x", "elements": [{"codeAgenceCommande": "1319", "numeroCommande": 412420},
					 {"codeAgenceCommande": "1319", "numeroCommande": 999}]}""", MediaType.APPLICATION_JSON));
		server.expect(requestTo(orders + "orderDetailsById/2120478/1319-000412420"))
			.andRespond(withSuccess("""
					{"lines": [{"referenceCommerciale": "FILR2V5G10TGL", "referenceRexel": "FIL031700"},
					 {"referenceCommerciale": "filr2v5g10tgl"}, {"referenceCommerciale": "ACOM5007C1"},
					 {"referenceCommerciale": "AB"}, {"libelleCourtRexel": "sans référence"}]}""",
					MediaType.APPLICATION_JSON));
		server.expect(requestTo(orders + "orderDetailsById/2120478/1319-000000999"))
			.andRespond(withStatus(HttpStatus.NOT_FOUND));

		var purchases = connector.purchases();

		server.verify();
		assertThat(purchases.ordersRead()).isEqualTo(1);
		assertThat(purchases.ordersFailed()).isEqualTo(1);
		assertThat(purchases.products()).containsExactly(new Product("FIL", "R2V5G10TGL", 1), new Product("ACO", "M5007C1", 1));
	}

	@Test
	void keepsTheTokenUntilItEnds() {
		expectToken("jwt-1");
		server.expect(times(2), requestTo(PRICES))
			.andExpect(header("Authorization", "Bearer jwt-1"))
			.andRespond(withSuccess(prices(""), MediaType.APPLICATION_JSON));

		connector.prices(ONE, NOWHERE);
		connector.prices(ONE, NOWHERE);
		server.verify();
	}

	@Test
	void asksANewTokenOnceAfterARefusal() {
		expectToken("old");
		server.expect(requestTo(PRICES))
			.andExpect(header("Authorization", "Bearer old"))
			.andRespond(withUnauthorizedRequest());
		expectToken("new");
		server.expect(requestTo(PRICES))
			.andExpect(header("Authorization", "Bearer new"))
			.andRespond(withSuccess(prices(priceLine("A9F", "10")), MediaType.APPLICATION_JSON));

		assertThat(connector.prices(ONE, NOWHERE)).hasSize(1);
		server.verify();
	}

	@Test
	void aSecondRefusalIsAnAuthenticationFailure() {
		expectToken("old");
		server.expect(requestTo(PRICES)).andRespond(withUnauthorizedRequest());
		expectToken("new");
		server.expect(requestTo(PRICES)).andRespond(withUnauthorizedRequest());

		assertFails(Kind.AUTHENTICATION);
		server.verify();
	}

	@Test
	void aTokenRefusedIsAnAuthenticationFailure() {
		server.expect(requestTo(TOKEN_V2)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

		assertFails(Kind.AUTHENTICATION);
		server.verify();
	}

	@Test
	void sendsTheProductsByBatchesWithoutDuplicates() {
		connector = connector(properties(RexelEnvironment.TEST, TOKEN_V2, 2));
		expectToken("jwt");
		server.expect(requestTo(PRICES))
			.andExpect(jsonPath("$.getProductSalePricesExt.productDetails.length()").value(2))
			.andRespond(withSuccess(prices(priceLine("R0", "1") + "," + priceLine("R1", "2")),
					MediaType.APPLICATION_JSON));
		server.expect(requestTo(PRICES))
			.andExpect(jsonPath("$.getProductSalePricesExt.productDetails.length()").value(1))
			.andRespond(withSuccess(prices(priceLine("R2", "3")), MediaType.APPLICATION_JSON));
		List<Product> products = new java.util.ArrayList<>(
				IntStream.range(0, 3).mapToObj(number -> new Product("SCH", "R" + number, 1)).toList());
		products.add(new Product("sch", "r0", 1));

		assertThat(connector.prices(products, NOWHERE)).extracting(Price::manufacturerRef)
			.containsExactly("R0", "R1", "R2");
		server.verify();
	}

	@Test
	void leavesOutALineWithoutAUsablePriceNeverAtZero() {
		expectToken("jwt");
		server.expect(requestTo(PRICES))
			.andRespond(withSuccess(prices(String.join(",", priceLine("ZERO", "0"), priceLine("TEXT", "sur devis"),
					"{\"supplierCode\": \"SCH\", \"supplierComRef\": \"NONE\"}", priceLine("GOOD", "12.5"))),
					MediaType.APPLICATION_JSON));

		assertThat(connector.prices(ONE, NOWHERE)).extracting(Price::manufacturerRef).containsExactly("GOOD");
	}

	@Test
	void computesTheDiscountFromTheTwoPrices() {
		expectToken("jwt");
		server.expect(requestTo(PRICES))
			.andRespond(withSuccess(prices(priceLine("A9F", "65")), MediaType.APPLICATION_JSON));

		assertThat(connector.prices(ONE, NOWHERE).getFirst().discountRate()).isEqualByComparingTo(new BigDecimal("0.35"));
	}

	@Test
	void triesAgainOnceAfterATemporaryFailureThenGivesUp() {
		expectToken("jwt");
		server.expect(requestTo(PRICES)).andRespond(withServerError());
		server.expect(requestTo(PRICES)).andRespond(withServerError());

		assertFails(Kind.TEMPORARY);
		server.verify();
	}

	@Test
	void neverRepeatsARateLimitOrARefusal() {
		expectToken("jwt");
		server.expect(requestTo(PRICES)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
		assertFails(Kind.RATE_LIMITED);
		server.verify();

		server.reset();
		server.expect(requestTo(PRICES)).andRespond(withStatus(HttpStatus.BAD_REQUEST));
		assertFails(Kind.REJECTED);
		server.verify();
	}

	@Test
	void anAnswerOfAnotherShapeIsInvalid() {
		expectToken("jwt");
		server.expect(requestTo(PRICES)).andRespond(withSuccess("{\"unexpected\": 1}", MediaType.APPLICATION_JSON));

		assertFails(Kind.INVALID_RESPONSE);
	}

	@Test
	void readsTheStocksWhetherOneObjectOrAList() {
		String line = """
				{"supplierCode": "SCH", "supplierComRef": "A9F", "itemLabel": "Disjoncteur", "availableBranchStock": "4",
				 "branchAvailabilityDelay": "J+1", "availableCLRStock": "120", "DCAvailabilityDelay": "J+2",
				 "deliveryDate": "2026-11-06"}""";
		expectToken("jwt");
		server.expect(requestTo(STOCKS))
			.andExpect(jsonPath("$.getPositionsExtRequest.productDetails[0].orderingQty").value(10))
			.andRespond(withSuccess("{\"data\": {\"getPositionsExt\": {\"productDetails\": " + line + "}}}",
					MediaType.APPLICATION_JSON));
		server.expect(requestTo(STOCKS))
			.andRespond(withSuccess("{\"data\": {\"getPositionsExt\": {\"productDetails\": [" + line + "]}}}",
					MediaType.APPLICATION_JSON));

		List<Availability> single = connector.availability(ONE, NOWHERE);
		List<Availability> list = connector.availability(ONE, NOWHERE);

		assertThat(single).singleElement().satisfies(stock -> {
			assertThat(stock.branchStock()).isEqualTo("4");
			assertThat(stock.warehouseStock()).isEqualTo("120");
			assertThat(stock.deliveryDate()).isEqualTo("2026-11-06");
		});
		assertThat(list).isEqualTo(single);
		server.verify();
	}

	@Test
	void sendsNothingWhileDisabledOrIncomplete() {
		for (RexelProperties properties : List.of(
				new RexelProperties(false, RexelEnvironment.TEST, TOKEN_V2, "c", "s", "k", "1", 20, Duration.ZERO),
				new RexelProperties(true, null, TOKEN_V2, "c", "s", "k", "1", 20, Duration.ZERO),
				new RexelProperties(true, RexelEnvironment.TEST, TOKEN_V2, "c", "s", "k", " ", 20, Duration.ZERO))) {
			RexelConnector off = connector(properties);
			assertThat(off.available()).isFalse();
			assertThatThrownBy(() -> off.prices(ONE, NOWHERE)).isInstanceOfSatisfying(SupplierConnectorException.class,
					ex -> assertThat(ex.getKind()).isEqualTo(Kind.NOT_CONFIGURED));
			server.verify();
		}
	}

	@Test
	void theSecretsAreNotPrinted() {
		assertThat(properties(RexelEnvironment.TEST, TOKEN_V2, 20).toString()).doesNotContain("the-secret", "the-key");
	}

	private void assertFails(Kind kind) {
		assertThatThrownBy(() -> connector.prices(ONE, NOWHERE)).isInstanceOfSatisfying(SupplierConnectorException.class,
				ex -> assertThat(ex.getKind()).isEqualTo(kind));
	}

}
