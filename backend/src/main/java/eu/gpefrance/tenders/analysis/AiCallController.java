package eu.gpefrance.tenders.analysis;

import java.time.Instant;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import eu.gpefrance.tenders.analysis.AiAssistant.CallSummary;
import eu.gpefrance.tenders.user.AppUserPrincipal;
import jakarta.validation.constraints.Pattern;

/**
 * What a call to the AI usually takes, and what the calls a user just asked
 * cost: shown beside the button that started them.
 */
@RestController
public class AiCallController {

	private final AiAssistant ai;

	public AiCallController(AiAssistant ai) {
		this.ai = ai;
	}

	/**
	 * @param kind MEMOIR_SECTION, RESPONSE_FORM, ESTIMATE_REVIEW...
	 * @param since the moment the user asked; without it, only the typical duration
	 */
	@GetMapping("/api/ai-calls/summary")
	public CallSummary summary(@RequestParam @Pattern(regexp = "[A-Z_]{1,30}") String kind,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return ai.summary(kind, since, user.displayName());
	}

}
