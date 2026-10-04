package eu.gpefrance.tenders.tender;

/**
 * Step of the company's work on a tender, shared by the team. Independent of
 * the life cycle of the tender itself ({@link TenderStatus}).
 */
public enum FollowUpStatus {

	NEW,

	/** To analyse. */
	TO_STUDY,

	/** Answer in preparation. */
	RESPONDING,

	/** Answer sent to the buyer. */
	SUBMITTED,

	DISMISSED

}
