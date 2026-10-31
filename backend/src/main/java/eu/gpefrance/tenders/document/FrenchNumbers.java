package eu.gpefrance.tenders.document;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Reads a number written as text, the French way or not: "1 234,56 €",
 * "1.234,56", "1234.56", "-12,5", "1 500 € HT". A value that is not clearly
 * one number (an article number "1.2.3", "PM", "inclus", a percentage, a
 * date) is not a number.
 */
public final class FrenchNumbers {

	private static final Pattern SPACES = Pattern.compile("[\\s\\u00A0\\u202F\\u2009']+");

	private static final Pattern CURRENCY = Pattern.compile("(?i)(€|eur(os?)?)?\\s*(h\\.?t\\.?|t\\.?t\\.?c\\.?)?$");

	private static final Pattern DOT_THOUSANDS = Pattern.compile("-?\\d{1,3}(\\.\\d{3}){2,}");

	private static final Pattern COMMA_THOUSANDS = Pattern.compile("-?\\d{1,3}(,\\d{3}){2,}");

	private static final Pattern PLAIN = Pattern.compile("-?\\d+(\\.\\d+)?");

	private FrenchNumbers() {
	}

	/**
	 * @return the number, or null when the text is not clearly one number
	 */
	public static BigDecimal parse(String text) {
		if (text == null) {
			return null;
		}
		String value = text.strip();
		if (value.isEmpty() || value.length() > 40) {
			return null;
		}
		value = CURRENCY.matcher(value).replaceFirst("").strip();
		value = SPACES.matcher(value).replaceAll("");
		if (value.startsWith("(") && value.endsWith(")")) {
			value = "-" + value.substring(1, value.length() - 1);
		}
		value = value.replace('−', '-');
		if (value.isEmpty()) {
			return null;
		}
		int comma = value.lastIndexOf(',');
		int dot = value.lastIndexOf('.');
		if (comma >= 0 && dot >= 0) {
			// The last separator is the decimal one: 1.234,56 or 1,234.56.
			value = comma > dot ? value.replace(".", "").replace(',', '.') : value.replace(",", "");
		}
		else if (comma >= 0) {
			value = COMMA_THOUSANDS.matcher(value).matches() ? value.replace(",", "") : value.replace(',', '.');
		}
		else if (DOT_THOUSANDS.matcher(value).matches()) {
			value = value.replace(".", "");
		}
		if (!PLAIN.matcher(value).matches()) {
			return null;
		}
		return new BigDecimal(value);
	}

}
