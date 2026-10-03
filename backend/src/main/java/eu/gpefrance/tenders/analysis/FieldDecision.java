package eu.gpefrance.tenders.analysis;

/**
 * What a user decided about an extracted value.
 */
public enum FieldDecision {

	/** The value is right. */
	VALIDATED,

	/** The value is replaced by the one entered by the user. */
	CORRECTED,

	/** The value is wrong and must not be used. */
	REJECTED

}
