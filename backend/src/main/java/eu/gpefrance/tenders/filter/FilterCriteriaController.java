package eu.gpefrance.tenders.filter;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import eu.gpefrance.tenders.user.AppUserPrincipal;
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
	public FilterCriteriaResponse replace(@Valid @RequestBody FilterCriteriaRequest request,
			@AuthenticationPrincipal AppUserPrincipal user) {
		FilterCriteria updated = filterService.replaceCriteria(request, user.displayName());
		filterService.filterAll();
		return FilterCriteriaResponse.from(updated);
	}

}
