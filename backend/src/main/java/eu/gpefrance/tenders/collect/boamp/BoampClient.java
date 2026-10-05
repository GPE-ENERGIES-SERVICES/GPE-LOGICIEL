package eu.gpefrance.tenders.collect.boamp;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import eu.gpefrance.tenders.collect.SourceRecord;

import tools.jackson.databind.JsonNode;

/**
 * Reads notices from the BOAMP open data API (Opendatasoft Explore v2.1).
 * Notices are fetched one publication day at a time: the API refuses to page
 * beyond 10,000 results and a single day stays far below that.
 */
@Component
public class BoampClient {

	static final int PAGE_SIZE = 100;

	private static final int MAX_RESULTS = 10_000;

	private final RestClient restClient;

	public BoampClient(RestClient.Builder builder, BoampProperties properties) {
		this.restClient = builder.baseUrl(properties.baseUrl()).build();
	}

	public List<SourceRecord> fetchPublishedOn(LocalDate date) {
		List<SourceRecord> records = new ArrayList<>();
		int offset = 0;
		long total;
		do {
			JsonNode page = fetchPage(date, offset);
			total = page.path("total_count").asLong();
			if (total > MAX_RESULTS) {
				throw new IllegalStateException(
						"BOAMP returned " + total + " notices for " + date + ", more than the API can page through");
			}
			JsonNode results = page.path("results");
			for (JsonNode result : results) {
				records.add(toRecord(result));
			}
			if (results.isEmpty()) {
				break;
			}
			offset += results.size();
		}
		while (offset < total);
		return records;
	}

	private JsonNode fetchPage(LocalDate date, int offset) {
		JsonNode page = restClient.get()
			.uri(uri -> uri.path("/records")
				.queryParam("where", "{where}")
				.queryParam("order_by", "idweb")
				.queryParam("limit", PAGE_SIZE)
				.queryParam("offset", offset)
				.build(Map.of("where", "dateparution = date'" + date + "'")))
			.retrieve()
			.body(JsonNode.class);
		if (page == null) {
			throw new IllegalStateException("Empty response from BOAMP for " + date);
		}
		return page;
	}

	/**
	 * A notice without identifier is returned all the same: the collection
	 * rejects it without stopping, see {@link SourceRecord#isValid()}.
	 */
	private static SourceRecord toRecord(JsonNode result) {
		String idweb = result.path("idweb").asString(null);
		String publishedAt = result.path("dateparution").asString(null);
		return new SourceRecord(idweb == null || idweb.isBlank() ? null : idweb,
				publishedAt == null ? null : LocalDate.parse(publishedAt), result.toString());
	}

}
