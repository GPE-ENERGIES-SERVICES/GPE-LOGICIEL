package eu.gpefrance.tenders.equipment;

import java.math.BigDecimal;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import eu.gpefrance.tenders.equipment.TechnicalChoiceService.Choices;
import eu.gpefrance.tenders.equipment.TechnicalChoiceService.LineRequest;
import eu.gpefrance.tenders.equipment.TechnicalChoiceService.View;
import eu.gpefrance.tenders.user.AppUserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** The technical choices of a tender, between its analysis and its estimate. */
@RestController
public class TechnicalChoiceController {

	private final TechnicalChoiceService service;

	public TechnicalChoiceController(TechnicalChoiceService service) {
		this.service = service;
	}

	public record ChoicesRequest(@Size(max = 20) String valorisation, @Size(max = 4000) String roof,
			@DecimalMin(value = "0", inclusive = false) @DecimalMax("100000") BigDecimal requestedKwc,
			@DecimalMin(value = "0", inclusive = false) @DecimalMax("5000") BigDecimal yieldKwhKwc,
			@DecimalMin(value = "0", inclusive = false) @DecimalMax("1000000") BigDecimal productionMwh,
			@Size(max = 8000) String constraints, @Size(max = 8000) String siteAccess, @Size(max = 8000) String safety,
			@Size(max = 8000) String subcontracting, @Size(max = 8000) String planning, @Size(max = 8000) String notes) {
	}

	public record LineBody(@Size(max = 4000) String text, @Size(max = 4000) String answer, @Size(max = 20) String status,
			@DecimalMin("0") @DecimalMax("1000000000") BigDecimal value, @Size(max = 20) String unit,
			@Min(1) @Max(1_000_000) Integer moduleCount, @Size(max = 60) String orientation,
			@DecimalMin("0") @DecimalMax("90") BigDecimal tilt) {

		LineRequest request() {
			return new LineRequest(text, answer, status, value, unit, moduleCount, orientation, tilt);
		}

	}

	/** @param unitPower Wc for a module, kVA for an inverter */
	public record EquipmentDetail(@DecimalMin(value = "0", inclusive = false) @DecimalMax("1000000") BigDecimal quantity,
			@DecimalMin(value = "0", inclusive = false) @DecimalMax("100000") BigDecimal unitPower,
			@Size(max = 2000) String note) {
	}

	/** @param added the values and lines proposed */
	public record Prefilled(int added, View view) {
	}

	@GetMapping("/api/tenders/{tenderId}/technical-choices")
	public View view(@PathVariable Long tenderId) {
		return service.view(tenderId);
	}

	@PutMapping("/api/tenders/{tenderId}/technical-choices")
	public View save(@PathVariable Long tenderId, @Valid @RequestBody ChoicesRequest request,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return service.save(tenderId, new Choices(request.valorisation(), request.roof(), request.requestedKwc(),
				request.yieldKwhKwc(), request.productionMwh(), request.constraints(), request.siteAccess(),
				request.safety(), request.subcontracting(), request.planning(), request.notes()), user.displayName());
	}

	@PostMapping("/api/tenders/{tenderId}/technical-choices/validation")
	public View validate(@PathVariable Long tenderId, @AuthenticationPrincipal AppUserPrincipal user) {
		return service.validate(tenderId, user.displayName());
	}

	/** What the latest analysis of the file read, where nothing is written yet. */
	@PostMapping("/api/tenders/{tenderId}/technical-choices/prefill")
	public Prefilled prefill(@PathVariable Long tenderId, @AuthenticationPrincipal AppUserPrincipal user) {
		int added = service.prefill(tenderId, user.displayName());
		return new Prefilled(added, service.view(tenderId));
	}

	@PostMapping("/api/tenders/{tenderId}/technical-lines/{kind}")
	public View addLine(@PathVariable Long tenderId,
			@PathVariable @Pattern(regexp = "REQUIREMENT|QUESTION|QUANTITY|PLANE") String kind,
			@Valid @RequestBody LineBody body, @AuthenticationPrincipal AppUserPrincipal user) {
		return service.addLine(tenderId, kind, body.request(), user.displayName());
	}

	@PutMapping("/api/tenders/{tenderId}/technical-lines/{lineId}")
	public View updateLine(@PathVariable Long tenderId, @PathVariable Long lineId, @Valid @RequestBody LineBody body,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return service.updateLine(tenderId, lineId, body.request(), user.displayName());
	}

	@DeleteMapping("/api/tenders/{tenderId}/technical-lines/{lineId}")
	public View deleteLine(@PathVariable Long tenderId, @PathVariable Long lineId,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return service.deleteLine(tenderId, lineId, user.displayName());
	}

	@PutMapping("/api/tenders/{tenderId}/equipment/{sheetId}")
	public View detailEquipment(@PathVariable Long tenderId, @PathVariable Long sheetId,
			@Valid @RequestBody EquipmentDetail detail, @AuthenticationPrincipal AppUserPrincipal user) {
		return service.detailEquipment(tenderId, sheetId, detail.quantity(), detail.unitPower(), detail.note(),
				user.displayName());
	}

}
