package eu.gpefrance.tenders.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import eu.gpefrance.tenders.TestcontainersConfiguration;

/**
 * The login flow of the web interface over real HTTP: CSRF cookie, login with
 * the token in a header, then requests with the session cookie.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class LoginFlowTests {

	@LocalServerPort
	private int port;

	@Autowired
	private AppUserRepository users;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JdbcTemplate jdbc;

	private final CookieManager cookies = new CookieManager();

	private final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).build();

	@BeforeEach
	void createUser() {
		jdbc.execute("TRUNCATE tender_follow, app_user RESTART IDENTITY");
		users.save(new AppUser("marie@gpe.test", "Marie Durand", passwordEncoder.encode("un-mot-de-passe-solide"),
				Role.USER, Instant.now()));
	}

	@Test
	void logsInWithTheCsrfTokenAndUsesTheSession() throws Exception {
		assertThat(send(get("/api/auth/me")).statusCode()).isEqualTo(401);
		assertThat(send(get("/api/auth/csrf")).statusCode()).isEqualTo(204);
		String token = cookie("XSRF-TOKEN");
		assertThat(token).isNotBlank();

		HttpResponse<String> login = send(post("/api/auth/login",
				"{\"email\": \"marie@gpe.test\", \"password\": \"un-mot-de-passe-solide\"}", cookie("XSRF-TOKEN")));
		assertThat(login.statusCode()).isEqualTo(200);
		assertThat(cookie("JSESSIONID")).isNotBlank();

		HttpResponse<String> me = send(get("/api/auth/me"));
		assertThat(me.statusCode()).isEqualTo(200);
		assertThat(me.body()).contains("Marie Durand");

		assertThat(send(post("/api/auth/logout", "", cookie("XSRF-TOKEN"))).statusCode()).isEqualTo(204);
		assertThat(send(get("/api/auth/me")).statusCode()).isEqualTo(401);
	}

	@Test
	void refusesALoginWithoutCsrfToken() throws Exception {
		HttpResponse<String> login = send(post("/api/auth/login",
				"{\"email\": \"marie@gpe.test\", \"password\": \"un-mot-de-passe-solide\"}", null));

		// An anonymous request refused for its missing token gets the authentication entry point
		assertThat(login.statusCode()).isEqualTo(401);
		assertThat(send(get("/api/auth/me")).statusCode()).isEqualTo(401);
	}

	private HttpRequest get(String path) {
		return HttpRequest.newBuilder(uri(path)).GET().build();
	}

	private HttpRequest post(String path, String json, String csrfToken) {
		HttpRequest.Builder request = HttpRequest.newBuilder(uri(path))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(json));
		if (csrfToken != null) {
			request.header("X-XSRF-TOKEN", csrfToken);
		}
		return request.build();
	}

	private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
		return http.send(request, HttpResponse.BodyHandlers.ofString());
	}

	private String cookie(String name) {
		return cookies.getCookieStore()
			.getCookies()
			.stream()
			.filter(cookie -> cookie.getName().equals(name))
			.map(HttpCookie::getValue)
			.findFirst()
			.orElse(null);
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + port + path);
	}

}
