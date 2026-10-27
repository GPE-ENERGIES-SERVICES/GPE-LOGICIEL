package eu.gpefrance.tenders.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

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
	void theVersionNamesTheGridAndTheInstructions() {
		assertThat(ExtractionPrompt.VERSION).isEqualTo("grid-3/prompt-3");
	}

}
