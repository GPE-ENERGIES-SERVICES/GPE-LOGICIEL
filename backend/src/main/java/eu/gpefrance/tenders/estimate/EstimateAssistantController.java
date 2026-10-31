package eu.gpefrance.tenders.estimate;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import eu.gpefrance.tenders.analysis.AiAssistant;
import eu.gpefrance.tenders.user.AppUserPrincipal;

/**
 * The proposals of the AI on the estimates, each decided by a user.
 */
@RestController
public class EstimateAssistantController {

	private final EstimateAssistant assistant;

	public EstimateAssistantController(EstimateAssistant assistant) {
		this.assistant = assistant;
	}

	@GetMapping("/api/estimate-assistant")
	public AiAssistant.Availability availability() {
		return assistant.availability();
	}

	@GetMapping("/api/estimates/{id}/suggestions")
	public List<EstimateSuggestion.Response> list(@PathVariable Long id) {
		return assistant.list(id);
	}

	@PostMapping("/api/estimate-items/{itemId}/supply-suggestions")
	public List<EstimateSuggestion.Response> suggestSupplies(@PathVariable Long itemId,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return assistant.suggestSupplies(itemId, user.displayName());
	}

	@PostMapping("/api/estimates/{id}/review")
	public List<EstimateSuggestion.Response> review(@PathVariable Long id,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return assistant.review(id, user.displayName());
	}

	@PostMapping("/api/estimate-suggestions/{id}/acceptance")
	public EstimateResponse accept(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal user) {
		return assistant.accept(id, user.displayName());
	}

	@PostMapping("/api/estimate-suggestions/{id}/rejection")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void reject(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal user) {
		assistant.reject(id, user.displayName());
	}

}
