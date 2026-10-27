package eu.gpefrance.tenders.analysis.anthropic;

import java.io.InterruptedIOException;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.RequestOptions;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;

import eu.gpefrance.tenders.analysis.ExtractionClient;
import eu.gpefrance.tenders.analysis.ExtractionRequest;
import eu.gpefrance.tenders.analysis.FailureKind;
import eu.gpefrance.tenders.analysis.ProviderException;
import jakarta.annotation.PreDestroy;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Claude, by Anthropic ({@code analysis.provider=anthropic}). Sends the
 * instructions as the system prompt and the pages as the user message, and
 * asks for an answer that follows the JSON Schema of the grid (structured
 * output): the answer is read and checked by the application all the same.
 * <p>
 * The temperature of the request is not sent: current Claude models refuse
 * it. The model thinks before answering, which counts in its output, hence
 * {@code analysis.anthropic.max-tokens}. Nothing is retried here: the queue
 * of the analyses decides, from the kind of failure.
 */
@Component
public class AnthropicExtractionClient implements ExtractionClient {

	static final String PROVIDER = "anthropic";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final AnthropicProperties properties;

	/** Null without key: the analysis stays off ({@link #model()} is null). */
	private final AnthropicClient client;

	public AnthropicExtractionClient(AnthropicProperties properties, @Value("${ANALYSIS_API_KEY:}") String apiKey) {
		this.properties = properties;
		this.client = apiKey == null || apiKey.isBlank() ? null
				: AnthropicOkHttpClient.builder().apiKey(apiKey.strip()).maxRetries(0).build();
	}

	@PreDestroy
	void close() {
		if (client != null) {
			client.close();
		}
	}

	@Override
	public String provider() {
		return PROVIDER;
	}

	@Override
	public String model() {
		return client == null ? null : properties.model();
	}

	@Override
	public ModelAnswer complete(ExtractionRequest request) {
		if (client == null) {
			throw new ProviderException(FailureKind.NOT_CONFIGURED, "Clé Anthropic absente (ANALYSIS_API_KEY)");
		}
		Message message;
		try {
			message = client.messages()
				.create(params(request), RequestOptions.builder().timeout(request.timeout()).build());
		}
		catch (UnauthorizedException | PermissionDeniedException ex) {
			throw new ProviderException(FailureKind.AUTHENTICATION, "Anthropic : clé refusée (HTTP " + ex.statusCode() + ")");
		}
		catch (RateLimitException ex) {
			throw new ProviderException(FailureKind.RATE_LIMITED, "Anthropic : limite de débit atteinte (HTTP 429)");
		}
		catch (AnthropicServiceException ex) {
			throw failure(ex);
		}
		catch (AnthropicIoException ex) {
			boolean timeout = ex.getCause() instanceof InterruptedIOException;
			throw new ProviderException(timeout ? FailureKind.TIMEOUT : FailureKind.TEMPORARY,
					"Anthropic : " + (timeout ? "pas de réponse dans le délai" : "connexion impossible"));
		}
		StopReason stop = message.stopReason().orElse(null);
		if (StopReason.REFUSAL.equals(stop)) {
			throw new ProviderException(FailureKind.REFUSED, "Anthropic : analyse refusée par le modèle");
		}
		if (StopReason.MAX_TOKENS.equals(stop)) {
			throw new ProviderException(FailureKind.INVALID_RESPONSE,
					"Anthropic : réponse coupée, limite de " + maxTokens(request) + " tokens atteinte");
		}
		String text = message.content()
			.stream()
			.flatMap(block -> block.text().stream())
			.map(block -> block.text())
			.collect(Collectors.joining());
		return new ModelAnswer(text, Math.toIntExact(message.usage().inputTokens()),
				Math.toIntExact(message.usage().outputTokens()));
	}

	MessageCreateParams params(ExtractionRequest request) {
		return MessageCreateParams.builder()
			.model(properties.model())
			.maxTokens(maxTokens(request))
			.system(request.instructions())
			.addUserMessage(request.documents())
			.outputConfig(OutputConfig.builder()
				.effort(OutputConfig.Effort.of(properties.effort()))
				.format(JsonOutputFormat.builder().schema(schema(request.outputSchema())).build())
				.build())
			.build();
	}

	private long maxTokens(ExtractionRequest request) {
		return Math.max(properties.maxTokens(), request.maxOutputTokens());
	}

	private static JsonOutputFormat.Schema schema(String outputSchema) {
		JsonOutputFormat.Schema.Builder schema = JsonOutputFormat.Schema.builder();
		try {
			Map<?, ?> fields = JSON.readValue(outputSchema, Map.class);
			fields.forEach((key, value) -> schema.putAdditionalProperty(String.valueOf(key), JsonValue.from(value)));
		}
		catch (JacksonException ex) {
			throw new ProviderException(FailureKind.UNEXPECTED, "Schéma de réponse illisible", ex);
		}
		return schema.build();
	}

	/**
	 * The other refusals of the API, by status. The message keeps only the
	 * status and the type of error: never the documents nor the key.
	 */
	private static ProviderException failure(AnthropicServiceException ex) {
		int status = ex.statusCode();
		String type = ex.errorType().map(String::valueOf).orElse("inconnue");
		String detail = "Anthropic : HTTP " + status + " (" + type + ")";
		FailureKind kind = switch (status) {
			case 402 -> FailureKind.QUOTA_EXCEEDED;
			case 413 -> FailureKind.CONTEXT_TOO_LARGE;
			case 408 -> FailureKind.TIMEOUT;
			default -> status >= 500 ? FailureKind.TEMPORARY : FailureKind.UNEXPECTED;
		};
		return new ProviderException(kind, detail);
	}

}
