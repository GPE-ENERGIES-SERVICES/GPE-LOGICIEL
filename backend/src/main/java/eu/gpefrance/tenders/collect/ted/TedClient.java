package eu.gpefrance.tenders.collect.ted;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import eu.gpefrance.tenders.collect.CollectProperties;
import eu.gpefrance.tenders.collect.HttpRetry;
import eu.gpefrance.tenders.collect.SourceRecord;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Reads the notices of French buyers from the TED Search API (v3), one
 * publication day at a time. Searching published notices is open without
 * account; the fair usage policy of TED allows far more requests than one
 * collection makes (one per 250 notices).
 * <p>
 * Only the fields read by {@link TedNoticeParser} are asked. The links to the
 * notice are kept in French and in the multilingual XML only: the 24
 * languages would triple the size of every stored notice.
 */
@Component
public class TedClient {

	static final int PAGE_SIZE = 250;

	/** The API refuses to page beyond 15,000 results; a day of French notices stays far below. */
	private static final int MAX_RESULTS = 15_000;

	private static final Set<String> KEPT_LANGUAGES = Set.of("FRA", "MUL");

	static final List<String> FIELDS = List.of("publication-number", "notice-identifier", "notice-version",
			"notice-type", "procedure-identifier", "change-notice-version-identifier", "change-description",
			"change-reason-code", "title-proc", "description-proc", "buyer-name", "buyer-city", "buyer-post-code",
			"buyer-profile", "internal-identifier-proc", "place-of-performance-post-code-proc",
			"place-of-performance-post-code-lot", "place-of-performance-subdiv-proc",
			"place-of-performance-subdiv-lot", "place-of-performance-city-proc", "contract-nature-main-proc",
			"procedure-type", "classification-cpv", "estimated-value-proc", "estimated-value-cur-proc",
			"identifier-lot", "title-lot", "publication-date", "deadline-receipt-tender-date-lot",
			"deadline-receipt-tender-time-lot", "deadline-receipt-request-date-lot",
			"deadline-receipt-request-time-lot", "document-url-lot", "submission-url-lot");

	private final RestClient restClient;

	private final Duration retryDelay;

	public TedClient(RestClient.Builder builder, TedProperties properties, CollectProperties collect) {
		this.restClient = builder.baseUrl(properties.baseUrl()).build();
		this.retryDelay = collect.retryDelay();
	}

	public List<SourceRecord> fetchPublishedOn(LocalDate date) {
		List<SourceRecord> records = new ArrayList<>();
		long total;
		int page = 1;
		do {
			int number = page;
			JsonNode result = HttpRetry.once("TED page " + number + " of " + date, retryDelay,
					() -> fetchPage(date, number));
			total = result.path("totalNoticeCount").asLong();
			if (total > MAX_RESULTS) {
				throw new IllegalStateException(
						"TED returned " + total + " notices for " + date + ", more than the API can page through");
			}
			JsonNode notices = result.path("notices");
			for (JsonNode notice : notices.values()) {
				records.add(toRecord(notice));
			}
			if (notices.isEmpty()) {
				break;
			}
			page++;
		}
		while (records.size() < total);
		return records;
	}

	private JsonNode fetchPage(LocalDate date, int page) {
		Map<String, Object> search = Map.of("query",
				"buyer-country=FRA AND publication-date=" + date.format(DateTimeFormatter.BASIC_ISO_DATE), "fields",
				FIELDS, "limit", PAGE_SIZE, "page", page, "scope", "ALL", "paginationMode", "PAGE_NUMBER");
		JsonNode result = restClient.post()
			.uri("/v3/notices/search")
			.contentType(MediaType.APPLICATION_JSON)
			.body(search)
			.retrieve()
			.body(JsonNode.class);
		if (result == null) {
			throw new IllegalStateException("Empty response from TED for " + date);
		}
		return result;
	}

	/**
	 * A notice without publication number is returned all the same: the
	 * collection rejects it without stopping.
	 */
	private static SourceRecord toRecord(JsonNode notice) {
		String number = notice.path("publication-number").asString(null);
		String date = notice.path("publication-date").asString(null);
		if (notice instanceof ObjectNode object && object.get("links") instanceof ObjectNode links) {
			object.set("links", frenchLinks(links));
		}
		return new SourceRecord(number == null || number.isBlank() ? null : number,
				date == null || date.length() < 10 ? null : LocalDate.parse(date.substring(0, 10)),
				notice.toString());
	}

	private static ObjectNode frenchLinks(ObjectNode links) {
		ObjectNode kept = links.objectNode();
		for (Map.Entry<String, JsonNode> format : links.properties()) {
			ObjectNode languages = kept.objectNode();
			for (Map.Entry<String, JsonNode> language : format.getValue().properties()) {
				if (KEPT_LANGUAGES.contains(language.getKey())) {
					languages.set(language.getKey(), language.getValue());
				}
			}
			if (!languages.isEmpty()) {
				kept.set(format.getKey(), languages);
			}
		}
		return kept;
	}

}
