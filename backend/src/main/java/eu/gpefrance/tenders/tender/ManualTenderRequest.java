package eu.gpefrance.tenders.tender;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A tender added by a user: only its name is required.
 */
public record ManualTenderRequest(
		@NotBlank(message = "Donnez un nom à cet appel d'offres.") @Size(max = 500,
				message = "Le nom est trop long.") String title,
		@Size(max = 500, message = "Le nom de l'acheteur est trop long.") String buyer, Instant deadline) {
}
