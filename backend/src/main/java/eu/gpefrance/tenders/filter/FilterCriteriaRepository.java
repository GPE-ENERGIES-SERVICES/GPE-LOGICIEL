package eu.gpefrance.tenders.filter;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FilterCriteriaRepository extends JpaRepository<FilterCriteria, Long> {

	default FilterCriteria getCurrent() {
		return findById(FilterCriteria.SINGLE_ID)
			.orElseThrow(() -> new IllegalStateException("Filter criteria row is missing"));
	}

}
