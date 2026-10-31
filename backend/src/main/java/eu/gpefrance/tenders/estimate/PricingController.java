package eu.gpefrance.tenders.estimate;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import eu.gpefrance.tenders.estimate.PricingService.RateResponse;
import eu.gpefrance.tenders.estimate.PricingService.SettingsResponse;
import eu.gpefrance.tenders.user.AppUserPrincipal;
import jakarta.validation.Valid;

/**
 * The settings of GPE for its estimates. Changes are for administrators
 * only (security configuration).
 */
@RestController
public class PricingController {

	private final PricingService service;

	public PricingController(PricingService service) {
		this.service = service;
	}

	@GetMapping("/api/pricing-settings")
	public SettingsResponse settings() {
		return service.settings();
	}

	@PutMapping("/api/pricing-settings")
	public SettingsResponse changeSettings(@Valid @RequestBody EstimateRequest request,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return service.changeSettings(request, user.displayName());
	}

	@GetMapping("/api/pricing-rates")
	public List<RateResponse> rates() {
		return service.rates();
	}

	@PostMapping("/api/pricing-rates")
	@ResponseStatus(HttpStatus.CREATED)
	public RateResponse createRate(@Valid @RequestBody RateRequest request,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return service.createRate(request, user.displayName());
	}

	@PutMapping("/api/pricing-rates/{id}")
	public RateResponse updateRate(@PathVariable Long id, @Valid @RequestBody RateRequest request,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return service.updateRate(id, request, user.displayName());
	}

	@DeleteMapping("/api/pricing-rates/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteRate(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal user) {
		service.deleteRate(id, user.displayName());
	}

}
