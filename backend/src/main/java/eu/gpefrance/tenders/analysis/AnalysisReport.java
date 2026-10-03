package eu.gpefrance.tenders.analysis;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Journal and quality of the analyses, for the administrator: what each
 * analysis cost and, from the decisions of the users, how often the model
 * was right.
 */
public final class AnalysisReport {

	private AnalysisReport() {
	}

	public record JournalEntry(Long id, Long tenderId, String tenderTitle, int dossierVersion, AnalysisStatus status,
			String model, String promptVersion, Instant createdAt, Instant finishedAt, String errorMessage,
			Integer inputTokens, Integer outputTokens, BigDecimal cost) {
	}

	/**
	 * @param averageCost average cost of a finished analysis, null when prices
	 * are not set
	 */
	public record Quality(long done, long failed, BigDecimal totalCost, BigDecimal averageCost,
			List<FieldQuality> fields) {
	}

	/**
	 * Answers to one field of the grid in the finished analyses.
	 * @param verified answers whose quote was found in the document
	 * @param validated answers confirmed by a user
	 * @param corrected answers a user had to correct
	 * @param rejected answers a user rejected
	 */
	public record FieldQuality(String key, String label, long answers, long verified, long notIndicated,
			long validated, long corrected, long rejected) {
	}

}
