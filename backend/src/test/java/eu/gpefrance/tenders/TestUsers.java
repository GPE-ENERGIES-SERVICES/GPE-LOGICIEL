package eu.gpefrance.tenders;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import eu.gpefrance.tenders.user.AppUserPrincipal;
import eu.gpefrance.tenders.user.Role;

/**
 * Logged-in users for web tests.
 */
public final class TestUsers {

	private TestUsers() {
	}

	public static RequestPostProcessor user() {
		return as(new AppUserPrincipal(1L, "marie@gpe.test", "Marie Durand", "", Role.USER, true));
	}

	public static RequestPostProcessor admin() {
		return as(new AppUserPrincipal(2L, "admin@gpe.test", "Admin GPE", "", Role.ADMIN, true));
	}

	private static RequestPostProcessor as(AppUserPrincipal principal) {
		return authentication(
				UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
	}

}
