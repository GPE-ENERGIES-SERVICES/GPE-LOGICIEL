package eu.gpefrance.tenders.analysis;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import eu.gpefrance.tenders.analysis.AnalysisResponse.TenderAnalysis;

@RestController
public class AnalysisController {

	private final AnalysisService service;

	private final AnalysisQueries queries;

	public AnalysisController(AnalysisService service, AnalysisQueries queries) {
		this.service = service;
		this.queries = queries;
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

}
