package eu.gpefrance.tenders.tender;

/**
 * Tabs of the tender list.
 */
public enum TenderTab {

	/** Retained by the filter, no decision yet. */
	NEW,

	TO_STUDY,

	RESPONDING,

	/** Dismissed by a user. */
	DISMISSED,

	/** Excluded by the filter, kept visible to check it does not hide good tenders. */
	FILTERED_OUT

}
