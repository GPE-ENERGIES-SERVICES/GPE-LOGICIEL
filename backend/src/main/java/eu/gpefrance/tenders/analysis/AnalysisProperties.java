package eu.gpefrance.tenders.analysis;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param maxCharacters maximum text sent to the model for one analysis;
 * documents that do not fit are listed as not analysed, never cut
 * @param inputPricePerMillion price of one million input tokens, in euros
 * @param outputPricePerMillion price of one million output tokens, in euros
 */
@ConfigurationProperties("analysis")
public record AnalysisProperties(int maxCharacters, BigDecimal inputPricePerMillion,
		BigDecimal outputPricePerMillion) {

	private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

	/**
	 * @return the cost in euros, or null when the prices are not set
	 */
	BigDecimal cost(long inputTokens, long outputTokens) {
		if (inputPricePerMillion == null || outputPricePerMillion == null
				|| inputPricePerMillion.signum() == 0 && outputPricePerMillion.signum() == 0) {
			return null;
		}
		return inputPricePerMillion.multiply(BigDecimal.valueOf(inputTokens))
			.add(outputPricePerMillion.multiply(BigDecimal.valueOf(outputTokens)))
			.divide(MILLION, 4, RoundingMode.HALF_UP);
	}

}
