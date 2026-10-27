package eu.gpefrance.tenders.analysis.anthropic;

import java.util.Locale;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The Claude models of Anthropic, used when {@code analysis.provider} is
 * "anthropic". The key is never here: it is read from the environment
 * ({@code ANALYSIS_API_KEY}) by the client only.
 * @param model the model, such as "claude-opus-5-5" (the most reliable) or
 * "claude-sonnet-5-5" (half the price)
 * @param effort how much the model thinks before answering: low, medium,
 * high, xhigh or max; high by default, for an extraction that must be right
 * @param maxTokens longest output of one call, thinking included (the model
 * thinks before answering, and that counts); never less than
 * {@code analysis.max-output-tokens}
 */
@ConfigurationProperties("analysis.anthropic")
public record AnthropicProperties(@DefaultValue("claude-opus-5-5") String model, @DefaultValue("high") String effort,
		@DefaultValue("32000") int maxTokens) {

	private static final Set<String> EFFORTS = Set.of("low", "medium", "high", "xhigh", "max");

	public AnthropicProperties {
		model = model == null ? "" : model.strip();
		effort = effort == null ? "" : effort.strip().toLowerCase(Locale.ROOT);
		if (model.isEmpty()) {
			throw new IllegalArgumentException("analysis.anthropic.model may not be empty");
		}
		if (!EFFORTS.contains(effort)) {
			throw new IllegalArgumentException("analysis.anthropic.effort must be one of " + EFFORTS);
		}
		if (maxTokens < 1) {
			throw new IllegalArgumentException("analysis.anthropic.max-tokens must be at least 1");
		}
	}

}
