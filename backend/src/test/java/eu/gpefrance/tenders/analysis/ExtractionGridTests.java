package eu.gpefrance.tenders.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import eu.gpefrance.tenders.filter.FilterCriteria;
import eu.gpefrance.tenders.tender.MarketType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ExtractionGridTests {

	@Test
	void eachFieldHasItsOwnKey() {
		assertThat(ExtractionGrid.FIELDS).extracting(GridField::key).doesNotHaveDuplicates();
	}

	@Test
	void keysFitInTheDatabaseColumn() {
		assertThat(ExtractionGrid.FIELDS).allSatisfy(field -> assertThat(field.key()).hasSizeLessThanOrEqualTo(50));
	}

	@Test
	void onlyChoiceFieldsHaveChoices() {
		assertThat(ExtractionGrid.FIELDS).allSatisfy(field -> assertThat(field.choices().isEmpty())
			.isEqualTo(field.kind() != FieldKind.CHOICE));
	}

	@Test
	void theSchemaOfTheAnswerListsEveryKeyAndNothingElse() {
		JsonNode schema = JsonMapper.builder().build().readTree(ExtractionPrompt.outputSchema());
		JsonNode item = schema.path("properties").path("fields").path("items");

		assertThat(schema.path("additionalProperties").asBoolean(true)).isFalse();
		assertThat(item.path("additionalProperties").asBoolean(true)).isFalse();
		assertThat(item.path("properties").path("key").path("enum").valueStream().map(JsonNode::asString).toList())
			.isEqualTo(ExtractionGrid.FIELDS.stream().map(GridField::key).toList());
		assertThat(item.path("properties").path("status").path("enum").valueStream().map(JsonNode::asString).toList())
			.isEqualTo(List.of("FOUND", "NOT_INDICATED", "AMBIGUOUS", "CONTRADICTORY"));
	}

	@Test
	void theDecisionIsRequiredAndEachOfItsPropertiesIsGivenEvenNull() {
		JsonNode schema = JsonMapper.builder().build().readTree(ExtractionPrompt.outputSchema());
		JsonNode decision = schema.path("properties").path("decision");

		assertThat(schema.path("required").valueStream().map(JsonNode::asString).toList()).contains("decision");
		for (JsonNode object : List.of(decision, decision.at("/properties/strengths/items"),
				decision.at("/properties/obstacles/items"), decision.at("/properties/checks/items"))) {
			assertThat(object.path("additionalProperties").asBoolean(true)).isFalse();
			assertThat(object.path("required").valueStream().map(JsonNode::asString).toList())
				.containsExactlyInAnyOrderElementsOf(object.path("properties").propertyNames());
		}
		assertThat(decision.at("/properties/verdict/enum").valueStream().map(JsonNode::asString).toList())
			.containsExactly("PRIORITY", "EXAMINE", "POOR_FIT", "INSUFFICIENT");
	}

	@Test
	void givesTheSearchCriteriaOfTheCompanyAsWhatItLooksForNeverAsItsCapabilities() {
		String company = ExtractionPrompt.company(new FilterCriteria(List.of("74", "38"), List.of(MarketType.WORKS), null,
				List.of("4523"), List.of("voirie"), List.of(), new BigDecimal("50000"), null, Instant.now()));

		assertThat(company).startsWith("<entreprise>")
			.contains("ce qu'elle recherche, pas ce qu'elle sait faire", "Types de marché recherchés : travaux",
					"Départements recherchés : 74, 38", "Codes CPV recherchés (début de code) : 4523",
					"Mots-clés recherchés : voirie", "Montants recherchés (euros HT) : à partir de 50000",
					"Aucune information n'est enregistrée sur ses qualifications")
			.doesNotContain("Mots-clés écartés")
			.endsWith("</entreprise>\n");
		assertThat(ExtractionPrompt.company(null)).contains("Aucun critère de veille n'est enregistré");
	}

	@Test
	void theVersionNamesTheGridAndTheInstructions() {
		assertThat(ExtractionPrompt.VERSION).isEqualTo("grid-3/prompt-4");
	}

}
