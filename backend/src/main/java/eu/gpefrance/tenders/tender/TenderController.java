package eu.gpefrance.tenders.tender;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import eu.gpefrance.tenders.user.AppUserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Tenders: list with tabs and filters, counts, details, decision of the team
 * and follow of each user. See docs/contrat-api-interface.md.
 */
@RestController
@RequestMapping("/api/tenders")
@Validated
public class TenderController {

	private final TenderService tenderService;

	public TenderController(TenderService tenderService) {
		this.tenderService = tenderService;
	}

	@GetMapping
	public PageResponse<TenderSummaryResponse> search(@RequestParam(defaultValue = "NEW") TenderTab tab,
			@Valid @ModelAttribute TenderSearch search, @RequestParam(required = false) String sort,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return tenderService.search(tab, search, sort, user.id(), page, size);
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
