package eu.gpefrance.tenders.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import eu.gpefrance.tenders.TestcontainersConfiguration;

@SpringBootTest(properties = { "bootstrap-admin.email=Admin@GPE.test", "bootstrap-admin.password=mot-de-passe-initial",
		"bootstrap-admin.name=Admin GPE" })
@Import(TestcontainersConfiguration.class)
class UserServiceTests {

	@Autowired
	private UserService service;

	@Autowired
	private AppUserRepository users;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void cleanDatabase() {
		jdbc.execute("TRUNCATE tender_follow, app_user RESTART IDENTITY");
	}

	@Test
	void createsTheFirstAdministratorOnlyWhenNoAccountExists() {
		service.createFirstAdmin();
		service.createFirstAdmin();

		assertThat(users.findAll()).singleElement().satisfies(admin -> {
			assertThat(admin.getEmail()).isEqualTo("admin@gpe.test");
			assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
			assertThat(passwordEncoder.matches("mot-de-passe-initial", admin.getPasswordHash())).isTrue();
		});
	}

	@Test
	void refusesTwoAccountsWithTheSameEmail() {
		service.create(new CreateUserRequest("marie@gpe.test", "Marie", "un-mot-de-passe-solide", Role.USER));

		assertThatThrownBy(() -> service
			.create(new CreateUserRequest("MARIE@gpe.test", "Marie", "un-mot-de-passe-solide", Role.USER)))
			.isInstanceOf(UserRuleException.class);
	}

	@Test
	void keepsAtLeastOneActiveAdministrator() {
		service.createFirstAdmin();
		Long adminId = users.findByEmail("admin@gpe.test").orElseThrow().getId();

		assertThatThrownBy(() -> service.update(adminId, new UpdateUserRequest("Admin GPE", Role.USER, true, null)))
			.isInstanceOf(UserRuleException.class);
		assertThatThrownBy(() -> service.update(adminId, new UpdateUserRequest("Admin GPE", Role.ADMIN, false, null)))
			.isInstanceOf(UserRuleException.class);

		service.create(new CreateUserRequest("chef@gpe.test", "Chef", "un-mot-de-passe-solide", Role.ADMIN));
		assertThat(service.update(adminId, new UpdateUserRequest("Admin GPE", Role.USER, true, null)).role())
			.isEqualTo(Role.USER);
	}

	@Test
	void changesThePasswordOnlyWhenANewOneIsGiven() {
		UserResponse marie = service
			.create(new CreateUserRequest("marie@gpe.test", "Marie", "un-mot-de-passe-solide", Role.USER));

		service.update(marie.id(), new UpdateUserRequest("Marie D.", Role.USER, true, null));
		assertThat(passwordEncoder.matches("un-mot-de-passe-solide",
				users.findById(marie.id()).orElseThrow().getPasswordHash())).isTrue();

		service.update(marie.id(), new UpdateUserRequest("Marie D.", Role.USER, true, "un-nouveau-mot-de-passe"));
		assertThat(passwordEncoder.matches("un-nouveau-mot-de-passe",
				users.findById(marie.id()).orElseThrow().getPasswordHash())).isTrue();
	}

}
