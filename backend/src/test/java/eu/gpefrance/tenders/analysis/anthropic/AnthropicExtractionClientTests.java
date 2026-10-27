package eu.gpefrance.tenders.analysis.anthropic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;

import eu.gpefrance.tenders.analysis.ExtractionRequest;
import eu.gpefrance.tenders.analysis.FailureKind;
import eu.gpefrance.tenders.analysis.ProviderException;

/**
 * The request sent to Claude, built without any network call, and the client
 * without key, which can never be used.
 */
class AnthropicExtractionClientTests {

	private static final String SCHEMA = """
			{"type": "object", "additionalProperties": false, "required": ["fields"],
			 "properties": {"fields": {"type": "array"}}}""";

	private static ExtractionRequest request(int maxOutputTokens) {
		return new ExtractionRequest(ExtractionRequest.DCE_SUMMARY, "v1", "Remplis la grille.",
				"<document id=\"1\"><page numero=\"1\">Texte</page></document>", SCHEMA, maxOutputTokens, 0,
				Duration.ofMinutes(5));
	}

	@Test
	void sendsTheInstructionsThePagesAndTheSchemaButNoTemperature() {
		AnthropicExtractionClient client = new AnthropicExtractionClient(
				new AnthropicProperties("claude-opus-5-5", "high", 32000), "test-key-never-sent");

		MessageCreateParams params = client.params(request(8000));

		assertThat(params.model().asString()).isEqualTo("claude-opus-5-5");
		// Thinking counts in the output: the ceiling of the client wins over the 8000 of the answer.
		assertThat(params.maxTokens()).isEqualTo(32000L);
		assertThat(params.temperature()).isEmpty();
		assertThat(params.outputConfig()).hasValueSatisfying(config -> {
			assertThat(config.effort()).contains(OutputConfig.Effort.HIGH);
			assertThat(config.format()).isPresent();
		});
		assertThat(params.messages()).hasSize(1);
		assertThat(client.model()).isEqualTo("claude-opus-5-5");
		assertThat(client.provider()).isEqualTo("anthropic");
	}

	@Test
	void neverAsksForLessThanTheLongestAnswerAllowed() {
		AnthropicExtractionClient client = new AnthropicExtractionClient(
				new AnthropicProperties("claude-sonnet-5-5", "medium", 4000), "test-key-never-sent");

		assertThat(client.params(request(8000)).maxTokens()).isEqualTo(8000L);
	}

	@Test
	void withoutKeyHasNoModelAndRefusesToRun() {
		AnthropicExtractionClient client = new AnthropicExtractionClient(
				new AnthropicProperties("claude-opus-5-5", "high", 32000), " ");

		assertThat(client.model()).isNull();
		assertThatThrownBy(() -> client.complete(request(8000))).isInstanceOf(ProviderException.class)
			.satisfies(ex -> assertThat(((ProviderException) ex).kind()).isEqualTo(FailureKind.NOT_CONFIGURED));
	}

	@Test
	void refusesAnUnknownEffort() {
		assertThatThrownBy(() -> new AnthropicProperties("claude-opus-5-5", "extreme", 32000))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
