package eu.gpefrance.tenders.analysis;

import java.math.BigDecimal;

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
}
