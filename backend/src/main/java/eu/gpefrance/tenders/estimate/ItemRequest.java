package eu.gpefrance.tenders.estimate;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * An item as the interface sends it. {@code fixedUnitPrice} is read for a
 * FIXED item only, {@code textPrice} (PM, inclus...) for a TEXT item only.
 * {@code toCheck}: a user still has to check it (item read from a PDF).
 */
public record ItemRequest(@Size(max = 200) String chapter, @Size(max = 50) String reference,
		@NotBlank @Size(max = 4000) String designation, @Size(max = 20) String unit,
		@PositiveOrZero @Digits(integer = 12, fraction = 3) BigDecimal quantity, @NotNull PriceMode priceMode,
		@PositiveOrZero @Digits(integer = 13, fraction = 2) BigDecimal fixedUnitPrice,
		@Size(max = 30) String textPrice, Boolean option, Boolean toCheck) {

	/**
	 * A line with an entered price may wait for its price, as in a
	 * spreadsheet: it stays without amount until then.
	 */
	void check() {
		if (priceMode == PriceMode.TEXT && clean(textPrice) == null) {
			throw EstimateRuleException.invalid("Un poste sans prix chiffré doit dire pourquoi (PM, inclus...).");
		}
	}

	/** Blank text is no value. */
	static String clean(String text) {
		return text == null || text.isBlank() ? null : text.strip();
	}

}
