package eu.gpefrance.tenders.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import eu.gpefrance.tenders.TestUsers;
import eu.gpefrance.tenders.TestcontainersConfiguration;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthApiTests {

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private AppUserRepository users;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void createUser() {
		jdbc.execute("TRUNCATE app_user RESTART IDENTITY");
		users.save(new AppUser("Marie@GPE.test", "Marie Durand", passwordEncoder.encode("un-mot-de-passe-solide"),
				Role.USER, Instant.now()));
	}

	@Test
	void logsInAndKeepsTheUserInTheSession() {
		MvcTestResult login = login("marie@gpe.test", "un-mot-de-passe-solide");

		assertThat(login).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{"email": "marie@gpe.test", "displayName": "Marie Durand", "role": "USER"}""");
		MockHttpSession session = (MockHttpSession) login.getMvcResult().getRequest().getSession(false);
		assertThat(mvc.get().uri("/api/auth/me").session(session)).hasStatusOk()
			.bodyJson()
			.extractingPath("$.displayName")
			.isEqualTo("Marie Durand");
	}

	@Test
	void refusesAWrongPassword() {
		assertThat(login("marie@gpe.test", "mauvais-mot-de-passe")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void refusesADisabledAccount() {
		AppUser marie = users.findByEmail("marie@gpe.test").orElseThrow();
		marie.update("Marie Durand", Role.USER, false);
		users.save(marie);

		assertThat(login("marie@gpe.test", "un-mot-de-passe-solide")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void answersUnauthorizedWithoutSession() {
		assertThat(mvc.get().uri("/api/auth/me")).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.get().uri("/api/tenders")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void reservesAccountManagementToAdministrators() {
		assertThat(mvc.get().uri("/api/users").with(TestUsers.user())).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(mvc.get().uri("/api/users").with(TestUsers.admin())).hasStatusOk();
	}

	@Test
	void keepsHealthPublic() {
		assertThat(mvc.get().uri("/actuator/health")).hasStatusOk();
	}

	private MvcTestResult login(String email, String password) {
		return mvc.post()
			.uri("/api/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\": \"" + email + "\", \"password\": \"" + password + "\"}")
			.with(csrf())
			.exchange();
	}

}
