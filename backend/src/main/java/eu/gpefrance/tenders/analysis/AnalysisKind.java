package eu.gpefrance.tenders.analysis;

/**
 * What an analysis is for. Each kind has its own instructions, its own result
 * and its own queue position: a tender has at most one unfinished analysis of
 * each kind.
 */
public enum AnalysisKind {

	/** The summary sheet of the consultation file, with the decision help. */
	DCE_SUMMARY,

	/** The price schedules (DPGF, BPU, DQE), asked for by a user only. */
	FINANCIAL

}
