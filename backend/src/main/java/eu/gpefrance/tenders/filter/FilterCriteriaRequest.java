package eu.gpefrance.tenders.filter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import eu.gpefrance.tenders.tender.MarketType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record FilterCriteriaRequest(
		@NotNull List<@NotNull @Pattern(regexp = "\\s*(\\d{2}|2[ABab]|97\\d)\\s*",
				message = "département invalide (ex. 69, 2A, 974)") String> departments,
		@NotNull List<@NotNull MarketType> marketTypes,
		@PositiveOrZero @Max(365) Integer minDaysBeforeDeadline,
		@NotNull List<@NotNull @Pattern(regexp = "\\s*\\d{2,8}\\s*",
				message = "début de code CPV invalide (2 à 8 chiffres)") String> cpvPrefixes,
		@NotNull List<@NotNull @Size(max = 100) String> keywords,
		@NotNull List<@NotNull @Size(max = 100) String> excludedKeywords,
		@PositiveOrZero BigDecimal minAmount,
		@PositiveOrZero BigDecimal maxAmount) {

	@AssertTrue(message = "le montant minimum dépasse le maximum")
	boolean isAmountRangeValid() {
		return minAmount == null || maxAmount == null || minAmount.compareTo(maxAmount) <= 0;
	}

	List<String> cleanDepartments() {
		return clean(departments).stream().map(code -> code.toUpperCase(Locale.ROOT)).distinct().toList();
	}

	List<String> cleanCpvPrefixes() {
		return clean(cpvPrefixes);
	}

	List<String> cleanKeywords() {
		return clean(keywords);
	}

	List<String> cleanExcludedKeywords() {
		return clean(excludedKeywords);
	}

	private static List<String> clean(List<String> values) {
		return values.stream().map(String::strip).filter(value -> !value.isEmpty()).distinct().toList();
	}

}
