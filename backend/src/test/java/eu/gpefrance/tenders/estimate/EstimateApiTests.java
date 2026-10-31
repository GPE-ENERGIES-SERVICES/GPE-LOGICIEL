package eu.gpefrance.tenders.estimate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

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
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import eu.gpefrance.tenders.ClockConfiguration;
import eu.gpefrance.tenders.TestUsers;
import eu.gpefrance.tenders.TestcontainersConfiguration;

/**
 * An estimate entered through the API: items, their costs, the totals
 * computed by the application, and what may not be changed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EstimateApiTests {

	private static final Instant NOW = Instant.parse("2026-10-31T08:00:00Z");

	private static final String SOCKET = """
			{"chapter": "Courants forts", "reference": "2.1", "designation": "Prise 2P+T 16 A",
			 "unit": "u", "quantity": 42, "priceMode": "COMPOSED"}""";

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
		jdbc.execute("TRUNCATE estimate, lot, tender RESTART IDENTITY CASCADE");
		tenderId = jdbc.queryForObject("""
				INSERT INTO tender (source, external_id, title, status, created_at, updated_at)
				VALUES ('BOAMP', '26-1', 'Rénovation électrique école', 'ACTIVE', now(), now()) RETURNING id""",
				Long.class);
		jdbc.update("INSERT INTO lot (tender_id, position, number, title) VALUES (?, 1, '2', 'Électricité')", tenderId);
	}

	@Test
	void entersAnEstimateAndGetsItsFiguresComputed() {
		assertThat(post("/api/tenders/" + tenderId + "/estimates", """
				{"lotNumber": "2", "label": "Offre de base", "coefficients": {"SUPPLY": 1.30, "LABOR": 1.50}}""")).hasStatus(HttpStatus.CREATED)
			.bodyJson()
			.isLenientlyEqualTo("""
					{"id": 1, "lotNumber": "2", "version": 1, "status": "DRAFT", "label": "Offre de base",
					 "vatRate": 0.2000, "totalHt": 0.00, "createdBy": "Marie Durand", "items": []}""");

		assertThat(post("/api/estimates/1/items", SOCKET)).hasStatus(HttpStatus.CREATED)
			.bodyJson()
			.isLenientlyEqualTo("""
					{"incompleteItems": 1, "items": [{"id": 1, "position": 1, "unitPrice": null, "amount": null}]}""");
		send("/api/estimate-items/1/components", """
				{"type": "SUPPLY", "label": "Prise 2P+T Legrand", "quantity": 1, "unit": "u", "unitCost": 3.20,
				 "supplierName": "Rexel", "supplierRef": "LEG077121", "costDate": "2026-10-30"}""");
		assertThat(post("/api/estimate-items/1/components", """
				{"type": "LABOR", "label": "Électricien", "quantity": 0.25, "unit": "h", "unitCost": 45}"""))
			.hasStatus(HttpStatus.CREATED)
			.bodyJson()
			.isLenientlyEqualTo("""
					{"totalCost": 606.90, "totalHt": 883.68, "totalVat": 176.74, "totalTtc": 1060.42,
					 "margin": 276.78, "incompleteItems": 0,
					 "items": [{"unitCost": 14.4500, "unitPrice": 21.04, "amount": 883.68,
					            "components": [{"position": 1, "supplierRef": "LEG077121"}, {"position": 2}]}]}""");

		// The coefficient of labour changed: every price is computed again.
		assertThat(mvc.put().uri("/api/estimates/1").with(csrf()).with(TestUsers.admin())
			.contentType(MediaType.APPLICATION_JSON).content("""
					{"label": "Offre de base", "coefficients": {"LABOR": 1.60}}"""))
			.hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{"vatRate": 0.2000, "totalHt": 930.72, "updatedBy": "Admin GPE",
					 "items": [{"unitPrice": 22.16}]}""");

		assertThat(mvc.get().uri("/api/tenders/" + tenderId + "/estimates").with(TestUsers.user())).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("[{\"id\": 1, \"version\": 1, \"totalHt\": 930.72}]");
	}

	@Test
	void removesAndOrdersItemsAndComponents() {
		send("/api/tenders/" + tenderId + "/estimates", "{}");
		send("/api/estimates/1/items", SOCKET);
		send("/api/estimates/1/items", """
				{"designation": "Dépose de l'existant", "unit": "ens", "quantity": 1, "priceMode": "FIXED",
				 "fixedUnitPrice": 850}""");
		send("/api/estimates/1/items", """
				{"designation": "Essais et autocontrôles", "priceMode": "TEXT", "textPrice": "PM"}""");
		send("/api/estimate-items/1/components", """
				{"type": "LABOR", "label": "Électricien", "quantity": 0.25, "unitCost": 45}""");

		assertThat(mvc.put().uri("/api/estimates/1/items/order").with(csrf()).with(TestUsers.user())
			.contentType(MediaType.APPLICATION_JSON).content("{\"itemIds\": [3, 1, 2]}"))
			.hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{"items": [{"id": 3, "position": 1, "textPrice": "PM", "amount": 0.00},
					           {"id": 1, "position": 2}, {"id": 2, "position": 3, "amount": 850.00}]}""");
		assertThat(mvc.put().uri("/api/estimates/1/items/order").with(csrf()).with(TestUsers.user())
			.contentType(MediaType.APPLICATION_JSON).content("{\"itemIds\": [3, 1]}"))
			.hasStatus(HttpStatus.BAD_REQUEST);

		assertThat(mvc.delete().uri("/api/estimate-components/1").with(csrf()).with(TestUsers.user()))
			.hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("{\"totalHt\": 850.00, \"incompleteItems\": 1}");
		assertThat(mvc.delete().uri("/api/estimate-items/3").with(csrf()).with(TestUsers.user()))
			.hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("{\"items\": [{\"id\": 1, \"position\": 1}, {\"id\": 2, \"position\": 2}]}");

		assertThat(mvc.delete().uri("/api/estimates/1").with(csrf()).with(TestUsers.user()))
			.hasStatus(HttpStatus.NO_CONTENT);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM estimate_item", Long.class)).isZero();
		assertThat(mvc.get().uri("/api/estimates/1").with(TestUsers.user())).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void aFrozenEstimateIsNeverChanged() {
		send("/api/tenders/" + tenderId + "/estimates", "{}");
		send("/api/estimates/1/items", SOCKET);
		jdbc.update("UPDATE estimate SET status = 'VALIDATED' WHERE id = 1");

		assertThat(post("/api/estimates/1/items", SOCKET)).hasStatus(HttpStatus.CONFLICT);
		assertThat(mvc.put().uri("/api/estimate-items/1").with(csrf()).with(TestUsers.user())
			.contentType(MediaType.APPLICATION_JSON).content(SOCKET)).hasStatus(HttpStatus.CONFLICT);
		assertThat(mvc.delete().uri("/api/estimates/1").with(csrf()).with(TestUsers.user()))
			.hasStatus(HttpStatus.CONFLICT);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM estimate_item", Long.class)).isEqualTo(1);

		// A new draft is the next version.
		assertThat(post("/api/tenders/" + tenderId + "/estimates", "{}")).hasStatus(HttpStatus.CREATED)
			.bodyJson()
			.isLenientlyEqualTo("{\"id\": 2, \"version\": 2, \"status\": \"DRAFT\"}");
	}

	@Test
	void refusesWhatCannotBeEstimated() {
		send("/api/tenders/" + tenderId + "/estimates", "{}");

		assertThat(post("/api/tenders/" + tenderId + "/estimates", "{}")).hasStatus(HttpStatus.CONFLICT);
		assertThat(post("/api/tenders/" + tenderId + "/estimates", "{\"lotNumber\": \"2\"}"))
			.hasStatus(HttpStatus.CREATED);
		assertThat(post("/api/tenders/999/estimates", "{}")).hasStatus(HttpStatus.NOT_FOUND);
		long other = jdbc.queryForObject("""
				INSERT INTO tender (source, external_id, title, status, created_at, updated_at, merged_into_id)
				VALUES ('TED', '1-2026', 'Doublon', 'ACTIVE', now(), now(), ?) RETURNING id""", Long.class, tenderId);
		assertThat(post("/api/tenders/" + other + "/estimates", "{}")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(post("/api/tenders/" + other + "/estimates", "{\"lotNumber\": \"2\"}"))
			.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(post("/api/tenders/" + tenderId + "/estimates", "{\"vatRate\": 1.5}"))
			.hasStatus(HttpStatus.BAD_REQUEST);

		// A line may wait for its price, as in a spreadsheet: it stays without amount.
		assertThat(post("/api/estimates/1/items", "{\"designation\": \"Forfait\", \"priceMode\": \"FIXED\"}"))
			.hasStatus(HttpStatus.CREATED)
			.bodyJson()
			.isLenientlyEqualTo("{\"incompleteItems\": 1, \"items\": [{\"unitPrice\": null, \"amount\": null}]}");
		assertThat(post("/api/estimates/1/items", "{\"designation\": \"PM\", \"priceMode\": \"TEXT\"}"))
			.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(post("/api/estimates/1/items", "{\"designation\": \"x\", \"quantity\": -1, \"priceMode\": \"COMPOSED\"}"))
			.hasStatus(HttpStatus.BAD_REQUEST);
		send("/api/estimates/1/items", SOCKET);
		assertThat(post("/api/estimate-items/1/components", """
				{"type": "SUPPLY", "label": "Câble", "quantity": 1, "unitCost": -2}""")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(post("/api/estimate-items/99/components", """
				{"type": "SUPPLY", "label": "Câble", "quantity": 1}""")).hasStatus(HttpStatus.NOT_FOUND);

		assertThat(mvc.get().uri("/api/estimates/1")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	/** A whole estimate entered as in a spreadsheet: lines with a price, no detail, pasted at once. */
	@Test
	void anEstimateIsMadeByHandWithoutAnyDetail() {
		send("/api/tenders/" + tenderId + "/estimates", "{}");
		send("/api/estimates/1/items", """
				{"reference": "1", "designation": "Tableau électrique", "unit": "u", "quantity": 1,
				 "priceMode": "FIXED", "fixedUnitPrice": 1850}""");

		// Pasted from Excel: line 1 changed, two lines added.
		assertThat(post("/api/estimates/1/items/batch", """
				{"rows": [
				 {"id": 1, "item": {"reference": "1", "designation": "Tableau électrique", "unit": "u", "quantity": 2,
				                    "priceMode": "FIXED", "fixedUnitPrice": 1850}},
				 {"id": null, "item": {"reference": "2", "designation": "Prise 2P+T", "unit": "u", "quantity": 42,
				                       "priceMode": "FIXED", "fixedUnitPrice": 21.04}},
				 {"id": null, "item": {"reference": "3", "designation": "Essais", "priceMode": "TEXT", "textPrice": "PM"}}]}"""))
			.hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{"totalHt": 4583.68, "totalVat": 916.74, "totalTtc": 5500.42, "incompleteItems": 0,
					 "totalCost": null, "margin": null,
					 "items": [{"id": 1, "amount": 3700.00}, {"position": 2, "amount": 883.68},
					           {"position": 3, "amount": 0.00}]}""");
		assertThat(post("/api/estimates/1/items/batch", "{\"rows\": [{\"id\": 99, \"item\": {\"designation\": \"x\", "
				+ "\"priceMode\": \"FIXED\"}}]}")).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM estimate_item", Long.class)).isEqualTo(3);

		// Nothing is missing: it may be validated without any detail.
		assertThat(post("/api/estimates/1/validation", "")).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("{\"status\": \"VALIDATED\"}");
	}

	/** A rectification writes the lots again: the estimate of a lot must never prevent it. */
	@Test
	void theLotsOfATenderMayStillBeWrittenAgain() {
		send("/api/tenders/" + tenderId + "/estimates", "{\"lotNumber\": \"2\"}");

		jdbc.update("DELETE FROM lot WHERE tender_id = ?", tenderId);
		jdbc.update("INSERT INTO lot (tender_id, position, number, title) VALUES (?, 1, '2', 'Électricité')", tenderId);

		assertThat(mvc.get().uri("/api/estimates/1").with(TestUsers.user())).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("{\"lotNumber\": \"2\"}");
	}

	/** A step before what is checked. */
	private void send(String uri, String body) {
		post(uri, body).exchange();
	}

	private MockMvcTester.MockMvcRequestBuilder post(String uri, String body) {
		return mvc.post().uri(uri).with(csrf()).with(TestUsers.user()).contentType(MediaType.APPLICATION_JSON)
			.content(body);
	}

}
