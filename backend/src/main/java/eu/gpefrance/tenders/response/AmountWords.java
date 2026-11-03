package eu.gpefrance.tenders.response;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * An amount in euros written in letters, as the forms ask it.
 */
final class AmountWords {

	private static final String[] UNITS = { "", "un", "deux", "trois", "quatre", "cinq", "six", "sept", "huit", "neuf",
			"dix", "onze", "douze", "treize", "quatorze", "quinze", "seize", "dix-sept", "dix-huit", "dix-neuf" };

	private static final String[] TENS = { "", "dix", "vingt", "trente", "quarante", "cinquante", "soixante", "soixante",
			"quatre-vingt", "quatre-vingt" };

	private AmountWords() {
	}

	static String euros(BigDecimal amount) {
		BigDecimal rounded = amount.setScale(2, RoundingMode.HALF_UP);
		long euros = rounded.longValue();
		int cents = rounded.subtract(BigDecimal.valueOf(euros)).movePointRight(2).intValue();
		String words = number(euros) + (euros > 1 ? " euros" : " euro");
		return cents == 0 ? words : words + " et " + number(cents) + (cents > 1 ? " centimes" : " centime");
	}

	private static String number(long value) {
		if (value == 0) {
			return "zéro";
		}
		StringBuilder words = new StringBuilder();
		long millions = value / 1_000_000;
		long thousands = value / 1_000 % 1_000;
		int rest = (int) (value % 1_000);
		if (millions > 0) {
			words.append(hundreds((int) millions, false)).append(millions > 1 ? " millions" : " million");
		}
		if (thousands > 0) {
			if (words.length() > 0) {
				words.append(' ');
			}
			words.append(thousands == 1 ? "mille" : hundreds((int) thousands, true) + " mille");
		}
		if (rest > 0) {
			if (words.length() > 0) {
				words.append(' ');
			}
			words.append(hundreds(rest, false));
		}
		return words.toString();
	}

	/**
	 * @param beforeThousand « deux cent mille », « quatre-vingt mille »: no final s
	 */
	private static String hundreds(int value, boolean beforeThousand) {
		int hundreds = value / 100;
		int rest = value % 100;
		StringBuilder words = new StringBuilder();
		if (hundreds > 0) {
			words.append(hundreds == 1 ? "cent" : UNITS[hundreds] + " cent");
			if (hundreds > 1 && rest == 0 && !beforeThousand) {
				words.append('s');
			}
		}
		if (rest > 0) {
			if (words.length() > 0) {
				words.append(' ');
			}
			words.append(belowHundred(rest, beforeThousand));
		}
		return words.toString();
	}

	private static String belowHundred(int value, boolean beforeThousand) {
		if (value < 20) {
			return UNITS[value];
		}
		int ten = value / 10;
		int unit = value % 10;
		if (ten == 7 || ten == 9) {
			int low = value - (ten == 7 ? 60 : 80);
			return TENS[ten] + (ten == 7 && low == 11 ? " et " : "-") + UNITS[low];
		}
		if (unit == 0) {
			return TENS[ten] + (ten == 8 && !beforeThousand ? "s" : "");
		}
		return TENS[ten] + (unit == 1 && ten != 8 ? " et " : "-") + UNITS[unit];
	}

}
