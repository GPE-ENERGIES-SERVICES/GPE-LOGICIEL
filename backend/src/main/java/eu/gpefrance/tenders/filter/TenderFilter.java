package eu.gpefrance.tenders.filter;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import eu.gpefrance.tenders.tender.MarketType;
import eu.gpefrance.tenders.tender.Tender;

/**
 * Applies the filtering criteria to a tender (rule RM7): it is retained if it
 * meets every mandatory criterion (zone, market type, time left, amount,
 * excluded keywords) and at least one business criterion (CPV code or
 * keyword). Information missing from the notice never excludes a tender.
 */
final class TenderFilter {

	private static final Map<MarketType, String> MARKET_TYPE_LABELS = Map.of(MarketType.WORKS, "travaux",
			MarketType.SERVICES, "services", MarketType.SUPPLIES, "fournitures");

	private TenderFilter() {
	}

	static FilterResult apply(Tender tender, FilterCriteria criteria, Instant now) {
		List<String> reasons = new ArrayList<>();
		List<String> failures = new ArrayList<>();

		checkZone(tender, criteria, reasons, failures);
		checkMarketType(tender, criteria, reasons, failures);
		checkTimeLeft(tender, criteria, now, reasons, failures);
		checkAmount(tender, criteria, reasons, failures);

		TextMatcher text = new TextMatcher(searchableText(tender));
		List<String> excluded = criteria.getExcludedKeywords().stream().filter(text::contains).toList();
		if (!excluded.isEmpty()) {
			failures.add("mot-clé exclu " + quoted(excluded));
		}

		List<String> cpvCodes = tender.getCpvCodes()
			.stream()
			.filter(code -> criteria.getCpvPrefixes().stream().anyMatch(code::startsWith))
			.toList();
		List<String> keywords = criteria.getKeywords().stream().filter(text::contains).toList();
		if (!cpvCodes.isEmpty()) {
			reasons.add("CPV " + String.join(", ", cpvCodes));
		}
		if (!keywords.isEmpty()) {
			reasons.add("mot-clé " + quoted(keywords));
		}
		boolean businessCriteriaUsed = !criteria.getCpvPrefixes().isEmpty() || !criteria.getKeywords().isEmpty();
		if (businessCriteriaUsed && cpvCodes.isEmpty() && keywords.isEmpty()) {
			failures.add("aucun code CPV ni mot-clé métier");
		}

		if (!failures.isEmpty()) {
			return new FilterResult(false, "Écarté : " + String.join(" ; ", failures));
		}
		return new FilterResult(true,
				reasons.isEmpty() ? "Retenu : aucun critère défini" : "Retenu : " + String.join(" ; ", reasons));
	}

	private static void checkZone(Tender tender, FilterCriteria criteria, List<String> reasons, List<String> failures) {
		if (criteria.getDepartments().isEmpty()) {
			return;
		}
		if (tender.getDepartments().isEmpty()) {
			reasons.add("département non indiqué");
			return;
		}
		List<String> inZone = tender.getDepartments().stream().filter(criteria.getDepartments()::contains).toList();
		if (inZone.isEmpty()) {
			failures.add("hors zone (" + String.join(", ", tender.getDepartments()) + ")");
		}
		else {
			reasons.add("département " + String.join(", ", inZone));
		}
	}

	private static void checkMarketType(Tender tender, FilterCriteria criteria, List<String> reasons,
			List<String> failures) {
		if (criteria.getMarketTypes().isEmpty()) {
			return;
		}
		if (tender.getMarketTypes().isEmpty()) {
			reasons.add("type de marché non indiqué");
			return;
		}
		if (tender.getMarketTypes().stream().noneMatch(criteria.getMarketTypes()::contains)) {
			failures.add("type de marché non suivi (" + labels(tender.getMarketTypes()) + ")");
		}
	}

	private static void checkTimeLeft(Tender tender, FilterCriteria criteria, Instant now, List<String> reasons,
			List<String> failures) {
		Integer minDays = criteria.getMinDaysBeforeDeadline();
		if (minDays == null) {
			return;
		}
		if (tender.getDeadline() == null) {
			reasons.add("date limite non indiquée");
			return;
		}
		long daysLeft = Duration.between(now, tender.getDeadline()).toDays();
		if (tender.getDeadline().isBefore(now)) {
			failures.add("date limite dépassée");
		}
		else if (daysLeft < minDays) {
			failures.add("délai trop court (" + daysLeft + (daysLeft > 1 ? " jours restants)" : " jour restant)"));
		}
	}

	/**
	 * Rule RM6: a missing amount never excludes a tender.
	 */
	private static void checkAmount(Tender tender, FilterCriteria criteria, List<String> reasons,
			List<String> failures) {
		BigDecimal min = criteria.getMinAmount();
		BigDecimal max = criteria.getMaxAmount();
		if (min == null && max == null) {
			return;
		}
		BigDecimal amount = tender.getEstimatedAmount();
		if (amount == null) {
			reasons.add("montant non indiqué");
		}
		else if (min != null && amount.compareTo(min) < 0) {
			failures.add("montant inférieur au minimum");
		}
		else if (max != null && amount.compareTo(max) > 0) {
			failures.add("montant supérieur au maximum");
		}
	}

	/**
	 * Keywords are looked for in the title, the description and the lots.
	 */
	private static String searchableText(Tender tender) {
		StringBuilder text = new StringBuilder(tender.getTitle());
		if (tender.getDescription() != null) {
			text.append('\n').append(tender.getDescription());
		}
		tender.getLots().forEach(lot -> {
			if (lot.getTitle() != null) {
				text.append('\n').append(lot.getTitle());
			}
		});
		return text.toString();
	}

	private static String labels(List<MarketType> types) {
		return types.stream().map(MARKET_TYPE_LABELS::get).collect(Collectors.joining(", "));
	}

	private static String quoted(List<String> keywords) {
		return keywords.stream().map(keyword -> "« " + keyword + " »").collect(Collectors.joining(", "));
	}

}
