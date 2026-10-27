package eu.gpefrance.tenders.analysis;

/**
 * Progress of the background analysis of a dossier. The states of the
 * documents themselves (read, scanned, not supported) are those of
 * {@link eu.gpefrance.tenders.document.DocumentState}: the text is always
 * extracted before an analysis is asked.
 */
public enum AnalysisStatus {

	/** Queued, waiting for the worker. */
	PENDING,

	/** Sent to the provider. */
	PROCESSING,

	/** Answer read, checked and stored. */
	DONE,

	/** Failed for good: see its {@link FailureKind}. */
	FAILED,

	/** Temporary failure: tried again after its next attempt time. */
	RETRY_WAITING,

	/** Cancelled by a user before being sent. */
	CANCELLED

}
