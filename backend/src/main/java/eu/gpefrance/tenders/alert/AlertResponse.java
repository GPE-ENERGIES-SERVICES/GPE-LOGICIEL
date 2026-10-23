package eu.gpefrance.tenders.alert;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import eu.gpefrance.tenders.tender.MarketType;

/**
 * A saved search with its results among the open consultations
 * ({@code AlertSummary} of the interface).
 * @param matchCount open consultations that match it
 * @param newCount those published after the day of {@code lastViewedAt}
 * @param indeterminateCount open consultations for which it cannot decide
 * (missing information, types of works not evaluated)
 * @param evaluating the results are being evaluated again: the counts may
 * still change
 */
public record AlertResponse(Long id, String name, List<String> keywords, String region, List<String> departments,
		List<MarketType> marketTypes, List<String> activities, List<String> cpvPrefixes, BigDecimal minAmount,
		BigDecimal maxAmount, boolean active, Instant createdAt, Instant updatedAt, Instant lastViewedAt,
		long matchCount, long newCount, long indeterminateCount, boolean evaluating) {

	static AlertResponse from(Alert alert, long matchCount, long newCount, long indeterminateCount,
			boolean evaluating) {
		AlertCriteria criteria = alert.criteria();
		return new AlertResponse(alert.getId(), criteria.name(), criteria.keywords(), criteria.region(),
				criteria.departments(), criteria.marketTypes(), criteria.activities(), criteria.cpvPrefixes(),
				criteria.minAmount(), criteria.maxAmount(), alert.isActive(), alert.getCreatedAt(), alert.getUpdatedAt(),
				alert.getLastViewedAt(), matchCount, newCount, indeterminateCount, evaluating);
	}

}
