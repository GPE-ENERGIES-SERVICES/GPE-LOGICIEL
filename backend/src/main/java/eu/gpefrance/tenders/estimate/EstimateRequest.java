package eu.gpefrance.tenders.estimate;

import java.math.BigDecimal;
import java.util.Map;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The settings of an estimate. On a change, a VAT rate left empty keeps the
 * current one, and the coefficients not given keep theirs.
 * @param lotNumber the lot estimated, read at the creation only; null for the
 * whole tender
 * @param vatRate 0.20 for 20 %
 * @param coefficients sale coefficient by type of cost: sale = cost x
 * coefficient
 */
public record EstimateRequest(@Size(max = 50) String lotNumber, @Size(max = 200) String label,
		@DecimalMin("0") @DecimalMax(value = "1", inclusive = false) @Digits(integer = 1,
				fraction = 4) BigDecimal vatRate,
		Map<@NotNull ComponentType, @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 4,
				fraction = 4) BigDecimal> coefficients) {

	Map<ComponentType, BigDecimal> coefficientsOrEmpty() {
		return coefficients == null ? Map.of() : coefficients;
	}

}
