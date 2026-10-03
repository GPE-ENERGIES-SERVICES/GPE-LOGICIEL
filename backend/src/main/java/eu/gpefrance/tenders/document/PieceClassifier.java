package eu.gpefrance.tenders.document;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Guesses the kind of document from its file name. The user can correct it.
 */
final class PieceClassifier {

	private static final Pattern MARKS = Pattern.compile("\\p{M}+");

	private static final Pattern SEPARATORS = Pattern.compile("[^a-z0-9]+");

	/** Checked in order: the first match wins. */
	private static final List<Rule> RULES = List.of(
			new Rule(PieceType.CCTP, "(^| )cctp( |$)|clauses techniques"),
			new Rule(PieceType.CCAP, "(^| )ccap( |$)|clauses administratives"),
			new Rule(PieceType.RC, "(^| )r ?c( |$)|reglement (de |du )?(la )?consultation"),
			new Rule(PieceType.DPGF, "(^| )dpgf( |$)|decomposition du prix"),
			new Rule(PieceType.BPU, "(^| )bpu( |$)|bordereau (des )?prix"),
			new Rule(PieceType.DQE, "(^| )dqe( |$)|detail quantitatif"),
			new Rule(PieceType.AE, "(^| )ae( |$)|acte d ?engagement|(^| )attri ?1( |$)"),
			new Rule(PieceType.NOTICE, "(^| )(avis|aapc)( |$)"),
			new Rule(PieceType.PLAN, "(^| )plans?( |$)|(^| )(dwg|dxf)$"));

	private PieceClassifier() {
	}

	/**
	 * @param fileName the name of the file, possibly with its folders in the
	 * archive (only the file name is used)
	 */
	static PieceType classify(String fileName) {
		String name = fileName.replace('\\', '/');
		name = name.substring(name.lastIndexOf('/') + 1);
		String withoutAccents = MARKS.matcher(Normalizer.normalize(name, Normalizer.Form.NFD)).replaceAll("");
		String normalized = SEPARATORS.matcher(withoutAccents.toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
		for (Rule rule : RULES) {
			if (rule.pattern().matcher(normalized).find()) {
				return rule.type();
			}
		}
		return PieceType.OTHER;
	}

	private record Rule(PieceType type, Pattern pattern) {

		Rule(PieceType type, String regex) {
			this(type, Pattern.compile(regex));
		}

	}

}
