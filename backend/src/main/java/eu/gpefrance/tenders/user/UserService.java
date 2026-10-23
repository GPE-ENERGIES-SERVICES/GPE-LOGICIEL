package eu.gpefrance.tenders.user;

import java.time.Clock;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

	private static final Logger log = LoggerFactory.getLogger(UserService.class);

	private final AppUserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final BootstrapAdminProperties bootstrapAdmin;

	private final Clock clock;

	private final ApplicationEventPublisher events;

	public UserService(AppUserRepository users, PasswordEncoder passwordEncoder,
			BootstrapAdminProperties bootstrapAdmin, Clock clock, ApplicationEventPublisher events) {
		this.events = events;
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.bootstrapAdmin = bootstrapAdmin;
		this.clock = clock;
	}

	@EventListener(ApplicationReadyEvent.class)
	@Transactional
	public void createFirstAdmin() {
		if (users.count() > 0) {
			return;
		}
		if (isBlank(bootstrapAdmin.email()) || isBlank(bootstrapAdmin.password())) {
			log.warn("No account exists: set BOOTSTRAP_ADMIN_EMAIL and BOOTSTRAP_ADMIN_PASSWORD to create one");
			return;
		}
		String name = isBlank(bootstrapAdmin.name()) ? "Administrateur" : bootstrapAdmin.name();
		users.save(new AppUser(bootstrapAdmin.email(), name, passwordEncoder.encode(bootstrapAdmin.password()),
				Role.ADMIN, clock.instant()));
		log.info("First administrator account created for {}", bootstrapAdmin.email());
	}

	@Transactional(readOnly = true)
	public List<UserResponse> list() {
		return users.findAllByOrderByDisplayName().stream().map(UserResponse::from).toList();
	}

	@Transactional
	public UserResponse create(CreateUserRequest request) {
		String email = AppUser.normalizeEmail(request.email());
		if (users.existsByEmail(email)) {
			throw new UserRuleException("Un compte existe déjà pour " + email);
		}
		AppUser user = users.save(new AppUser(email, request.displayName(), passwordEncoder.encode(request.password()),
				request.role(), clock.instant()));
		return UserResponse.from(user);
	}

	@Transactional
	public UserResponse update(Long id, UpdateUserRequest request) {
		AppUser user = users.findById(id).orElseThrow(() -> new UserNotFoundException(id));
		boolean losesAdmin = user.getRole() == Role.ADMIN && user.isEnabled()
				&& (request.role() != Role.ADMIN || !request.enabled());
		if (losesAdmin && users.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
			throw new UserRuleException("Il doit rester au moins un administrateur actif");
		}
		boolean wasEnabled = user.isEnabled();
		user.update(request.displayName(), request.role(), request.enabled());
		if (wasEnabled != user.isEnabled()) {
			log.info("Account {} {}", id, user.isEnabled() ? "enabled" : "disabled");
			events.publishEvent(new UserStatusChangedEvent(id, user.isEnabled()));
		}
		if (!isBlank(request.password())) {
			user.changePassword(passwordEncoder.encode(request.password()));
		}
		return UserResponse.from(user);
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

}
