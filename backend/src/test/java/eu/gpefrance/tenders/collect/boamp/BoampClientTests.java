package eu.gpefrance.tenders.collect.boamp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.startsWith;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

class BoampClientTests {

	private static final String BASE_URL = "https://boamp.test/api";

	private MockRestServiceServer server;

	private BoampClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		client = new BoampClient(builder, new BoampProperties(BASE_URL, 7));
	}

	@Test
	void fetchesEveryPageOfTheDay() {
		server.expect(requestTo(startsWith(BASE_URL + "/records")))
			.andExpect(queryParam("where", "dateparution%20%3D%20date%272026-10-02%27"))
			.andExpect(queryParam("offset", "0"))
			.andRespond(withSuccess(page(150, "26-1", "26-2"), MediaType.APPLICATION_JSON));
		server.expect(requestTo(startsWith(BASE_URL + "/records")))
			.andExpect(queryParam("offset", "2"))
			.andRespond(withSuccess(page(3, "26-3"), MediaType.APPLICATION_JSON));

		List<BoampRecord> records = client.fetchPublishedOn(LocalDate.of(2026, 10, 2));

		assertThat(records).extracting(BoampRecord::idweb).containsExactly("26-1", "26-2", "26-3");
		assertThat(records.getFirst().publishedAt()).isEqualTo(LocalDate.of(2026, 10, 2));
		assertThat(records.getFirst().json()).contains("\"objet\":\"Travaux 26-1\"");
		server.verify();
	}

	@Test
	void stopsWhenTheApiReturnsNoMoreResults() {
		server.expect(requestTo(startsWith(BASE_URL + "/records")))
			.andRespond(withSuccess(page(5, "26-1"), MediaType.APPLICATION_JSON));
		server.expect(requestTo(startsWith(BASE_URL + "/records")))
			.andRespond(withSuccess(page(5), MediaType.APPLICATION_JSON));

		assertThat(client.fetchPublishedOn(LocalDate.of(2026, 10, 2))).hasSize(1);
		server.verify();
	}

	@Test
	void propagatesServerErrors() {
		server.expect(requestTo(startsWith(BASE_URL + "/records"))).andRespond(withServerError());

		assertThatThrownBy(() -> client.fetchPublishedOn(LocalDate.of(2026, 10, 2)))
			.isInstanceOf(HttpServerErrorException.class);
	}

	@Test
	void refusesADayItCannotPageThrough() {
		server.expect(requestTo(startsWith(BASE_URL + "/records")))
			.andRespond(withSuccess(page(20_000, "26-1"), MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> client.fetchPublishedOn(LocalDate.of(2026, 10, 2)))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("20000");
	}

	private static String page(int total, String... idwebs) {
		StringBuilder results = new StringBuilder();
		for (String idweb : idwebs) {
			if (!results.isEmpty()) {
				results.append(',');
			}
			results.append("""
					{"idweb":"%s","dateparution":"2026-10-02","objet":"Travaux %s"}""".formatted(idweb, idweb));
		}
		return """
				{"total_count":%d,"results":[%s]}""".formatted(total, results);
	}

}
