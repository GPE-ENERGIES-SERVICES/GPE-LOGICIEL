package eu.gpefrance.tenders.filter;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Finds keywords in a text regardless of case, accents and punctuation. Each
 * word of a keyword matches the beginning of a word, in the same order: "borne
 * de recharge" finds "Bornes de recharge" and "photovolt" finds
 * "photovoltaïque", but "eau" does not find "réseau".
 */
final class TextMatcher {

	private static final Pattern MARKS = Pattern.compile("\\p{M}+");

	private static final Pattern SEPARATORS = Pattern.compile("[^a-z0-9]+");

	private final String[] words;

	TextMatcher(String text) {
		this.words = words(text);
	}

	boolean contains(String keyword) {
		String[] searched = words(keyword);
		if (searched.length == 0) {
			return false;
		}
		for (int start = 0; start + searched.length <= words.length; start++) {
			if (matchesAt(start, searched)) {
				return true;
			}
		}
		return false;
	}

	private boolean matchesAt(int start, String[] searched) {
		for (int i = 0; i < searched.length; i++) {
			if (!words[start + i].startsWith(searched[i])) {
				return false;
			}
		}
		return true;
	}

	private static String[] words(String value) {
		if (value == null) {
			return new String[0];
		}
		String withoutAccents = MARKS.matcher(Normalizer.normalize(value, Normalizer.Form.NFD)).replaceAll("");
		String normalized = SEPARATORS.matcher(withoutAccents.toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
		return normalized.isEmpty() ? new String[0] : normalized.split(" ");
	}

}
