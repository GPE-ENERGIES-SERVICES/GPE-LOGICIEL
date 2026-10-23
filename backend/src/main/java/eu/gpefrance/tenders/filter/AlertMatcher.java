package eu.gpefrance.tenders.filter;

import eu.gpefrance.tenders.alert.AlertCriteria;
import eu.gpefrance.tenders.tender.Tender;

/**
 * Evaluates the criteria of a saved search with the rules of the filter of
 * the company ({@link TenderFilter}), which it does not change.
 */
public final class AlertMatcher {

	private AlertMatcher() {
	}

	public static AlertMatch match(Tender tender, AlertCriteria criteria) {
		return TenderFilter.matchAlert(tender, criteria);
	}

}
