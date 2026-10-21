package eu.gpefrance.tenders.collect.awsolutions;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Predicate;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import eu.gpefrance.tenders.collect.CollectProperties;
import eu.gpefrance.tenders.collect.HttpRetry;
import eu.gpefrance.tenders.collect.SourceRecord;
import tools.jackson.databind.node.ObjectNode;

/**
 * Reads the notices of calls for tenders published on marches-publics.info
 * (AW Solutions) from its public search, for the natures of contract chosen:
 * the list of the notices published today or yesterday, the only days the
 * search can select, then the page of each notice not stored yet.
 * <p>
 * The reading stays below the limits of the terms of use of the site (see
 * {@link AwsProperties}): a pause before every page, and no more page once
 * the limit of the hour or of the day is reached; the notices left are read
 * at the next collection while they are still of today or yesterday. A
 * notice is stored under the name of its page and the deadline shown in the
 * list: a changed deadline makes the notice read again.
 */
@Component
public class AwsClient {

	private static final Logger log = LoggerFactory.getLogger(AwsClient.class);

	private static final String SEARCH = "/Annonces/lister";

	private static final DateTimeFormatter LIST_DATE = DateTimeFormatter.ofPattern("dd/MM/yy");

	private final RestClient restClient;

	private final AwsProperties properties;

	private final Duration retryDelay;

	private final Clock clock;

	/** When each page was read, over the last 24 hours. */
	private final Deque<Instant> reads = new ArrayDeque<>();

	public AwsClient(RestClient.Builder builder, AwsProperties properties, CollectProperties collect, Clock clock) {
		this.restClient = builder.clone()
			.baseUrl(properties.baseUrl())
			.defaultHeader(HttpHeaders.USER_AGENT, "GPE-LOGICIEL (veille interne)")
			.build();
		this.properties = properties;
		this.retryDelay = collect.retryDelay();
		this.clock = clock;
	}

	/**
	 * The notices published on the given day, today or yesterday; nothing for
	 * an older day, which the search cannot select.
	 * @param known whether a notice is already stored, by its identifier
	 */
	public List<SourceRecord> fetchPublishedOn(LocalDate day, Predicate<String> known) {
		LocalDate today = LocalDate.now(clock);
		String when = day.equals(today) ? " = 0" : day.equals(today.minusDays(1)) ? " = 1" : null;
		if (when == null) {
			return List.of();
		}
		List<SourceRecord> records = new ArrayList<>();
		try {
			for (String nature : properties.natures()) {
				for (ObjectNode listed : search(nature, when)) {
					if (day.format(LIST_DATE).equals(listed.path("publishedOn").asString(null))) {
						SourceRecord record = read(listed, today, known);
						if (record != null) {
							records.add(record);
						}
					}
				}
			}
		}
		catch (LimitReachedException ex) {
			log.warn("marches-publics.info: {}; {} notices of {} read, the others at the next collection",
					ex.getMessage(), records.size(), day);
		}
		return records;
	}

	private List<ObjectNode> search(String nature, String when) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("IDE", "EC");
		form.add("IDN", nature);
		form.add("IDP", "X");
		form.add("IDR", "X");
		form.add("dateParution", when);
		form.add("Rechercher", "Rechercher");
		throttle();
		ResponseEntity<byte[]> first = HttpRetry.once("marches-publics.info search", retryDelay, () -> restClient.post()
			.uri(SEARCH)
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.body(form)
			.retrieve()
			.toEntity(byte[].class));
		String session = session(first.getHeaders());
		Document page = parse(first.getBody(), SEARCH);
		List<ObjectNode> listed = new ArrayList<>(AwsPages.listing(page));
		int pages = AwsPages.pageCount(page);
		for (int number = 2; number <= pages; number++) {
			String uri = SEARCH + "?pager_t=" + number;
			throttle();
			byte[] next = HttpRetry.once("marches-publics.info page " + number, retryDelay, () -> restClient.get()
				.uri(uri)
				.headers(headers -> {
					if (session != null) {
						headers.add(HttpHeaders.COOKIE, session);
					}
				})
				.retrieve()
				.body(byte[].class));
			listed.addAll(AwsPages.listing(parse(next, uri)));
		}
		return listed;
	}

	/**
	 * The notice with its page, or null when it is already stored. A notice
	 * without page is returned without identifier: the collection rejects it.
	 */
	private SourceRecord read(ObjectNode listed, LocalDate today, Predicate<String> known) {
		String noticeId = listed.path("noticeId").asString(null);
		String noticeUrl = listed.path("noticeUrl").asString(null);
		LocalDate publishedOn = LocalDate.parse(listed.path("publishedOn").asString(), LIST_DATE);
		ObjectNode payload = listed.objectNode();
		payload.put("collectedOn", today.toString());
		payload.set("listing", listed);
		if (noticeId == null || noticeUrl == null) {
			return new SourceRecord(null, publishedOn, payload.toString());
		}
		String id = noticeId + "#" + deadlineKey(listed.path("deadline").asString(""));
		if (known.test(id)) {
			return null;
		}
		throttle();
		byte[] body = HttpRetry.once("marches-publics.info notice " + noticeId, retryDelay,
				() -> restClient.get().uri(noticeUrl).retrieve().body(byte[].class));
		payload.set("notice", AwsPages.notice(parse(body, noticeUrl)));
		return new SourceRecord(id, publishedOn, payload.toString());
	}

	/**
	 * Waits the pause, then counts the page about to be read; refuses when
	 * the limit of the hour or of the day is reached.
	 */
	private synchronized void throttle() {
		Instant now = clock.instant();
		while (!reads.isEmpty() && reads.peekFirst().isBefore(now.minus(Duration.ofDays(1)))) {
			reads.removeFirst();
		}
		Instant hourAgo = now.minus(Duration.ofHours(1));
		long lastHour = reads.stream().filter(read -> read.isAfter(hourAgo)).count();
		if (lastHour >= properties.maxPagesPerHour()) {
			throw new LimitReachedException(lastHour + " pages read in the last hour");
		}
		if (reads.size() >= properties.maxPagesPerDay()) {
			throw new LimitReachedException(reads.size() + " pages read in the last 24 hours");
		}
		if (!reads.isEmpty()) {
			pause();
		}
		reads.addLast(clock.instant());
	}

	private void pause() {
		if (properties.pause().isZero()) {
			return;
		}
		try {
			Thread.sleep(properties.pause());
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new LimitReachedException("interrupted");
		}
	}

	private Document parse(byte[] body, String uri) {
		if (body == null) {
			throw new IllegalStateException("Empty response from marches-publics.info for " + uri);
		}
		try {
			return Jsoup.parse(new ByteArrayInputStream(body), null, properties.baseUrl() + uri);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/** The session cookie set by the search, which the next pages of results need. */
	private static String session(HttpHeaders headers) {
		for (String cookie : headers.getOrEmpty(HttpHeaders.SET_COOKIE)) {
			if (cookie.startsWith("PHPSESSID=")) {
				return cookie.split(";", 2)[0];
			}
		}
		return null;
	}

	/** "09/11/26 à 12h00" read as "2611091200"; empty without deadline. */
	static String deadlineKey(String deadline) {
		String digits = deadline.replaceAll("\\D", "");
		return digits.length() == 10 ? digits.substring(4, 6) + digits.substring(2, 4) + digits.substring(0, 2)
				+ digits.substring(6) : digits;
	}

	private static final class LimitReachedException extends RuntimeException {

		LimitReachedException(String message) {
			super(message);
		}

	}

}
