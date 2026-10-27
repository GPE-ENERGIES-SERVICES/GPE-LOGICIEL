package eu.gpefrance.tenders.analysis;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Journal and quality of the analyses, for the administrator: what each
 * analysis cost and, from the decisions of the users, how often the model
 * was right.
 */
public final class AnalysisReport {

	private AnalysisReport() {
	}

	public record JournalEntry(Long id, Long tenderId, String tenderTitle, int dossierVersion, AnalysisStatus status,
			String provider, String model, String promptVersion, FailureKind errorKind, int attempts,
			Instant createdAt, Instant finishedAt, String errorMessage, Integer inputTokens, Integer outputTokens,
			BigDecimal cost) {
	}

	/**
	 * Configuration of the AI analysis as the server applies it. Never holds
	 * a key nor a header: the secrets stay in the environment of the server.
	 * @param state see {@link AnalysisGate.State}
	 * @param enabled {@code analysis.enabled}
	 * @param provider {@code analysis.provider}, "none" by default
	 * @param model the model of the provider, when it has one
	 * @param externalTransmissionAllowed {@code analysis.external-transmission-allowed}
	 * @param availableProviders the providers present in the application
	 * @param problems why the configuration is inconsistent
	 * @param limits limits of size, concurrency, attempts and cost
	 * @param queue analyses by status
	 * @param monthToDate tokens and estimated cost of the current month
	 */
	public record Configuration(AnalysisGate.State state, String message, boolean enabled, String provider,
			String model, boolean externalTransmissionAllowed, List<String> availableProviders, List<String> problems,
			Limits limits, Map<AnalysisStatus, Long> queue, Consumption monthToDate) {
	}

	/**
	 * @param monthlyBudget ceiling of the month in euros, null for none
	 */
	public record Limits(int maxCharacters, int maxDocumentsPerTender, int maxOutputTokens, int maxConcurrentRequests,
			int maxAttempts, long requestTimeoutSeconds, BigDecimal monthlyBudget, boolean pricesSet) {
	}

	/**
	 * @param estimatedCost tokens counted by GPE times the configured prices:
	 * an estimate, not the invoice of the provider; null when prices are not
	 * set
	 */
	public record Consumption(long inputTokens, long outputTokens, BigDecimal estimatedCost) {
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
