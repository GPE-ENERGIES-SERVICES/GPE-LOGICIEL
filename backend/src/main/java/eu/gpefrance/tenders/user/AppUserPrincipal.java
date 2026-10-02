package eu.gpefrance.tenders.user;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The logged-in user. Kept in the session, so it holds no entity.
 */
public record AppUserPrincipal(Long id, String email, String displayName, String passwordHash, Role role,
		boolean enabled) implements UserDetails {

	static AppUserPrincipal from(AppUser user) {
		return new AppUserPrincipal(user.getId(), user.getEmail(), user.getDisplayName(), user.getPasswordHash(),
				user.getRole(), user.isEnabled());
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
	}

	@Override
	public String getPassword() {
		return passwordHash;
	}

	@Override
	public String getUsername() {
		return email;
	}

	@Override
	public boolean isEnabled() {
		return enabled;
	}

	/**
	 * The password hash is not needed once authenticated and must not stay in
	 * the session.
	 */
	public AppUserPrincipal withoutPassword() {
		return new AppUserPrincipal(id, email, displayName, "", role, enabled);
	}

}
