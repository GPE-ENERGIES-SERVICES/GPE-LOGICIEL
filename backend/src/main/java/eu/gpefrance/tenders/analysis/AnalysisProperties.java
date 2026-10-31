package eu.gpefrance.tenders.analysis;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * AI analysis of the consultation files. Everything is off by default: no
 * analysis can start, nothing is sent and nothing is spent until
 * {@code enabled}, {@code provider} and {@code external-transmission-allowed}
 * are all set and the provider has its key. Values out of range stop the
 * start; an inconsistent combination only keeps the analysis off, with the
 * reason shown to the administrator ({@link AnalysisGate}).
 * @param enabled master switch of the AI analysis; false by default
 * @param provider identifier of the {@link ExtractionClient} to use; "none"
 * by default
 * @param externalTransmissionAllowed whether the documents may be sent to an
 * external provider: a key alone never allows it; false by default
 * @param maxCharacters maximum text sent to the model for one analysis;
 * documents that do not fit are listed as not analysed, never cut
 * @param maxDocumentsPerTender documents at most sent for one analysis
 * @param maxOutputTokens longest answer asked from the model
 * @param maxConcurrentRequests calls to the provider at the same time
 * @param maxAttempts attempts at most of an analysis, on temporary errors
 * @param retryDelay wait after the first temporary error, doubled at each
 * following one, with a random part (jitter)
 * @param maxRetryDelay longest wait between two attempts
 * @param requestTimeout longest wait for one answer, given to the provider
 * @param lease time after which an analysis left in progress (stop, hang) is
 * released
 * @param pollInterval wait between two looks at the queue
 * @param monthlyBudget ceiling in euros of the analyses started in a calendar
 * month (Paris), estimated locally from the tokens and the prices below; empty
 * for none. It is not a ceiling of the invoice of the provider: set one there
 * too.
 * @param inputPricePerMillion price of one million input tokens, in euros
 * @param outputPricePerMillion price of one million output tokens, in euros
 */
@ConfigurationProperties("analysis")
public record AnalysisProperties(@DefaultValue("false") boolean enabled, @DefaultValue("none") String provider,
		@DefaultValue("false") boolean externalTransmissionAllowed, @DefaultValue("400000") int maxCharacters,
		@DefaultValue("30") int maxDocumentsPerTender, @DefaultValue("8000") int maxOutputTokens,
		@DefaultValue("1") int maxConcurrentRequests, @DefaultValue("3") int maxAttempts,
		@DefaultValue("2m") Duration retryDelay, @DefaultValue("1h") Duration maxRetryDelay,
		@DefaultValue("5m") Duration requestTimeout, @DefaultValue("15m") Duration lease,
		@DefaultValue("30s") Duration pollInterval, BigDecimal monthlyBudget,
		@DefaultValue("0") BigDecimal inputPricePerMillion, @DefaultValue("0") BigDecimal outputPricePerMillion) {

	public static final String NO_PROVIDER = "none";

	private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

	public AnalysisProperties {
		provider = provider == null || provider.isBlank() ? NO_PROVIDER : provider.strip().toLowerCase(Locale.ROOT);
		if (maxCharacters < 1 || maxDocumentsPerTender < 1 || maxOutputTokens < 1 || maxConcurrentRequests < 1
				|| maxAttempts < 1) {
			throw new IllegalArgumentException("analysis: max-characters, max-documents-per-tender, "
					+ "max-output-tokens, max-concurrent-requests and max-attempts must be at least 1");
		}
		if (!positive(retryDelay) || !positive(maxRetryDelay) || !positive(requestTimeout) || !positive(lease)
				|| !positive(pollInterval)) {
			throw new IllegalArgumentException("analysis: delays must be positive");
		}
		if (lease.compareTo(requestTimeout) <= 0) {
			throw new IllegalArgumentException("analysis.lease must be longer than analysis.request-timeout");
		}
		if (monthlyBudget != null && monthlyBudget.signum() < 0 || inputPricePerMillion.signum() < 0
				|| outputPricePerMillion.signum() < 0) {
			throw new IllegalArgumentException("analysis: budget and prices may not be negative");
		}
	}

	private static boolean positive(Duration duration) {
		return duration != null && !duration.isNegative() && !duration.isZero();
	}

	boolean pricesSet() {
		return inputPricePerMillion.signum() > 0 || outputPricePerMillion.signum() > 0;
	}

	/**
	 * @return the cost in euros, or null when the prices are not set
	 */
	BigDecimal cost(long inputTokens, long outputTokens) {
		if (!pricesSet()) {
			return null;
		}
		return inputPricePerMillion.multiply(BigDecimal.valueOf(inputTokens))
			.add(outputPricePerMillion.multiply(BigDecimal.valueOf(outputTokens)))
			.divide(MILLION, 4, RoundingMode.HALF_UP);
	}

}
