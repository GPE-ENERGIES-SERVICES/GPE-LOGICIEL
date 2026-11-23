package eu.gpefrance.tenders.analysis;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The price of each model, in euros per million tokens, when it differs from
 * {@code analysis.input-price-per-million} and {@code analysis.output-price-per-million}:
 * {@code analysis.pricing.models.claude-opus-5-5.input=15}. A model absent here
 * is priced with those two.
 */
@ConfigurationProperties("analysis.pricing")
public record AiPrices(Map<String, ModelPrice> models) {

	public AiPrices {
		models = models == null ? Map.of() : Map.copyOf(models);
		models.forEach((model, price) -> {
			if (price.input() == null || price.output() == null || price.input().signum() < 0
					|| price.output().signum() < 0) {
				throw new IllegalArgumentException("analysis.pricing.models." + model
						+ ": input and output prices are required and may not be negative");
			}
		});
	}

	/** @param input euros per million input tokens; @param output euros per million output tokens */
	public record ModelPrice(BigDecimal input, BigDecimal output) {
	}

}
