package eu.gpefrance.tenders.tender;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import eu.gpefrance.tenders.filter.FilterService;
import eu.gpefrance.tenders.user.AppUserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Tenders: list with tabs and filters, counts, details, decision of the team
 * and follow of each user. See docs/contrat-api-interface.md.
 */
@RestController
@RequestMapping("/api/tenders")
@Validated
public class TenderController {

	private final TenderService tenderService;

	private final FilterService filterService;

	public TenderController(TenderService tenderService, FilterService filterService) {
		this.tenderService = tenderService;
		this.filterService = filterService;
	}

	/** @param activities the domains chosen, empty for none of those of GPE */
	public record ActivitiesRequest(
			@NotNull List<@NotNull @Pattern(regexp = "ELECTRICITY|PHOTOVOLTAIC|IRVE", message = "domaine inconnu") String> activities) {
	}

	/** A user gives the domains of a tender: they win over the rules and the AI, and the tender is filtered again. */
	@PutMapping("/{id}/activities")
	public TenderDetailResponse classify(@PathVariable Long id, @Valid @RequestBody ActivitiesRequest request,
			@AuthenticationPrincipal AppUserPrincipal user) {
		filterService.classifyByHand(id, request.activities().stream().distinct().toList());
		return tenderService.detail(id, user.id());
	}

	@GetMapping
	public PageResponse<TenderSummaryResponse> search(@RequestParam(defaultValue = "NEW") TenderTab tab,
			@Valid @ModelAttribute TenderSearch search, @RequestParam(required = false) String sort,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return tenderService.search(tab, search, sort, user.id(), page, size);
	}

	/**
	 * Adds a tender found in no source followed: it then goes the way of any
	 * other, from its consultation file to the answer.
	 */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public TenderDetailResponse create(@Valid @RequestBody ManualTenderRequest request,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return tenderService.createManual(request, user.displayName(), user.id());
	}

	@GetMapping("/counts")
	public Map<TenderTab, Long> counts(@Valid @ModelAttribute TenderSearch search,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return tenderService.countByTab(search, user.id());
	}

	@GetMapping("/status-counts")
	public Map<FollowUpStatus, Long> statusCounts(@RequestParam(defaultValue = "ALL") TenderTab tab,
			@Valid @ModelAttribute TenderSearch search, @AuthenticationPrincipal AppUserPrincipal user) {
		return tenderService.countByStatus(tab, search, user.id());
	}

	@GetMapping("/{id}")
	public TenderDetailResponse detail(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal user) {
		return tenderService.detail(id, user.id());
	}

	@PutMapping("/{id}/follow-up")
	public TenderDetailResponse followUp(@PathVariable Long id, @Valid @RequestBody FollowUpRequest request,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return tenderService.followUp(id, request, user.displayName(), user.id());
	}

	@PutMapping("/{id}/follow")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void follow(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal user) {
		tenderService.follow(id, user.id(), true);
	}

	@DeleteMapping("/{id}/follow")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void unfollow(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal user) {
		tenderService.follow(id, user.id(), false);
	}

}
