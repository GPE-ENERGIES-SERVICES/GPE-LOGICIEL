package eu.gpefrance.tenders.analysis;

import java.util.List;

/**
 * The opinion of the model on whether the tender deserves GPE's time, read
 * with the extraction grid in the same answer. Every quote has been looked
 * for in the cited document: an obstacle is confirmed only when its quote was
 * found there.
 * @param verdict the recommendation
 * @param conclusion why, in one or two sentences
 * @param strengths what makes the tender worth studying, at most three
 * @param obstacles what may prevent or burden an answer, most important first
 * @param checks what to check before deciding
 * @param limits what may change the recommendation
 */
public record Decision(Verdict verdict, String conclusion, List<Point> strengths, List<Point> obstacles,
		List<Check> checks, List<String> limits) {

	public enum Verdict {

		PRIORITY, EXAMINE, POOR_FIT, INSUFFICIENT

	}

	/**
	 * How sure an obstacle is.
	 */
	public enum ObstacleKind {

		/** Written in a document, with its quote found there. */
		CONFIRMED,
		/** Plausible, to be checked. */
		TO_CHECK,
		/** Missing information that prevents concluding. */
		MISSING

	}

	/**
	 * An argument or an obstacle, with its source when there is one.
	 * @param kind null for an argument
	 * @param deduced whether the model inferred it from several elements
	 * rather than read it as written
	 * @param quote the exact quote, kept only when found in the document
	 * @param documentId the cited document, when it was sent to the model
	 * @param page the page where the quote was found, null when not found
	 * @param verified whether the quote was found in the cited document
	 */
	public record Point(String text, ObstacleKind kind, boolean deduced, String quote, Long documentId, Integer page,
			boolean verified) {
	}

	/**
	 * @param documentId the document concerned, when one was sent to the model
	 */
	public record Check(String action, String reason, Long documentId) {
	}

}
