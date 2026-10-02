package eu.gpefrance.tenders.user;

import java.time.Instant;

public record UserResponse(Long id, String email, String displayName, Role role, boolean enabled,
		Instant createdAt) {

	static UserResponse from(AppUser user) {
		return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole(),
				user.isEnabled(), user.getCreatedAt());
	}

}
