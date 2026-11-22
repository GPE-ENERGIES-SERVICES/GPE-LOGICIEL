package eu.gpefrance.tenders.estimate;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Designations of works compared by plain code: the same work written two
 * ways (« Inter SA encastré », « Interrupteur simple allumage encastré »)
 * gives the same key, so that a price of the library is found without the AI.
 */
final class Designations {

	/** Abbreviations of the trade, written out. */
	private static final Map<String, String> ABBREVIATIONS = Map.ofEntries(Map.entry("pc", "prise courant"),
			Map.entry("pdc", "prise courant"), Map.entry("bp", "bouton poussoir"), Map.entry("inter", "interrupteur"),
			Map.entry("int", "interrupteur"), Map.entry("sa", "simple allumage"), Map.entry("va", "vaetvient"),
			Map.entry("vv", "vaetvient"), Map.entry("cdc", "chemin cable"),
			Map.entry("td", "tableau divisionnaire"), Map.entry("ecl", "eclairage"), Map.entry("lum", "luminaire"),
			Map.entry("det", "detecteur"));

	private static final Set<String> STOP_WORDS = Set.of("de", "du", "d", "la", "le", "l", "les", "des", "et", "en",
			"a", "au", "aux", "pour", "avec", "y", "compris", "fourniture", "pose", "raccordement", "mise", "oeuvre",
			"sur", "par", "un", "une");

	/** « 3 x 1,5 », « 3G1,5 », « 3 G 1.5 » : the same section of cable. */
	private static final Pattern SECTION = Pattern.compile("(\\d+)\\s*[xg]\\s*(\\d+(?:[,.]\\d+)?)");

	private static final Pattern MARKS = Pattern.compile("\\p{M}+");

	private Designations() {
	}

	/** The words of a designation, without order, accents, plurals nor abbreviations. */
	static String key(String designation) {
		if (designation == null) {
			return "";
		}
		String text = MARKS.matcher(Normalizer.normalize(designation, Normalizer.Form.NFD)).replaceAll("")
			.toLowerCase(Locale.ROOT)
			.replace("va-et-vient", "vaetvient")
			.replace("va et vient", "vaetvient");
		text = SECTION.matcher(text).replaceAll(match -> match.group(1) + "x" + match.group(2).replace('.', ','));
		TreeSet<String> words = new TreeSet<>();
		for (String raw : text.split("[^a-z0-9,/+]+")) {
			String word = raw.replaceAll("^[,/+]+|[,/+]+$", "");
			if (word.isEmpty()) {
				continue;
			}
			for (String part : ABBREVIATIONS.getOrDefault(word, word).split(" ")) {
				String singular = part.length() > 3 && part.endsWith("s") ? part.substring(0, part.length() - 1) : part;
				if (!STOP_WORDS.contains(singular)) {
					words.add(singular);
				}
			}
		}
		return String.join(" ", words);
	}

	/** « Luminaire type 6 », « repère A », « modèle B » : a reference of one project, defined in its CCTP. */
	private static final Pattern PROJECT_REFERENCE = Pattern
		.compile("(?i)\\btype\\s*[a-z0-9-]{1,6}\\b|\\brep[eè]re\\b|\\bmod[eè]le\\s*[a-z0-9]{1,3}\\b");

	/**
	 * A designation naming a reference of its project: the same words in
	 * another job are another product (Istres: « Luminaire type 6 » priced
	 * 3 times its real price from another job).
	 */
	static boolean isProjectReference(String designation) {
		return designation != null && PROJECT_REFERENCE.matcher(designation).find();
	}

	/**
	 * A key precise enough for the code to decide alone: it holds a figure
	 * (section, rating, IP, power). « Déclencheur manuel » is not: the same
	 * words were priced 38 € in one job and 97 € in another.
	 */
	static boolean isPrecise(String key) {
		return key.matches(".*\\d.*");
	}

	/** U, unité, pce... : one unit; ml, m : the metre; m2, m² : the square metre. */
	static String unit(String unit) {
		if (unit == null) {
			return "";
		}
		String plain = MARKS.matcher(Normalizer.normalize(unit, Normalizer.Form.NFD)).replaceAll("")
			.toLowerCase(Locale.ROOT)
			.replaceAll("[^a-z0-9²]", "");
		return switch (plain) {
			case "u", "un", "unite", "pce", "piece", "nb", "nbre" -> "u";
			case "m", "ml", "metre", "mlineaire" -> "ml";
			case "m2", "m²", "mc" -> "m2";
			default -> plain;
		};
	}

}
