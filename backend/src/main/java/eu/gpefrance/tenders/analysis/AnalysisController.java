package eu.gpefrance.tenders.analysis;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import eu.gpefrance.tenders.analysis.AnalysisResponse.TenderAnalysis;
import eu.gpefrance.tenders.user.AppUserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

@RestController
public class AnalysisController {

	private final AnalysisService service;

	private final AnalysisQueries queries;

	private final ValidationService validation;

	public AnalysisController(AnalysisService service, AnalysisQueries queries, ValidationService validation) {
		this.service = service;
		this.queries = queries;
		this.validation = validation;
	}

	@GetMapping("/api/tenders/{tenderId}/analysis")
	public TenderAnalysis ofTender(@PathVariable Long tenderId) {
		return queries.ofTender(tenderId);
	}

	/**
	 * Starts the analysis of a dossier in the background.
	 * @param force analyse again content already analysed
	 */
	@PostMapping("/api/dossiers/{dossierId}/analyses")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public AnalysisResponse request(@PathVariable Long dossierId,
			@RequestParam(defaultValue = "false") boolean force) {
		return queries.summary(service.request(dossierId, force));
	}

	/**
	 * Validates, corrects or rejects one value; a null decision cancels the
	 * previous one.
	 */
	@PutMapping("/api/extracted-fields/{fieldId}/decision")
	public TenderAnalysis decide(@PathVariable Long fieldId, @Valid @RequestBody DecisionRequest request,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return queries.ofTender(validation.decide(fieldId, request.decision(), request.value(), user.displayName()));
	}

	@PostMapping("/api/analyses/{analysisId}/validation")
	public TenderAnalysis validateSheet(@PathVariable Long analysisId,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return queries.ofTender(validation.validateSheet(analysisId, user.displayName()));
	}

	/**
	 * @param value the right value, for a correction
	 */
	public record DecisionRequest(FieldDecision decision,
			@Size(max = 4000, message = "La valeur corrigée est trop longue") String value) {
	}

}
