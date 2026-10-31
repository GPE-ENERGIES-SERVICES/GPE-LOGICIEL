package eu.gpefrance.tenders.estimate;

public enum EstimateStatus {

	/** Being written: the only status that may be changed. */
	DRAFT,

	/** Validated by a user: frozen. */
	VALIDATED,

	/** Replaced by a later version: frozen. */
	SUPERSEDED

}
