package eu.gpefrance.tenders.collect.boamp;

import java.util.regex.Pattern;

/**
 * Removes markup and stray whitespace from texts published by the BOAMP.
 */
final class TextCleaner {

	private static final Pattern TAG = Pattern.compile("<[^>]+>");

	private static final Pattern SPACES = Pattern.compile("[ \\t\\x0B\\f\\u00A0]+");

	private static final Pattern BLANK_LINES = Pattern.compile("\\s*\\n\\s*");

	private TextCleaner() {
	}

	/**
	 * @return the cleaned text, or {@code null} if nothing meaningful is left
	 */
	static String clean(String text) {
		if (text == null) {
			return null;
		}
		String cleaned = TAG.matcher(text).replaceAll(" ")
			.replace("&nbsp;", " ")
			.replace("&amp;", "&")
			.replace("&lt;", "<")
			.replace("&gt;", ">")
			.replace("&quot;", "\"")
			.replace("&#39;", "'")
			.replace("\r", "");
		cleaned = SPACES.matcher(cleaned).replaceAll(" ");
		cleaned = BLANK_LINES.matcher(cleaned).replaceAll("\n").strip();
		return cleaned.isEmpty() ? null : cleaned;
	}

	/**
	 * Same as {@link #clean(String)} for a value displayed on a single line.
	 */
	static String cleanLine(String text) {
		String cleaned = clean(text);
		return cleaned == null ? null : cleaned.replace('\n', ' ');
	}

}
