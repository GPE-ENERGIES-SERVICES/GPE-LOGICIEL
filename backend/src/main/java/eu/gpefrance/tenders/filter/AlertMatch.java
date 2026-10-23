package eu.gpefrance.tenders.filter;

/**
 * Result of a saved search on one tender.
 * @param reason the criteria met, failed or impossible to evaluate
 */
public record AlertMatch(Outcome outcome, String reason) {

	public enum Outcome {

		/** Every criterion holds. */
		MATCH,

		/** At least one criterion fails. */
		NO_MATCH,

		/** No criterion fails, but the tender lacks what one of them needs. */
		INDETERMINATE

	}

}
