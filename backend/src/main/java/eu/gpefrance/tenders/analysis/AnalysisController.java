package eu.gpefrance.tenders.analysis;

import java.util.List;

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

import eu.gpefrance.tenders.analysis.AnalysisReport.Configuration;
import eu.gpefrance.tenders.analysis.AnalysisReport.JournalEntry;
import eu.gpefrance.tenders.analysis.AnalysisReport.Quality;
import eu.gpefrance.tenders.analysis.AnalysisResponse.TenderAnalysis;
import eu.gpefrance.tenders.user.AppUserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

@RestController
public class AnalysisController {

	private final AnalysisService service;

	private final AnalysisQueries queries;

	private final ValidationService validation;

	private final AnalysisReports reports;

	public AnalysisController(AnalysisService service, AnalysisQueries queries, ValidationService validation,
			AnalysisReports reports) {
		this.service = service;
		this.queries = queries;
		this.validation = validation;
		this.reports = reports;
	}

	/**
	 * Latest analyses with their cost and errors (administrator).
	 */
	@GetMapping("/api/analyses")
	public List<JournalEntry> journal(@RequestParam(defaultValue = "30") @Min(1) @Max(200) int limit) {
		return reports.journal(limit);
	}

	/**
	 * Answers of the model compared with the decisions of the users
	 * (administrator).
	 */
	@GetMapping("/api/analyses/quality")
	public Quality quality() {
		return reports.quality();
	}

	/**
	 * Configuration of the AI analysis, queue and consumption of the month
	 * (administrator). No key is ever returned.
	 */
	@GetMapping("/api/analyses/configuration")
	public Configuration configuration() {
		return reports.configuration();
	}

	/**
	 * Where an analysis stands, without its answers nor any text.
	 */
	@GetMapping("/api/analyses/{analysisId}")
	public AnalysisResponse status(@PathVariable Long analysisId) {
		return queries.summary(analysisId);
	}

	@GetMapping("/api/tenders/{tenderId}/analysis")
	public TenderAnalysis ofTender(@PathVariable Long tenderId) {
		return queries.ofTender(tenderId);
	}

	/**
	 * Queues the analysis of a dossier of GPE; it runs in the background. A
	 * failed analysis is tried again the same way, with force.
	 * @param force analyse again content already analysed
	 */
	@PostMapping("/api/dossiers/{dossierId}/analyses")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public AnalysisResponse request(@PathVariable Long dossierId,
			@RequestParam(defaultValue = "false") boolean force, @AuthenticationPrincipal AppUserPrincipal user) {
		return queries.summary(service.request(dossierId, force, user.displayName()));
	}

	/**
	 * Cancels an analysis not sent yet.
	 */
	@PostMapping("/api/analyses/{analysisId}/cancel")
	public TenderAnalysis cancel(@PathVariable Long analysisId, @AuthenticationPrincipal AppUserPrincipal user) {
		return queries.ofTender(service.cancel(analysisId, user.displayName()));
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
