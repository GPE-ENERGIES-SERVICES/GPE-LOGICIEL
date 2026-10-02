package eu.gpefrance.tenders.filter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import eu.gpefrance.tenders.tender.MarketType;

public record FilterCriteriaResponse(List<String> departments, List<MarketType> marketTypes,
		Integer minDaysBeforeDeadline, List<String> cpvPrefixes, List<String> keywords, List<String> excludedKeywords,
		BigDecimal minAmount, BigDecimal maxAmount, Instant updatedAt, String updatedBy) {

	static FilterCriteriaResponse from(FilterCriteria criteria) {
		return new FilterCriteriaResponse(criteria.getDepartments(), criteria.getMarketTypes(),
				criteria.getMinDaysBeforeDeadline(), criteria.getCpvPrefixes(), criteria.getKeywords(),
				criteria.getExcludedKeywords(), criteria.getMinAmount(), criteria.getMaxAmount(),
				criteria.getUpdatedAt(), criteria.getUpdatedBy());
	}

}
