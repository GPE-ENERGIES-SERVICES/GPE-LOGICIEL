package eu.gpefrance.tenders.estimate;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * A cost of one unit of an item, as the interface sends it. A unit cost left
 * empty is to be entered: the item stays without price until then.
 * @param wasteRate share lost (offcuts of cable...), 0.05 for 5 %
 * @param coefficient sale coefficient of this cost only, or null for that of
 * the estimate
 * @param rateCode a rate of GPE: when it is picked, its type, unit and cost
 * are copied by the server
 * @param offerId an offer of a supplier: when it is picked, its net price per
 * unit, references and date are copied by the server
 */
public record ComponentRequest(@NotNull ComponentType type, @NotBlank @Size(max = 300) String label,
		@NotNull @PositiveOrZero @Digits(integer = 11, fraction = 4) BigDecimal quantity,
		@Size(max = 20) String unit, @PositiveOrZero @Digits(integer = 11, fraction = 4) BigDecimal unitCost,
		@DecimalMin("0") @DecimalMax(value = "1", inclusive = false) @Digits(integer = 1,
				fraction = 4) BigDecimal wasteRate,
		@DecimalMin(value = "0", inclusive = false) @Digits(integer = 4, fraction = 4) BigDecimal coefficient,
		@Size(max = 100) String supplierName, @Size(max = 100) String supplierRef,
		@Size(max = 100) String manufacturerRef, LocalDate costDate, @Size(max = 30) String rateCode,
		Long offerId) {

	/** The same cost, its type, unit and cost copied from a rate. */
	ComponentRequest withRate(PricingRate rate) {
		return new ComponentRequest(rate.getType(), label, quantity, rate.getUnit(), rate.getCost(), wasteRate,
				coefficient, null, null, null, null, rate.getCode(), null);
	}

	/** The same cost, bought from a supplier at this price per unit. */
	ComponentRequest withOffer(Long offerId, String supplierName, String supplierRef, String manufacturerRef,
			String unit, BigDecimal unitCost, LocalDate costDate) {
		return new ComponentRequest(ComponentType.SUPPLY, label, quantity, unit, unitCost, wasteRate, coefficient,
				supplierName, supplierRef, manufacturerRef, costDate, null, offerId);
	}

}
