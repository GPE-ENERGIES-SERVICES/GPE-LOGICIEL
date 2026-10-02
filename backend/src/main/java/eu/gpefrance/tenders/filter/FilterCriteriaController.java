package eu.gpefrance.tenders.filter;

import java.security.Principal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/filter-criteria")
public class FilterCriteriaController {

	private final FilterService filterService;

	private final FilterCriteriaRepository criteria;

	public FilterCriteriaController(FilterService filterService, FilterCriteriaRepository criteria) {
		this.filterService = filterService;
		this.criteria = criteria;
	}

	@GetMapping
	public FilterCriteriaResponse current() {
		return FilterCriteriaResponse.from(criteria.getCurrent());
	}

	/**
	 * Replaces the criteria and filters every tender again.
	 */
	@PutMapping
	public FilterCriteriaResponse replace(@Valid @RequestBody FilterCriteriaRequest request, Principal principal) {
		FilterCriteria updated = filterService.replaceCriteria(request, principal == null ? null : principal.getName());
		filterService.filterAll();
		return FilterCriteriaResponse.from(updated);
	}

}
