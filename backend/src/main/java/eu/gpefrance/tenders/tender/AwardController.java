package eu.gpefrance.tenders.tender;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import eu.gpefrance.tenders.user.AppUserPrincipal;

/**
 * Results of the tenders: « Marchés attribués », the awards of a tender and
 * the recording of a result. See docs/contrat-api-interface.md.
 */
@RestController
@Validated
public class AwardController {

	private final AwardService awardService;

	private final TenderService tenderService;

	public AwardController(AwardService awardService, TenderService tenderService) {
		this.awardService = awardService;
		this.tenderService = tenderService;
	}

	@GetMapping("/api/awards")
	public AwardService.AwardSearchResponse search(@ModelAttribute AwardService.AwardFilters filters,
			@RequestParam(required = false) String sort, @RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return awardService.search(filters, sort, page, size);
	}

	@GetMapping("/api/tenders/{id}/awards")
	public List<AwardService.AwardResponse> ofTender(@PathVariable Long id) {
		return awardService.ofTender(id);
	}

	/** Records the result of a tender, won or lost, for the whole contract or one lot. */
	@PutMapping("/api/tenders/{id}/award")
	public TenderDetailResponse record(@PathVariable Long id, @Valid @RequestBody AwardService.AwardRequest request,
			@AuthenticationPrincipal AppUserPrincipal user) {
		awardService.record(id, request, user.displayName());
		return tenderService.detail(id, user.id());
	}

	@GetMapping("/api/tenders/{id}/history")
	public List<TenderService.StatusChangeResponse> history(@PathVariable Long id) {
		return tenderService.history(id);
	}

}
