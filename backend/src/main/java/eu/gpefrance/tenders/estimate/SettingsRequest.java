package eu.gpefrance.tenders.estimate;

import java.math.BigDecimal;
import java.util.Map;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

/**
 * A change of the settings of GPE: a value left empty keeps the current one,
 * the coefficients not given keep theirs. Rates are shares: 0.05 for 5 %.
 * @param agencyFeeRate agency fees, a share of the sale price
 * @param overheadRate overheads, a share of the sale price
 * @param serviceFeeRate service fees, a share of the sale price
 * @param priceIndexRate annual rate updating the prices of the library from their year
 */
public record SettingsRequest(
		@DecimalMin("0") @DecimalMax(value = "1", inclusive = false) @Digits(integer = 1,
				fraction = 4) BigDecimal vatRate,
		Map<@NotNull ComponentType, @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 4,
				fraction = 4) BigDecimal> coefficients,
		@DecimalMin("0") @DecimalMax(value = "1", inclusive = false) @Digits(integer = 1,
				fraction = 4) BigDecimal agencyFeeRate,
		@DecimalMin("0") @DecimalMax(value = "1", inclusive = false) @Digits(integer = 1,
				fraction = 4) BigDecimal overheadRate,
		@DecimalMin("0") @DecimalMax(value = "1", inclusive = false) @Digits(integer = 1,
				fraction = 4) BigDecimal serviceFeeRate,
		@DecimalMin(value = "-1", inclusive = false) @DecimalMax(value = "1", inclusive = false) @Digits(integer = 1,
				fraction = 4) BigDecimal priceIndexRate) {

	Map<ComponentType, BigDecimal> coefficientsOrEmpty() {
		return coefficients == null ? Map.of() : coefficients;
	}

}
