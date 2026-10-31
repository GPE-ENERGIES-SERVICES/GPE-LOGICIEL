package eu.gpefrance.tenders.estimate;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * A rate of GPE as the interface sends it.
 * @param code short name, such as ELEC or CHEF
 */
public record RateRequest(@NotBlank @Size(max = 30) @Pattern(regexp = "[A-Za-z0-9_-]+") String code,
		@NotBlank @Size(max = 200) String label, @NotNull ComponentType type, @NotBlank @Size(max = 20) String unit,
		@NotNull @PositiveOrZero @Digits(integer = 11, fraction = 4) BigDecimal cost, Boolean active) {
}
