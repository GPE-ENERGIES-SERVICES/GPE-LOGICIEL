package eu.gpefrance.tenders.analysis;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks, with plain code and no AI, that a quote given by the model is really
 * in the cited document and that the value can be read in it. Comparisons
 * ignore case, accents, spaces and punctuation, which PDF extraction often
 * alters.
 */
final class CitationVerifier {

	/** Shorter quotes would be found almost anywhere. */
	private static final int MIN_QUOTE_LENGTH = 5;

	private static final Pattern ELLIPSIS = Pattern.compile("\\s*[\\[(]?(?:\\.{3,}|…)[\\])]?\\s*");

	private static final Pattern AMOUNT = Pattern
		.compile("\\d{1,3}(?:[ .\\u00A0\\u202F]\\d{3})+(?:,\\d{1,2})?|\\d+(?:,\\d{1,2})?");

	private static final String[] MONTHS = { "janvier", "fevrier", "mars", "avril", "mai", "juin", "juillet", "aout",
			"septembre", "octobre", "novembre", "decembre" };

	private CitationVerifier() {
	}

	/**
	 * Looks for the quote in the cited page, then across the break with the
	 * next page, then anywhere else in the document.
	 * @param pages text of each page of the document, by page number
	 * @return the page where the quote starts, empty when it is not found
	 */
	static Optional<Integer> locate(String quote, Map<Integer, String> pages, Integer citedPage) {
		List<String> parts = parts(quote);
		if (parts.stream().mapToInt(String::length).sum() < MIN_QUOTE_LENGTH) {
			return Optional.empty();
		}
		if (citedPage != null && pages.containsKey(citedPage)) {
			String page = compact(pages.get(citedPage));
			if (containsInOrder(page, parts)
					|| containsInOrder(page + compact(pages.getOrDefault(citedPage + 1, "")), parts)) {
				return Optional.of(citedPage);
			}
		}
		return pages.entrySet()
			.stream()
			.sorted(Map.Entry.comparingByKey())
			.filter(page -> containsInOrder(compact(page.getValue()), parts))
			.map(Map.Entry::getKey)
			.findFirst();
	}

	/**
	 * Whether the value can be read in the quote. Free text and choices are
	 * interpretations: only their quote is checked.
	 */
	static boolean valueMatches(FieldKind kind, String value, String quote) {
		if (value == null || quote == null) {
			return false;
		}
		return switch (kind) {
			case DATE, DATE_TIME -> dateMatches(value.trim(), words(quote));
			case AMOUNT -> amountMatches(value, quote);
			case TEXT, CHOICE -> true;
		};
	}

	private static boolean dateMatches(String value, String quote) {
		LocalDate date;
		LocalTime time = null;
		try {
			date = LocalDate.parse(value.substring(0, Math.min(10, value.length())));
			if (value.length() > 10) {
				time = LocalTime.parse(value.substring(11));
			}
		}
		catch (DateTimeParseException | StringIndexOutOfBoundsException ex) {
			return false;
		}
		return containsDate(quote, date) && (time == null || containsTime(quote, time));
	}

	private static boolean containsDate(String quote, LocalDate date) {
		String day = "0?" + date.getDayOfMonth();
		String month = "0?" + date.getMonthValue();
		String year = "(?:" + date.getYear() + "|" + date.getYear() % 100 + ")";
		String monthName = MONTHS[date.getMonthValue() - 1];
		return find(quote, "\\b" + day + " " + month + " " + year + "\\b")
				|| find(quote, "\\b" + date.getYear() + " " + month + " " + day + "\\b")
				|| find(quote, "\\b" + day + "(?: ?er)? " + monthName + "(?: " + date.getYear() + ")?\\b");
	}

	private static boolean containsTime(String quote, LocalTime time) {
		String hour = "0?" + time.getHour();
		if (time.getMinute() == 0) {
			return find(quote, "\\b" + hour + " ?(?:h|heures?)(?: ?00)?(?! ?\\d)")
					|| find(quote, "\\b" + hour + " 00\\b") || (time.getHour() == 12 && find(quote, "\\bmidi\\b"));
		}
		return find(quote, "\\b" + hour + " ?(?:h|heures?)? ?" + String.format("%02d", time.getMinute()) + "\\b");
	}

	private static boolean amountMatches(String value, String quote) {
		BigDecimal expected;
		try {
			expected = new BigDecimal(value.replaceAll("[\\s\\u00A0\\u202F€]", "").replace(',', '.'));
		}
		catch (NumberFormatException ex) {
			return false;
		}
		Matcher matcher = AMOUNT.matcher(quote);
		while (matcher.find()) {
			String digits = matcher.group().replaceAll("[ .\\u00A0\\u202F]", "").replace(',', '.');
			if (new BigDecimal(digits).compareTo(expected) == 0) {
				return true;
			}
		}
		return false;
	}

	private static boolean find(String text, String regex) {
		return Pattern.compile(regex).matcher(text).find();
	}

	/**
	 * A quote may skip words with an ellipsis: each part must be found, in
	 * order.
	 */
	private static List<String> parts(String quote) {
		List<String> parts = new ArrayList<>();
		if (quote == null) {
			return parts;
		}
		for (String part : ELLIPSIS.split(quote)) {
			String compact = compact(part);
			if (!compact.isEmpty()) {
				parts.add(compact);
			}
		}
		return parts;
	}

	private static boolean containsInOrder(String text, List<String> parts) {
		int from = 0;
		for (String part : parts) {
			int index = text.indexOf(part, from);
			if (index < 0) {
				return false;
			}
			from = index + part.length();
		}
		return true;
	}

	/** Letters and digits only, lower case, without accents. */
	static String compact(String text) {
		return stripAccents(text).replaceAll("[^a-z0-9]", "");
	}

	/** Words and numbers separated by single spaces, lower case, without accents. */
	static String words(String text) {
		return stripAccents(text).replaceAll("[^a-z0-9]+", " ").trim();
	}

	private static String stripAccents(String text) {
		return Normalizer.normalize(text, Normalizer.Form.NFKD)
			.replaceAll("\\p{M}", "")
			.replace("­", "")
			.toLowerCase(Locale.ROOT);
	}

}
