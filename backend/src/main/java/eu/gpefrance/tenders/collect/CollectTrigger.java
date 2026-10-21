package eu.gpefrance.tenders.collect;

public enum CollectTrigger {
	SCHEDULED, MANUAL,

	/** Started after a stop of the application made it miss a scheduled collection. */
	CATCH_UP
}
