package eu.gpefrance.tenders.estimate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
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
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import eu.gpefrance.tenders.ClockConfiguration;
import eu.gpefrance.tenders.TestUsers;
import eu.gpefrance.tenders.TestcontainersConfiguration;
import eu.gpefrance.tenders.analysis.ExtractionClient;
import eu.gpefrance.tenders.analysis.ExtractionClient.ModelAnswer;
import eu.gpefrance.tenders.analysis.ExtractionRequest;

/**
 * The AI proposes, the application prices, a user decides: with a simulated
 * provider, never a real one.
 */
@SpringBootTest(properties = { "analysis.enabled=true", "analysis.provider=test",
		"analysis.external-transmission-allowed=true" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EstimateAssistantTests {

	private static final Instant NOW = Instant.parse("2026-10-31T08:00:00Z");

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoBean
	private ExtractionClient client;

	@TestBean
	private Clock clock;

	static Clock clock() {
		return Clock.fixed(NOW, ClockConfiguration.ZONE);
	}

	private final List<ExtractionRequest> sent = new ArrayList<>();

	@BeforeEach
	void prepare() {
		jdbc.execute("TRUNCATE ai_call, supplier, estimate, lot, tender RESTART IDENTITY CASCADE");
		jdbc.update("UPDATE pricing_coefficient SET coefficient = 1.0000");
		jdbc.update("INSERT INTO supplier (code, name, created_at) VALUES ('REXEL', 'Rexel', now())");
		jdbc.update("""
				INSERT INTO supplier_offer (supplier_id, supplier_ref, designation, purchase_unit, net_price, imported_at)
				VALUES (1, 'LEG077121', 'Prise de courant 2P+T 16A Mosaic', 'U', 3.20, now()),
				       (1, 'LEG077122', 'Prise de courant 2P+T 16A Mosaic alu', 'U', 5.10, now())""");
		long tenderId = jdbc.queryForObject("""
				INSERT INTO tender (source, external_id, title, status, created_at, updated_at)
				VALUES ('BOAMP', '26-1', 'École', 'ACTIVE', now(), now()) RETURNING id""", Long.class);
		send("/api/tenders/" + tenderId + "/estimates", "{}");
		send("/api/estimates/1/items", "{\"designation\": \"Prise de courant 2P+T 16 A encastrée\", \"unit\": \"u\", "
				+ "\"quantity\": 10, \"priceMode\": \"COMPOSED\"}");
		given(client.provider()).willReturn("test");
		given(client.model()).willReturn("test-model");
		given(client.complete(any(ExtractionRequest.class))).willAnswer(invocation -> {
			ExtractionRequest request = invocation.getArgument(0);
			sent.add(request);
			return request.analysisType().equals("ESTIMATE_SUPPLY") ? new ModelAnswer("""
					{"suggestions": [
					  {"offer_id": 1, "quantity": 1, "explanation": "Prise 2P+T 16 A de la gamme Mosaic."},
					  {"offer_id": 999, "quantity": 1, "explanation": "Offre inventée."},
					  {"offer_id": 2, "quantity": -3, "explanation": "Variante alu."}]}""", 1200, 80)
					: new ModelAnswer("""
							{"points": [{"item_id": 1, "text": "Pas de main-d'œuvre pour un poste fourni posé."},
							            {"item_id": 777, "text": "Point général."}]}""", 900, 60);
		});
	}

	@Test
	void proposesOffersOfTheCatalogueWhichAUserAccepts() {
		assertThat(post("/api/estimate-items/1/supply-suggestions")).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					[{"id": 1, "kind": "SUPPLY", "offerId": 1, "quantity": 1.0000, "status": "PENDING",
					  "model": "test-model"},
					 {"id": 2, "offerId": 2, "quantity": null}]""");
		// The AI receives the offers found by the application, without their prices.
		assertThat(sent.getFirst().documents()).contains("LEG077121").doesNotContain("3.2").doesNotContain("net");
		assertThat(jdbc.queryForObject("SELECT input_tokens FROM ai_call", Integer.class)).isEqualTo(1200);

		assertThat(post("/api/estimate-suggestions/1/acceptance")).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{"totalHt": 32.00, "items": [{"components": [{"type": "SUPPLY", "quantity": 1.0000,
					 "unitCost": 3.2000, "supplierRef": "LEG077121", "offerId": 1}]}]}""");
		assertThat(post("/api/estimate-suggestions/1/acceptance")).hasStatus(HttpStatus.CONFLICT);
		assertThat(post("/api/estimate-suggestions/2/rejection")).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(mvc.get().uri("/api/estimates/1/suggestions").with(TestUsers.user())).bodyJson()
			.isLenientlyEqualTo("""
					[{"id": 1, "status": "ACCEPTED", "decidedBy": "Marie Durand"}, {"id": 2, "status": "REJECTED"}]""");
		assertThat(mvc.get().uri("/api/estimates/1/events").with(TestUsers.user())).bodyJson()
			.isLenientlyEqualTo("[{\"kind\": \"SUGGESTION_ACCEPTED\"}, {\"kind\": \"CREATED\"}]");
	}

	@Test
	void pointsOutWhatToCheckWithoutChangingAnything() {
		assertThat(post("/api/estimates/1/review")).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					[{"kind": "CHECK", "itemId": 1, "text": "Pas de main-d'œuvre pour un poste fourni posé."},
					 {"kind": "CHECK", "itemId": null}]""");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM estimate_component", Long.class)).isZero();
		assertThat(mvc.get().uri("/api/estimate-assistant").with(TestUsers.user())).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("{\"available\": true}");
	}

	private MockMvcTester.MockMvcRequestBuilder post(String uri) {
		return mvc.post().uri(uri).with(csrf()).with(TestUsers.user());
	}

	private void send(String uri, String body) {
		mvc.post().uri(uri).with(csrf()).with(TestUsers.user()).contentType(MediaType.APPLICATION_JSON).content(body)
			.exchange();
	}

}
