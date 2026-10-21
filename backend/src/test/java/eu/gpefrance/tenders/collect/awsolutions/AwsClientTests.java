package eu.gpefrance.tenders.collect.awsolutions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import eu.gpefrance.tenders.ClockConfiguration;
import eu.gpefrance.tenders.collect.CollectProperties;
import eu.gpefrance.tenders.collect.SourceRecord;
import tools.jackson.databind.json.JsonMapper;

/**
 * The client against a simulated site serving the real pages: the first page
 * of the works published on 20/10/2026 (6 pages), the next pages empty.
 */
class AwsClientTests {

	private static final String BASE = "https://aws.test";

	private static final LocalDate YESTERDAY = LocalDate.of(2026, 10, 20);

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-21T05:00:00Z"), ClockConfiguration.ZONE);

	private static final MediaType HTML = new MediaType("text", "html", StandardCharsets.UTF_8);

	private static final String EMPTY_PAGE = "<html><body></body></html>";

	private MockRestServiceServer server;

	private AwsClient client(int maxPagesPerHour) {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
		return new AwsClient(builder, new AwsProperties(BASE, List.of("T"), Duration.ZERO, maxPagesPerHour, 450),
				new CollectProperties(7, Set.of(), Duration.ZERO), CLOCK);
	}

	@Test
	void searchesTheWorksOfTheDayThenReadsEachNewNotice() {
		AwsClient client = client(90);
		expectSearch(" = 1");
		expectNextPages(2, 6);
		server.expect(manyTimes(), requestTo(Matchers.matchesPattern(BASE + "/Annonces/[A-Z]+-pub-.*")))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess(AwsPagesTests.bytes("notice-mpi.html"), HTML));

		List<SourceRecord> records = client.fetchPublishedOn(YESTERDAY, id -> false);

		assertThat(records).hasSize(10);
		SourceRecord first = records.getFirst();
		assertThat(first.externalId()).isEqualTo("LDM-pub-2026293007#2611031200");
		assertThat(first.publishedAt()).isEqualTo(YESTERDAY);
		var payload = JsonMapper.builder().build().readTree(first.payload());
		assertThat(payload.path("collectedOn").asString()).isEqualTo("2026-10-21");
		assertThat(payload.path("listing").path("reference").asString()).isEqualTo("LAUNAGUET ML3");
		assertThat(payload.path("notice").path("fields").path("Mode").asString()).isEqualTo("Procédure adaptée ouverte");
		server.verify();
	}

	@Test
	void doesNotReadAgainTheNoticesAlreadyStored() {
		AwsClient client = client(90);
		expectSearch(" = 1");
		expectNextPages(2, 6);
		server.expect(never(), requestTo(Matchers.matchesPattern(BASE + "/Annonces/[A-Z]+-pub-.*")));

		assertThat(client.fetchPublishedOn(YESTERDAY, id -> true)).isEmpty();
		server.verify();
	}

	@Test
	void searchesTodayWithItsOwnCriterion() {
		AwsClient client = client(90);
		expectSearch(" = 0");
		server.expect(manyTimes(), requestTo(Matchers.startsWith(BASE + "/Annonces/lister?pager_t=")))
			.andRespond(withSuccess(EMPTY_PAGE, HTML));

		assertThat(client.fetchPublishedOn(YESTERDAY.plusDays(1), id -> true)).isEmpty();
		server.verify();
	}

	@Test
	void readsNothingForAnOlderDay() {
		AwsClient client = client(90);

		assertThat(client.fetchPublishedOn(YESTERDAY.minusDays(1), id -> false)).isEmpty();
		server.verify();
	}

	@Test
	void stopsAtTheLimitOfTheHourAndKeepsWhatWasRead() {
		AwsClient client = client(8);
		expectSearch(" = 1");
		expectNextPages(2, 6);
		server.expect(manyTimes(), requestTo(Matchers.matchesPattern(BASE + "/Annonces/[A-Z]+-pub-.*")))
			.andRespond(withSuccess(AwsPagesTests.bytes("notice-mpi.html"), HTML));

		List<SourceRecord> records = client.fetchPublishedOn(YESTERDAY, id -> false);

		// 1 search + 5 next pages + 2 notices = 8 pages.
		assertThat(records).hasSize(2);
		assertThat(client.fetchPublishedOn(YESTERDAY, id -> false)).isEmpty();
	}

	@Test
	void triesAPageAgainOnceAfterAServerError() {
		AwsClient client = client(90);
		server.expect(once(), requestTo(BASE + "/Annonces/lister")).andRespond(withServerError());
		expectSearch(" = 1");
		expectNextPages(2, 6);
		server.expect(manyTimes(), requestTo(Matchers.matchesPattern(BASE + "/Annonces/[A-Z]+-pub-.*")))
			.andRespond(withSuccess(AwsPagesTests.bytes("notice-mpi.html"), HTML));

		assertThat(client.fetchPublishedOn(YESTERDAY, id -> false)).hasSize(10);
	}

	@Test
	void readsADeadlineAsAKeyOfItsIdentifier() {
		assertThat(AwsClient.deadlineKey("09/11/26 à 12h00")).isEqualTo("2611091200");
		assertThat(AwsClient.deadlineKey("")).isEmpty();
	}

	private void expectSearch(String when) {
		HttpHeaders cookie = new HttpHeaders();
		cookie.add(HttpHeaders.SET_COOKIE, "PHPSESSID=abc123; path=/; HttpOnly");
		server.expect(once(), requestTo(BASE + "/Annonces/lister"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(content().formDataContains(java.util.Map.of("IDE", "EC", "IDN", "T", "dateParution", when)))
			.andRespond(withSuccess(AwsPagesTests.bytes("list-page.html"), HTML).headers(cookie));
	}

	private void expectNextPages(int first, int last) {
		for (int page = first; page <= last; page++) {
			server.expect(once(), requestTo(BASE + "/Annonces/lister?pager_t=" + page))
				.andExpect(header(HttpHeaders.COOKIE, "PHPSESSID=abc123"))
				.andRespond(withSuccess(EMPTY_PAGE, HTML));
		}
	}

}
