package eu.gpefrance.tenders.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

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

}
