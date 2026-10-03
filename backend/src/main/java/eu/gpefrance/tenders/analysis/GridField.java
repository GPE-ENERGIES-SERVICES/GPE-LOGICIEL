package eu.gpefrance.tenders.analysis;

import java.util.List;

/**
 * One information of the extraction grid.
 * @param key stable identifier, stored with each extracted value
 * @param label name shown on the summary sheet
 * @param section where the information appears on the summary sheet
 * @param kind expected form of the value
 * @param multiple whether the answer is a list, each item with its own quote
 * @param question what the model must look for
 * @param choices allowed answers of a {@link FieldKind#CHOICE} field
 */
public record GridField(String key, String label, SummarySection section, FieldKind kind, boolean multiple,
		String question, List<String> choices) {

	static GridField single(String key, String label, SummarySection section, FieldKind kind, String question) {
		return new GridField(key, label, section, kind, false, question, List.of());
	}

	static GridField list(String key, String label, SummarySection section, String question) {
		return new GridField(key, label, section, FieldKind.TEXT, true, question, List.of());
	}

	static GridField choice(String key, String label, SummarySection section, String question,
			List<String> choices) {
		return new GridField(key, label, section, FieldKind.CHOICE, false, question, choices);
	}

}
