package eu.gpefrance.tenders.tender;

/**
 * Views of the tenders. The open ones are split from NEW to FILTERED_OUT; the
 * followed and the past ones have their own sections. A tender excluded by the
 * filter stays out of the other views until a user takes a decision on it.
 */
public enum TenderTab {

	/** Retained by the filter, no decision yet, open. */
	NEW,

	/** To analyse or in preparation, open. */
	TO_PROCESS,

	/** Followed by the logged-in user, open or past. */
	FOLLOWED,

	/** Open, except the tenders excluded by the filter without a decision. */
	ALL,

	/** Dismissed by a user, open. */
	DISMISSED,

	/** Closed, cancelled or abandoned. */
	ARCHIVED,

	/** Excluded by the filter, no decision yet, open: to check the filter does not hide good tenders. */
	FILTERED_OUT

}
