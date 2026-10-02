package eu.gpefrance.tenders.user;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AppUserDetailsService implements UserDetailsService {

	private final AppUserRepository users;

	public AppUserDetailsService(AppUserRepository users) {
		this.users = users;
	}

	@Override
	public UserDetails loadUserByUsername(String email) {
		return users.findByEmail(AppUser.normalizeEmail(email))
			.map(AppUserPrincipal::from)
			.orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
	}

}
