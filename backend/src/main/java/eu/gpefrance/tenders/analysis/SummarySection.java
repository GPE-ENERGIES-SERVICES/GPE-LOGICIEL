package eu.gpefrance.tenders.analysis;

/**
 * Sections of the summary sheet, in the order of the questions asked before
 * deciding to answer a tender.
 */
public enum SummarySection {

	/** Deadline, visit, lots, budget, duration. */
	AT_A_GLANCE,

	/** Remarkable or restrictive points noted in the documents. */
	ATTENTION,

	/** Subject, buyer, place, procedure. */
	REQUESTED,

	/** Criteria, documents to provide, qualifications, submission. */
	TO_ANSWER

}
