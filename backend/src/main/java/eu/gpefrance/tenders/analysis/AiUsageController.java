package eu.gpefrance.tenders.analysis;

import java.time.Instant;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import eu.gpefrance.tenders.analysis.AiUsageService.Call;
import eu.gpefrance.tenders.analysis.AiUsageService.Overview;
import eu.gpefrance.tenders.analysis.AiUsageService.PageOf;
import eu.gpefrance.tenders.analysis.AiUsageService.TenderCost;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/** What the AI cost, for the supervision; administrators only (see SecurityConfiguration). */
@RestController
@Validated
public class AiUsageController {

	private final AiUsageService usage;

	public AiUsageController(AiUsageService usage) {
		this.usage = usage;
	}

	/** @param since start of the period of the services; all the calls without it */
	@GetMapping("/api/ai-usage")
	public Overview overview(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since) {
		return usage.overview(since);
	}

	@GetMapping("/api/ai-usage/tenders")
	public PageOf<TenderCost> tenders(@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return usage.tenders(page, size);
	}

	/** @param sort "cost" for the most expensive first, the latest first otherwise */
	@GetMapping("/api/ai-usage/calls")
	public PageOf<Call> calls(@RequestParam(required = false) @Pattern(regexp = "[A-Z_]{1,30}") String kind,
			@RequestParam(required = false) @Pattern(regexp = "RUNNING|DONE|FAILED|INTERRUPTED") String status,
			@RequestParam(required = false) Long tenderId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
			@RequestParam(defaultValue = "date") @Pattern(regexp = "date|cost") String sort,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return usage.calls(kind, status, tenderId, from, to, "cost".equals(sort), page, size);
	}

}
