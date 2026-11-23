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
		jdbc.execute("TRUNCATE ai_call, supplier, estimate, document_page, document, dossier, lot, tender, price_library_import "
				+ "RESTART IDENTITY CASCADE");
		jdbc.update("UPDATE pricing_coefficient SET coefficient = 1.0000");
		jdbc.update("UPDATE pricing_settings SET price_index_rate = 0.03");
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
			if (request.analysisType().equals("ESTIMATE_DRAFT")) {
				return new ModelAnswer("""
						{"postes": [{"item_id": 1,
						   "fournitures": [{"offer_id": 1, "quantite_par_unite": 1, "explication": "Prise Mosaic 16 A."},
						                   {"offer_id": 999, "quantite_par_unite": 1, "explication": "Offre inventée."}],
						   "main_oeuvre": [{"code_taux": "MONTEUR", "heures_par_unite": 0.5, "explication": "Pose encastrée."},
						                   {"code_taux": "ROBOT", "heures_par_unite": 1, "explication": "Taux inventé."}],
						   "a_verifier": "Boîte d'encastrement à prévoir."},
						  {"item_id": 9, "fournitures": [], "main_oeuvre": [], "a_verifier": "Poste inventé."}]}""", 1500, 300);
			}
			if (request.analysisType().equals(TechnicalProposals.KIND)) {
				return new ModelAnswer("""
						{"prestations": [{"item_id": 1, "type": "Prise 2P+T 16 A Mosaic", "caracteristiques": "16 A, encastrée",
						   "methode": "Pose en boîte d'encastrement.", "statut": "COMPLETE",
						   "faits": [{"texte": "Prises 16 A encastrées", "document_id": 1, "page": 1,
						              "citation": "prises de courant 16 A seront encastrées"},
						             {"texte": "Couleur blanche", "document_id": 1, "page": 1, "citation": "texte absent"}],
						   "manquant": []},
						  {"item_id": 2, "type": "Écrasé ?", "caracteristiques": "", "methode": "", "statut": "INSUFFICIENT",
						   "faits": [], "manquant": []},
						  {"item_id": 9, "type": "Inventé", "caracteristiques": "", "methode": "", "statut": "COMPLETE",
						   "faits": [], "manquant": []}]}""", 1200, 400);
			}
			if (request.analysisType().equals("ESTIMATE_PRICE")) {
				return new ModelAnswer("""
						{"choices": [{"item_id": 1, "library_id": 1, "explanation": "Même prise encastrée."},
						             {"item_id": 1, "library_id": 2, "explanation": "Second choix ignoré."},
						             {"item_id": 5, "library_id": 1, "explanation": "Poste inventé."}]}""", 700, 50);
			}
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

	/** Prices of 2024 brought to 2026 at 3 % a year: × 1.0609. */
	@Test
	void takesAPriceOfTheLibraryProposedByTheAiOrPickedByHand() {
		jdbc.update("""
				INSERT INTO price_library_import (label, file_name, price_year, item_count, imported_at)
				VALUES ('Istres', 'dpgf.xlsx', 2024, 2, now())""");
		jdbc.update("""
				INSERT INTO price_library_item (import_id, source_row, designation, unit, unit_price)
				VALUES (1, 13, '- PC 2x10/16A encastrée', 'U', 28.70), (1, 64, '* 3 x 1,5 mm²', 'ml', 2.90),
				       (1, 81, 'Interrupteur simple allumage encastré', 'u', 20.00)""");
		send("/api/estimates/1/items", "{\"designation\": \"- 3G1,5 mm²\", \"unit\": \"m\", \"quantity\": 5, "
				+ "\"priceMode\": \"FIXED\"}");
		send("/api/estimates/1/items", "{\"designation\": \"Câblage et distribution\", \"unit\": \"Ens\", \"quantity\": 1, "
				+ "\"priceMode\": \"FIXED\"}");

		// The same work found by the code, without the AI; the lump sum never priced from another job.
		assertThat(post("/api/estimates/1/library-price-suggestions")).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					[{"kind": "PRICE", "itemId": 2, "libraryItemId": 2, "price": 3.08, "model": null,
					  "text": "Istres 2024 : 2,90 € → 3,08 € actualisé Même ouvrage, même unité : trouvé par le logiciel."},
					 {"kind": "PRICE", "itemId": 1, "libraryItemId": 1, "price": 30.45,
					  "text": "Istres 2024 : 28,70 € → 30,45 € actualisé Même prise encastrée."}]""");
		// The AI chooses among the lines found by the application, without their prices, only for item 1.
		assertThat(sent.getLast().documents()).contains("PC 2x10/16A").doesNotContain("28.7").doesNotContain("3G1,5")
			.doesNotContain("Câblage");

		assertThat(post("/api/estimate-suggestions/2/acceptance")).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("{\"totalHt\": 304.50, \"items\": [{\"priceMode\": \"FIXED\", \"unitPrice\": 30.45}, "
					+ "{\"unitPrice\": null}, {\"unitPrice\": null}]}");

		assertThat(mvc.post().uri("/api/estimate-items/1/library-price").with(csrf()).with(TestUsers.user())
			.contentType(MediaType.APPLICATION_JSON).content("{\"libraryItemId\": 2}")).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("{\"items\": [{\"unitPrice\": 3.08}, {\"unitPrice\": null}, {\"unitPrice\": null}]}");
	}

	@Test
	void draftsTheItemsWithoutCostAsProposalsPricedByTheApplication() {
		jdbc.execute("DELETE FROM pricing_rate WHERE code = 'MONTEUR'");
		jdbc.update("""
				INSERT INTO pricing_rate (code, label, type, unit, cost, active, updated_at)
				VALUES ('MONTEUR', 'Monteur', 'LABOR', 'h', 30, true, now())""");

		// Only what the application gave: the offer and the rate it knows, the item it sent.
		assertThat(post("/api/estimates/1/ai-draft")).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					[{"kind": "SUPPLY", "itemId": 1, "offerId": 1, "quantity": 1.0000},
					 {"kind": "LABOR", "itemId": 1, "rateCode": "MONTEUR", "quantity": 0.5000, "text": "Pose encastrée."},
					 {"kind": "CHECK", "itemId": 1, "text": "Boîte d'encastrement à prévoir."}]""");
		// Neither the prices of the catalogue nor the cost of the rates are sent.
		assertThat(sent.getLast().documents()).contains("LEG077121", "MONTEUR").doesNotContain("3.2").doesNotContain("\"30");

		assertThat(post("/api/estimate-suggestions/2/acceptance")).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{"items": [{"components": [{"type": "LABOR", "quantity": 0.5000, "unitCost": 30.0000,
					 "rateCode": "MONTEUR"}]}]}""");
		// An item with costs is not drafted again.
		post("/api/estimate-suggestions/1/acceptance").exchange();
		assertThat(post("/api/estimates/1/ai-draft")).hasStatus(HttpStatus.BAD_REQUEST);
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

	@Test
	void proposesTheTechnicalSolutionOfEachItemWithItsSourcesAndKeepsWhatAUserDecided() {
		jdbc.update("INSERT INTO dossier (tender_id, version, status, uploaded_at) VALUES (1, 1, 'DONE', now())");
		jdbc.update("""
				INSERT INTO document (dossier_id, file_name, storage_path, size_bytes, sha256, piece_type, state, page_count)
				VALUES (1, 'CCTP.pdf', 'x/CCTP.pdf', 1, 'x', 'CCTP', 'READ', 1),
				       (1, 'DPGF.xlsx', 'x/DPGF.xlsx', 1, 'y', 'DPGF', 'READ', 1)""");
		jdbc.update("""
				INSERT INTO document_page (document_id, page_number, text, method) VALUES
				(1, 1, 'Article 4 - Les prises de courant 16 A seront encastrées.', 'NATIVE'),
				(2, 1, 'Tableau des prix', 'NATIVE')""");
		send("/api/estimates/1/items", "{\"designation\": \"Câble AC\", \"unit\": \"ml\", \"quantity\": 50, \"priceMode\": \"COMPOSED\"}");
		// Written by the estimator: the AI never changes it.
		assertThat(mvc.put().uri("/api/estimate-items/2/technical").with(csrf()).with(TestUsers.user())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"type\": \"Câble U1000 R2V 5G16\", \"status\": \"TO_CONFIRM\", \"missing\": [\"Longueur à confirmer\"]}"))
			.hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("{\"items\": [{\"id\": 1}, {\"id\": 2, \"technical\": {\"type\": \"Câble U1000 R2V 5G16\", "
					+ "\"origin\": \"USER\", \"missing\": [\"Longueur à confirmer\"]}}]}");

		assertThat(post("/api/estimates/1/technical-proposals")).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{"proposed": 1, "estimate": {"items": [
					  {"id": 1, "technical": {"type": "Prise 2P+T 16 A Mosaic", "origin": "AI", "status": "TO_CONFIRM",
					   "facts": [{"text": "Prises 16 A encastrées", "fileName": "CCTP.pdf", "page": 1}],
					   "missing": ["À confirmer : Couleur blanche"]}},
					  {"id": 2, "technical": {"type": "Câble U1000 R2V 5G16", "origin": "USER"}}]}}""");
		// The specifications are read, never the price schedule whose lines are the items.
		assertThat(sent.getLast().documents()).contains("CCTP.pdf", "Prise de courant 2P+T").doesNotContain("Tableau des prix");

		assertThat(post("/api/estimate-items/1/technical/validation")).hasStatusOk()
			.bodyJson()
			.extractingPath("$.items[0].technical.validatedBy")
			.isNotNull();
		assertThat(post("/api/estimates/1/technical-proposals")).hasStatus(HttpStatus.BAD_REQUEST);

		// The resources proposed next follow the solution retained.
		post("/api/estimates/1/ai-draft").exchange();
		assertThat(sent.getLast().documents()).contains("solution_technique", "Prise 2P+T 16 A Mosaic");
	}

	private MockMvcTester.MockMvcRequestBuilder post(String uri) {
		return mvc.post().uri(uri).with(csrf()).with(TestUsers.user());
	}

	private void send(String uri, String body) {
		mvc.post().uri(uri).with(csrf()).with(TestUsers.user()).contentType(MediaType.APPLICATION_JSON).content(body)
			.exchange();
	}

}
