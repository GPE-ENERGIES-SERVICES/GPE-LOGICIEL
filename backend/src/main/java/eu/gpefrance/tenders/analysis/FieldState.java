package eu.gpefrance.tenders.analysis;

public enum FieldState {

	/** The quote was found in the cited page and contains the value. */
	VERIFIED,

	/** A value was given but its quote has not been found in the cited page. */
	UNVERIFIED,

	/** The information is not in the analysed documents. */
	NOT_INDICATED,

	/** The documents allow several readings. */
	AMBIGUOUS,

	/** The documents give different values. */
	CONTRADICTORY

}
