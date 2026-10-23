package eu.gpefrance.tenders.alert;

import java.math.BigDecimal;
import java.util.List;

import eu.gpefrance.tenders.tender.MarketType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * A saved search as the interface sends it ({@code AlertInput}). The owner is
 * always the logged-in user, never read from the request.
 */
public record AlertRequest(@NotBlank @Size(max = 100) String name,
		@Size(max = 50) List<@NotBlank @Size(max = 100) String> keywords, @Size(max = 100) String region,
		@Size(max = 120) List<@NotNull @Pattern(regexp = "\\d{2}|2A|2B|97[1-6]") String> departments,
		@Size(max = 3) List<@NotNull MarketType> marketTypes,
		@Size(max = 20) List<@NotNull @Pattern(regexp = "[A-Z_]{2,40}") String> activities,
		@Size(max = 100) List<@NotNull @Pattern(regexp = "\\d{2,8}") String> cpvPrefixes,
		@PositiveOrZero BigDecimal minAmount, @PositiveOrZero BigDecimal maxAmount, Boolean active) {

	List<String> keywordsOrEmpty() {
		return keywords == null ? List.of() : keywords.stream().map(String::strip).distinct().toList();
	}

	List<String> departmentsOrEmpty() {
		return departments == null ? List.of() : departments.stream().distinct().toList();
	}

	List<MarketType> marketTypesOrEmpty() {
		return marketTypes == null ? List.of() : marketTypes.stream().distinct().toList();
	}

	List<String> activitiesOrEmpty() {
		return activities == null ? List.of() : activities.stream().distinct().toList();
	}

	List<String> cpvPrefixesOrEmpty() {
		return cpvPrefixes == null ? List.of() : cpvPrefixes.stream().distinct().toList();
	}

}
