package eu.gpefrance.tenders.tender;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.Map;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;

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
			@RequestParam(required = false) String department, @RequestParam(required = false) String q,
			@RequestParam(required = false) @PositiveOrZero Integer minDaysLeft,
			@RequestParam(required = false) @PositiveOrZero BigDecimal maxAmount,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return tenderService.search(new TenderSearch(tab, department, q, minDaysLeft, maxAmount), page, size);
	}

	@GetMapping("/counts")
	public Map<TenderTab, Long> counts() {
		return tenderService.countByTab();
	}

	@GetMapping("/{id}")
	public TenderDetailResponse detail(@PathVariable Long id) {
		return tenderService.detail(id);
	}

	@PutMapping("/{id}/follow-up")
	public TenderDetailResponse followUp(@PathVariable Long id, @Valid @RequestBody FollowUpRequest request,
			Principal principal) {
		return tenderService.followUp(id, request, principal == null ? null : principal.getName());
	}

}
