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

	private final LibraryPriceService libraryPrices;

	private final TechnicalProposals technical;

	public EstimateAssistantController(EstimateAssistant assistant, LibraryPriceService libraryPrices,
			TechnicalProposals technical) {
		this.technical = technical;
		this.assistant = assistant;
		this.libraryPrices = libraryPrices;
	}

	/** For every item still without price, the same work in the price library, chosen by the AI. */
	@PostMapping("/api/estimates/{id}/library-price-suggestions")
	public List<EstimateSuggestion.Response> suggestLibraryPrices(@PathVariable Long id,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return libraryPrices.suggest(id, user.displayName());
	}

	public record LibraryPick(@jakarta.validation.constraints.NotNull Long libraryItemId) {
	}

	/** A line of the price library picked by a user: its price, brought to this year, becomes the item's. */
	@PostMapping("/api/estimate-items/{itemId}/library-price")
	public EstimateResponse pickLibraryPrice(@PathVariable Long itemId,
			@jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody LibraryPick pick,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return libraryPrices.pick(itemId, pick.libraryItemId(), user.displayName());
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

	/** The technical solution of each item not decided by a user, proposed by the AI (technical choices). */
	@PostMapping("/api/estimates/{id}/technical-proposals")
	public TechnicalProposals.Proposed technicalProposals(@PathVariable Long id,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return technical.propose(id, user.displayName());
	}

	/** A first estimate of the items still without cost: supplies and labour proposed, each to accept. */
	@PostMapping("/api/estimates/{id}/ai-draft")
	public List<EstimateSuggestion.Response> draft(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal user) {
		return assistant.draftWithAi(id, user.displayName());
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
