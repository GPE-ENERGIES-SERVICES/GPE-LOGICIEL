package eu.gpefrance.tenders.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param password a new password, or {@code null} to keep the current one
 */
public record UpdateUserRequest(@NotBlank @Size(max = 100) String displayName, @NotNull Role role,
		boolean enabled, @Size(min = 12, max = 128, message = CreateUserRequest.PASSWORD_RULE) String password) {
}
