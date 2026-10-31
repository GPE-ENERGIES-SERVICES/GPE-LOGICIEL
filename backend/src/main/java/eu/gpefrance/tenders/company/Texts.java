package eu.gpefrance.tenders.company;

import java.util.List;

/** Text as typed in a form: blank is no value. */
final class Texts {

	private Texts() {
	}

	static String clean(String text) {
		return text == null || text.isBlank() ? null : text.strip();
	}

	static List<String> list(List<String> values) {
		return values == null ? List.of()
				: values.stream().map(String::strip).filter(value -> !value.isEmpty()).distinct().toList();
	}

}
