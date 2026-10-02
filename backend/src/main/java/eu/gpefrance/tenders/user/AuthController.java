package eu.gpefrance.tenders.user;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthenticationManager authenticationManager;

	private final SecurityContextRepository securityContextRepository;

	private final SecurityContextHolderStrategy securityContextHolder = SecurityContextHolder
		.getContextHolderStrategy();

	private final SessionAuthenticationStrategy sessionStrategy = new ChangeSessionIdAuthenticationStrategy();

	public AuthController(AuthenticationManager authenticationManager,
			SecurityContextRepository securityContextRepository) {
		this.authenticationManager = authenticationManager;
		this.securityContextRepository = securityContextRepository;
	}

	/**
	 * Makes sure the browser has a CSRF cookie before its first POST.
	 */
	@GetMapping("/csrf")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void csrf(CsrfToken token) {
		token.getToken();
	}

	@PostMapping("/login")
	public CurrentUserResponse login(@Valid @RequestBody LoginRequest login, HttpServletRequest request,
			HttpServletResponse response) {
		Authentication result = authenticationManager
			.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(login.email(), login.password()));
		AppUserPrincipal principal = ((AppUserPrincipal) result.getPrincipal()).withoutPassword();
		Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null,
				principal.getAuthorities());

		sessionStrategy.onAuthentication(authentication, request, response);
		SecurityContext context = securityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		securityContextHolder.setContext(context);
		securityContextRepository.saveContext(context, request, response);
		return CurrentUserResponse.from(principal);
	}

	@GetMapping("/me")
	public CurrentUserResponse me(@AuthenticationPrincipal AppUserPrincipal principal) {
		return CurrentUserResponse.from(principal);
	}

	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<Void> loginFailed() {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
	}

	public record LoginRequest(@NotBlank String email, @NotBlank String password) {
	}

	public record CurrentUserResponse(Long id, String email, String displayName, Role role) {

		static CurrentUserResponse from(AppUserPrincipal principal) {
			return new CurrentUserResponse(principal.id(), principal.email(), principal.displayName(),
					principal.role());
		}

	}

}
