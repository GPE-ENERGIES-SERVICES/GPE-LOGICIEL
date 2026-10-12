package eu.gpefrance.tenders.collect.ted;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.twice;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import eu.gpefrance.tenders.collect.CollectProperties;
import eu.gpefrance.tenders.collect.SourceRecord;

class TedClientTests {

	private static final String SEARCH = "https://ted.test/v3/notices/search";

	private static final LocalDate DAY = LocalDate.of(2026, 10, 9);

	private MockRestServiceServer server;

	private TedClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		client = new TedClient(builder, new TedProperties("https://ted.test"),
				new CollectProperties(7, Set.of(), Duration.ZERO));
	}

	@Test
	void searchesTheFrenchNoticesOfTheDayPageByPage() {
		server.expect(requestTo(SEARCH))
			.andExpect(method(HttpMethod.POST))
			.andExpect(content().json("""
					{"query": "buyer-country=FRA AND publication-date=20261009", "page": 1, "limit": 250,
					 "scope": "ALL", "paginationMode": "PAGE_NUMBER"}"""))
			.andRespond(withSuccess(page(3, "1-2026", "2-2026"), MediaType.APPLICATION_JSON));
		server.expect(requestTo(SEARCH))
			.andExpect(content().json("{\"page\": 2}"))
			.andRespond(withSuccess(page(3, "3-2026"), MediaType.APPLICATION_JSON));

		List<SourceRecord> records = client.fetchPublishedOn(DAY);

		assertThat(records).extracting(SourceRecord::externalId).containsExactly("1-2026", "2-2026", "3-2026");
		assertThat(records.getFirst().publishedAt()).isEqualTo(DAY);
		server.verify();
	}

	@Test
	void keepsTheLinksInFrenchOnly() {
		server.expect(requestTo(SEARCH)).andRespond(withSuccess("""
				{"totalNoticeCount": 1, "notices": [{"publication-number": "1-2026",
				 "publication-date": "2026-10-09+02:00", "links": {
				   "xml": {"MUL": "https://ted.europa.eu/en/notice/1-2026/xml"},
				   "html": {"DEU": "https://ted.europa.eu/de/notice/-/detail/1-2026",
				            "FRA": "https://ted.europa.eu/fr/notice/-/detail/1-2026"},
				   "pdf": {"ENG": "https://ted.europa.eu/en/notice/1-2026/pdf"}}}]}""",
				MediaType.APPLICATION_JSON));

		String payload = client.fetchPublishedOn(DAY).getFirst().payload();

		assertThat(payload).contains("/fr/notice/-/detail/1-2026", "/en/notice/1-2026/xml")
			.doesNotContain("/de/", "pdf");
	}

	@Test
	void returnsANoticeWithoutPublicationNumberAsInvalid() {
		server.expect(requestTo(SEARCH)).andRespond(withSuccess("""
				{"totalNoticeCount": 2, "notices": [{"publication-date": "2026-10-09+02:00"},
				 {"publication-number": "1-2026", "publication-date": "2026-10-09+02:00"}]}""",
				MediaType.APPLICATION_JSON));

		assertThat(client.fetchPublishedOn(DAY)).extracting(SourceRecord::isValid).containsExactly(false, true);
	}

	@Test
	void stopsWhenTheApiReturnsNoMoreNotices() {
		server.expect(requestTo(SEARCH)).andRespond(withSuccess(page(5, "1-2026"), MediaType.APPLICATION_JSON));
		server.expect(requestTo(SEARCH)).andRespond(withSuccess(page(5), MediaType.APPLICATION_JSON));

		assertThat(client.fetchPublishedOn(DAY)).hasSize(1);
		server.verify();
	}

	@Test
	void triesAPageAgainOnceAfterAServerError() {
		server.expect(once(), requestTo(SEARCH)).andRespond(withServerError());
		server.expect(once(), requestTo(SEARCH)).andRespond(withSuccess(page(1, "1-2026"), MediaType.APPLICATION_JSON));

		assertThat(client.fetchPublishedOn(DAY)).hasSize(1);
		server.verify();
	}

	@Test
	void failsWhenTheServerErrorLasts() {
		server.expect(twice(), requestTo(SEARCH)).andRespond(withServerError());

		assertThatThrownBy(() -> client.fetchPublishedOn(DAY)).isInstanceOf(HttpServerErrorException.class);
		server.verify();
	}

	@Test
	void refusesADayItCannotPageThrough() {
		server.expect(requestTo(SEARCH)).andRespond(withSuccess(page(20_000, "1-2026"), MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> client.fetchPublishedOn(DAY)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("20000");
	}

	private static String page(int total, String... numbers) {
		StringBuilder notices = new StringBuilder();
		for (String number : numbers) {
			if (!notices.isEmpty()) {
				notices.append(',');
			}
			notices.append("""
					{"publication-number": "%s", "publication-date": "2026-10-09+02:00", "notice-type": "cn-standard"}"""
				.formatted(number));
		}
		return """
				{"totalNoticeCount": %d, "notices": [%s]}""".formatted(total, notices);
	}

}
