package eu.gpefrance.tenders.equipment;

import java.math.BigDecimal;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import eu.gpefrance.tenders.equipment.TechnicalChoiceService.Choices;
import eu.gpefrance.tenders.equipment.TechnicalChoiceService.View;
import eu.gpefrance.tenders.user.AppUserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** The technical choices of a tender, between its analysis and its estimate. */
@RestController
public class TechnicalChoiceController {

	private final TechnicalChoiceService service;

	public TechnicalChoiceController(TechnicalChoiceService service) {
		this.service = service;
	}

	public record ChoicesRequest(@Size(max = 20) String valorisation, @Size(max = 2000) String roof,
			@DecimalMin(value = "0", inclusive = false) @DecimalMax("100000") BigDecimal requestedKwc,
			@Min(1) @Max(1_000_000) Integer moduleCount,
			@DecimalMin(value = "0", inclusive = false) @DecimalMax("2000") BigDecimal moduleWc,
			@Min(1) @Max(10_000) Integer inverterCount,
			@DecimalMin(value = "0", inclusive = false) @DecimalMax("100000") BigDecimal inverterKva,
			@Size(max = 8000) String constraints, @Size(max = 8000) String openPoints, @Size(max = 8000) String notes) {
	}

	@GetMapping("/api/tenders/{tenderId}/technical-choices")
	public View view(@PathVariable Long tenderId) {
		return service.view(tenderId);
	}

	@PutMapping("/api/tenders/{tenderId}/technical-choices")
	public View save(@PathVariable Long tenderId, @Valid @RequestBody ChoicesRequest request,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return service.save(tenderId, new Choices(request.valorisation(), request.roof(), request.requestedKwc(),
				request.moduleCount(), request.moduleWc(), request.inverterCount(), request.inverterKva(),
				request.constraints(), request.openPoints(), request.notes()), user.displayName());
	}

	@PostMapping("/api/tenders/{tenderId}/technical-choices/validation")
	public View validate(@PathVariable Long tenderId, @AuthenticationPrincipal AppUserPrincipal user) {
		return service.validate(tenderId, user.displayName());
	}

}
