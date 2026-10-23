package eu.gpefrance.tenders.alert;

import java.math.BigDecimal;
import java.util.List;

import eu.gpefrance.tenders.tender.MarketType;

/**
 * The criteria of a saved search, already validated.
 * @param region name of a region of France, as the interface sends it
 * @param activities codes of types of works (ELECTRICITY...): the tenders do
 * not carry them yet, so a saved search using them can only give an
 * indeterminate result
 */
public record AlertCriteria(String name, List<String> keywords, String region, List<String> departments,
		List<MarketType> marketTypes, List<String> activities, List<String> cpvPrefixes, BigDecimal minAmount,
		BigDecimal maxAmount) {

	public AlertCriteria {
		keywords = List.copyOf(keywords);
		departments = List.copyOf(departments);
		marketTypes = List.copyOf(marketTypes);
		activities = List.copyOf(activities);
		cpvPrefixes = List.copyOf(cpvPrefixes);
	}

	/** Whether at least one criterion restricts the tenders. */
	public boolean restricts() {
		return !keywords.isEmpty() || region != null || !departments.isEmpty() || !marketTypes.isEmpty()
				|| !activities.isEmpty() || !cpvPrefixes.isEmpty() || minAmount != null || maxAmount != null;
	}

}
