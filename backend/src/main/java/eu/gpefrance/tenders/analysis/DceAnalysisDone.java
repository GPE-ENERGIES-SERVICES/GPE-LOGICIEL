package eu.gpefrance.tenders.analysis;

/**
 * The analysis of a consultation file is over and committed: what depends on
 * what it read (the technical choices) may start from it.
 */
public record DceAnalysisDone(Long tenderId, Long analysisId) {
}
