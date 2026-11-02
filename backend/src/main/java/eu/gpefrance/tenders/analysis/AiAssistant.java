package eu.gpefrance.tenders.analysis;

import java.time.Clock;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.ErrorResponseException;

/**
 * One call to the AI for another part of the application (the assistance to
 * the estimates), with the same safeguards as the analyses: the gate first
 * (enabled, provider and key, transmission allowed, monthly ceiling), the
 * tokens counted in the same ceiling, the errors of the provider cleaned. The
 * provider stays hidden here: the caller gives instructions and text, and
 * gets the raw answer, which it must check.
 */
@Component
public class AiAssistant {

	private static final Logger log = LoggerFactory.getLogger(AiAssistant.class);

	private final AnalysisGate gate;

	private final AnalysisProperties properties;

	private final JdbcTemplate jdbc;

	private final Clock clock;

	public AiAssistant(AnalysisGate gate, AnalysisProperties properties, JdbcTemplate jdbc, Clock clock) {
		this.gate = gate;
		this.properties = properties;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	/**
	 * @param available whether a call may be made now
	 * @param message why not, for the interface
	 */
	public record Availability(boolean available, String message) {
	}

	public Availability availability() {
		AnalysisGate.Status status = gate.status();
		return new Availability(status.ready(), status.message());
	}

	/**
	 * @param model the model that answered
	 */
	public record Answer(String text, String model) {
	}

	/**
	 * @param kind what the call is for, recorded with its tokens
	 * @throws ErrorResponseException 409 when the AI may not be called, 502 when
	 * the provider failed
	 */
	public Answer ask(String kind, String promptVersion, String instructions, String content, String outputSchema,
			int maxOutputTokens, String user) {
		ExtractionClient client = gate.readyClient()
			.orElseThrow(() -> problem(HttpStatus.CONFLICT, gate.status().message()));
		if (!gate.fitsBudget(instructions.length() + content.length())) {
			throw problem(HttpStatus.CONFLICT, "Le plafond mensuel de dépense de l'IA ne permet pas cette demande.");
		}
		ExtractionRequest request = new ExtractionRequest(kind, promptVersion, instructions, content, outputSchema,
				maxOutputTokens, 0, min(properties.requestTimeout(), Duration.ofMinutes(2)));
		try {
			ExtractionClient.ModelAnswer answer = client.complete(request);
			record(kind, client.model(), answer, user);
			return new Answer(answer.text(), client.model());
		}
		catch (ProviderException ex) {
			if (ex.usage() != null) {
				record(kind, client.model(), ex.usage(), user);
			}
			log.warn("AI call {} failed: {} {}", kind, ex.kind(), SecretRedactor.redact(ex.getMessage()));
			throw problem(HttpStatus.BAD_GATEWAY, "L'IA n'a pas répondu (" + ex.kind() + ") : réessayez plus tard.");
		}
	}

	/** The tokens of a call, counted in the monthly ceiling with the analyses. */
	private void record(String kind, String model, ExtractionClient.ModelAnswer usage, String user) {
		jdbc.update("""
				INSERT INTO ai_call (kind, model, input_tokens, output_tokens, created_by, created_at)
				VALUES (?, ?, ?, ?, ?, ?)""", kind, model, usage.inputTokens(), usage.outputTokens(), user,
				java.sql.Timestamp.from(clock.instant()));
	}

	private static Duration min(Duration a, Duration b) {
		return a.compareTo(b) <= 0 ? a : b;
	}

	private static ErrorResponseException problem(HttpStatus status, String detail) {
		return new ErrorResponseException(status, ProblemDetail.forStatusAndDetail(status, detail), null);
	}

}
