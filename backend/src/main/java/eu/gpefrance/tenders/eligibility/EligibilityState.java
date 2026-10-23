package eu.gpefrance.tenders.eligibility;

/**
 * Whether the consultation file of a tender may be retrieved, by the saved
 * searches.
 */
public enum EligibilityState {

	/** The tender is open and matches at least one active saved search. */
	ELIGIBLE,

	/** The tender is not open, or matches no active saved search, or there is none. */
	NOT_ELIGIBLE,

	/**
	 * No active saved search matches, but at least one cannot be decided: the
	 * tender lacks what it needs (department, CPV code, amount...) or it uses
	 * a criterion not evaluated yet (types of works). Nothing is retrieved.
	 */
	INDETERMINATE

}
