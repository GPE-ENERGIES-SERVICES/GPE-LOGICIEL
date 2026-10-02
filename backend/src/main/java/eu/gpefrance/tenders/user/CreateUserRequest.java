package eu.gpefrance.tenders.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(@NotBlank @Email @Size(max = 255) String email,
		@NotBlank @Size(max = 100) String displayName,
		@NotNull @Size(min = 12, max = 128, message = PASSWORD_RULE) String password, @NotNull Role role) {

	static final String PASSWORD_RULE = "le mot de passe doit contenir au moins 12 caractères";

}
