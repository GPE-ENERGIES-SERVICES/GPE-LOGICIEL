package eu.gpefrance.tenders.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import eu.gpefrance.tenders.ClockConfiguration;
import eu.gpefrance.tenders.TestcontainersConfiguration;
import eu.gpefrance.tenders.collect.boamp.BoampClient;
import eu.gpefrance.tenders.collect.boamp.BoampNoticeParser;
import eu.gpefrance.tenders.tender.MarketType;
import eu.gpefrance.tenders.tender.NoticeKind;
import eu.gpefrance.tenders.tender.Tender;
import eu.gpefrance.tenders.tender.TenderDetails;
import eu.gpefrance.tenders.tender.TenderNotice;
import eu.gpefrance.tenders.tender.TenderRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Several sources side by side: BOAMP, with its client mocked, and sources
 * declared for these tests only. TEST publishes notices in a simple format,
 * FAILING cannot be read and PROTECTED may not be read by a program.
 */
@SpringBootTest
@Import({ TestcontainersConfiguration.class, MultiSourceCollectTests.Sources.class })
class MultiSourceCollectTests {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);

	@Autowired
	private CollectService service;

	@Autowired
	private NormalizationService normalization;

	@Autowired
	private RawNoticeRepository rawNotices;

	@Autowired
	private CollectRunRepository runs;

	@Autowired
	private TenderRepository tenders;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private TransactionTemplate transaction;

	@Autowired
	private BoampNoticeParser boampParser;

	@Autowired
	private TestConnector testSource;

	@Autowired
	private ProtectedConnector protectedSource;

	@MockitoBean
	private BoampClient boampClient;

	@TestBean
	private Clock clock;

	static Clock clock() {
		return Clock.fixed(Instant.parse("2026-10-05T08:00:00Z"), ClockConfiguration.ZONE);
	}

	@BeforeEach
	void cleanDatabase() {
		jdbc.execute("TRUNCATE collect_run, tender_follow, tender_notice, lot, tender, raw_notice RESTART IDENTITY CASCADE");
		given(boampClient.fetchPublishedOn(any())).willReturn(List.of());
		testSource.published.clear();
		protectedSource.calls.set(0);
	}

	@Test
	void theSameIdentifierInTwoSourcesMakesTwoTenders() {
		given(boampClient.fetchPublishedOn(TODAY)).willReturn(List.of(boamp("26-1", "APPEL_OFFRE", null)));
		testSource.publish(TODAY, test("26-1", "CALL_FOR_TENDERS", null, "2026-11-10T11:00:00Z"));

		service.collectActive(CollectTrigger.SCHEDULED);

		assertThat(tenders.findAll()).extracting(Tender::getSource, Tender::getExternalId)
			.containsExactlyInAnyOrder(tuple("BOAMP", "26-1"),
					tuple("TEST", "26-1"));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM raw_notice WHERE external_id = '26-1'", Long.class))
			.isEqualTo(2);
	}

	@Test
	void aNoticeIsNeverAttachedToATenderOfAnotherSource() {
		given(boampClient.fetchPublishedOn(TODAY)).willReturn(List.of(boamp("26-1", "APPEL_OFFRE", null)));
		testSource.publish(TODAY, test("26-2", "RECTIFICATION", "26-1", "2026-12-01T11:00:00Z"));

		service.collectActive(CollectTrigger.SCHEDULED);
		service.collectActive(CollectTrigger.SCHEDULED);

		inTransaction(() -> {
			Tender boamp = tenders.findBySourceAndExternalId("BOAMP", "26-1").orElseThrow();
			assertThat(boamp.getNotices()).extracting(TenderNotice::getExternalId).containsExactly("26-1");
			assertThat(boamp.getDeadline()).isNull();
		});
		RawNotice waiting = rawNotices.findAll()
			.stream()
			.filter(notice -> notice.getSource().equals("TEST"))
			.findFirst()
			.orElseThrow();
		assertThat(waiting.getNormalizationNote()).isEqualTo(NormalizationService.INITIAL_NOT_COLLECTED);
		assertThat(waiting.getLinkedIds()).containsExactly("26-1");

		// Its own initial notice comes later: the rectification is applied to the TEST tender only.
		testSource.publish(TODAY.minusDays(3), test("26-1", "CALL_FOR_TENDERS", null, "2026-11-10T11:00:00Z"));
		service.collect("TEST", CollectTrigger.MANUAL, TODAY.minusDays(3));

		inTransaction(() -> {
			Tender test = tenders.findBySourceAndExternalId("TEST", "26-1").orElseThrow();
			assertThat(test.getNotices()).extracting(TenderNotice::getKind)
				.containsExactly(NoticeKind.INITIAL, NoticeKind.RECTIFICATION);
			assertThat(test.getDeadline()).isEqualTo(Instant.parse("2026-12-01T11:00:00Z"));
			assertThat(tenders.findBySourceAndExternalId("BOAMP", "26-1").orElseThrow().getNotices()).hasSize(1);
		});
	}

	@Test
	void aLateInitialNoticeNeverJoinsATenderOfAnotherSource() {
		// Complete rectifications collected first, each citing an initial notice of its own source.
		testSource.publish(TODAY, test("26-5", "COMPLETE_RECTIFICATION", "26-1", null));
		given(boampClient.fetchPublishedOn(TODAY)).willReturn(List.of(boampRectification("26-6", "26-2")));
		service.collectActive(CollectTrigger.SCHEDULED);
		// The identifiers they cite then appear, but in the other source.
		testSource.publish(TODAY, test("26-5", "COMPLETE_RECTIFICATION", "26-1", null),
				test("26-2", "CALL_FOR_TENDERS", null, null));
		given(boampClient.fetchPublishedOn(TODAY))
			.willReturn(List.of(boampRectification("26-6", "26-2"), boamp("26-1", "APPEL_OFFRE", null)));
		service.collectActive(CollectTrigger.SCHEDULED);

		inTransaction(() -> assertThat(tenders.findAll()).hasSize(4)
			.allSatisfy(tender -> assertThat(tender.getNotices()).singleElement()
				.extracting(TenderNotice::getExternalId)
				.isEqualTo(tender.getExternalId())));
		assertThat(tenders.findAll()).extracting(Tender::getSource, Tender::getExternalId)
			.containsExactlyInAnyOrder(tuple("TEST", "26-5"), tuple("TEST", "26-2"), tuple("BOAMP", "26-6"),
					tuple("BOAMP", "26-1"));
	}

	@Test
	void aFailingSourceDoesNotStopTheOthers() {
		testSource.publish(TODAY, test("26-1", "CALL_FOR_TENDERS", null, null));

		List<CollectRun> done = service.collectActive(CollectTrigger.SCHEDULED);

		assertThat(done).extracting(CollectRun::getSource, CollectRun::getStatus)
			.contains(tuple("FAILING", CollectStatus.FAILED),
					tuple("TEST", CollectStatus.SUCCESS),
					tuple("BOAMP", CollectStatus.SUCCESS));
		assertThat(tenders.findBySourceAndExternalId("TEST", "26-1")).isPresent();
	}

	@Test
	void aSourceThatMayNotBeReadByAProgramIsNeverCollected() {
		service.collectActive(CollectTrigger.SCHEDULED);

		assertThat(service.activeSources()).contains("BOAMP", "TEST").doesNotContain("PROTECTED");
		assertThat(protectedSource.calls).hasValue(0);
		assertThatThrownBy(() -> service.collect("PROTECTED", CollectTrigger.MANUAL, null))
			.isInstanceOf(CollectSourceException.class)
			.hasMessageContaining("PROTECTED");
		assertThatThrownBy(() -> service.startInBackground("PROTECTED", CollectTrigger.MANUAL, null))
			.isInstanceOf(CollectSourceException.class);
		assertThatThrownBy(() -> service.collect("UNKNOWN", CollectTrigger.MANUAL, null))
			.isInstanceOf(CollectSourceException.class);
		assertThat(protectedSource.calls).hasValue(0);
		assertThat(runs.findAll()).extracting(CollectRun::getSource).doesNotContain("PROTECTED");
	}

	@Test
	void eachSourceHasItsOwnRunAndReport() {
		given(boampClient.fetchPublishedOn(TODAY)).willReturn(List.of(boamp("26-1", "APPEL_OFFRE", null)));
		testSource.publish(TODAY, test("26-1", "CALL_FOR_TENDERS", null, null),
				test("26-2", "CALL_FOR_TENDERS", null, null));

		service.collect("TEST", CollectTrigger.MANUAL, TODAY);

		assertThat(runs.findAll()).singleElement().satisfies(run -> {
			assertThat(run.getSource()).isEqualTo("TEST");
			assertThat(run.getNoticesReceived()).isEqualTo(2);
			assertThat(run.getNoticesNew()).isEqualTo(2);
		});
		assertThat(tenders.findAll()).extracting(Tender::getSource).containsOnly("TEST");

		// A BOAMP notice stored but not normalized yet is left to the BOAMP run.
		transaction.executeWithoutResult(status -> rawNotices.insertIfAbsent("BOAMP", "26-1",
				boamp("26-1", "APPEL_OFFRE", null).payload(), TODAY));
		assertThat(normalization.normalizePending("TEST").total()).isZero();
		assertThat(normalization.normalizePending("BOAMP")).isEqualTo(new NormalizationReport(1, 0, 0, 0));
	}

	@Test
	void aReimportOfEverySourceCreatesNoDuplicate() {
		given(boampClient.fetchPublishedOn(TODAY)).willReturn(List.of(boamp("26-1", "APPEL_OFFRE", null)));
		testSource.publish(TODAY, test("26-1", "CALL_FOR_TENDERS", null, null));
		service.collectActive(CollectTrigger.SCHEDULED);

		service.collectActive(CollectTrigger.SCHEDULED);
		service.collect("TEST", CollectTrigger.MANUAL, TODAY.minusDays(30));

		assertThat(rawNotices.count()).isEqualTo(2);
		assertThat(tenders.count()).isEqualTo(2);
	}

	/**
	 * V13 fills the linked notices of the BOAMP notices collected before it as
	 * the parser reads them; the notices waiting for their initial notice are
	 * then applied without reading the BOAMP format again.
	 */
	@Test
	void theMigrationKeepsTheNoticesWaitingForTheirInitialNotice() {
		List<Long> ids = new ArrayList<>();
		for (String fixture : List.of("award", "cancellation", "fns-rectification", "eforms-change", "fns-initial",
				"eforms-works", "mapa")) {
			String payload = fixture(fixture);
			ids.add(jdbc.queryForObject("""
					INSERT INTO raw_notice (source, external_id, payload, published_at, normalized_at, normalization_note)
					VALUES ('BOAMP', ?, CAST(? AS jsonb), DATE '2026-10-01', now(), ?) RETURNING id""", Long.class,
					fixture, payload, NormalizationService.INITIAL_NOT_COLLECTED));
		}

		jdbc.execute(migrationUpdate());

		for (Long id : ids) {
			String payload = jdbc.queryForObject("SELECT payload::text FROM raw_notice WHERE id = ?", String.class, id);
			List<String> stored = jdbc.queryForList("SELECT unnest(linked_ids) FROM raw_notice WHERE id = ?",
					String.class, id);
			assertThat(new HashSet<>(stored)).as("linked notices of raw notice %s", id)
				.isEqualTo(new HashSet<>(boampParser.parse(payload).linkedIds()));
		}
		Long award = ids.getFirst();
		List<String> awardLinks = boampParser.parse(fixture("award")).linkedIds();
		assertThat(awardLinks).isNotEmpty();

		// The initial notice of the award is collected: the award is applied to it.
		given(boampClient.fetchPublishedOn(TODAY)).willReturn(List.of(boamp(awardLinks.getFirst(), "APPEL_OFFRE", null)));
		service.collect("BOAMP", CollectTrigger.MANUAL, TODAY);

		assertThat(rawNotices.findById(award).orElseThrow().getNormalizationNote()).isNull();
		inTransaction(() -> assertThat(
				tenders.findBySourceAndExternalId("BOAMP", awardLinks.getFirst()).orElseThrow().getNotices())
			.extracting(TenderNotice::getKind)
			.contains(NoticeKind.AWARD));
	}

	private void inTransaction(Runnable assertions) {
		transaction.executeWithoutResult(status -> assertions.run());
	}

	private static String migrationUpdate() {
		String migration = resource("/db/migration/V13__store_linked_notices.sql");
		String update = migration.substring(migration.indexOf("UPDATE raw_notice"));
		return update.substring(0, update.indexOf(';'));
	}

	private static String fixture(String name) {
		return resource("/boamp/" + name + ".json");
	}

	private static String resource(String path) {
		try (InputStream in = MultiSourceCollectTests.class.getResourceAsStream(path)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static SourceRecord boamp(String idweb, String nature, String linkedId) {
		String json = """
				{"idweb": "%s", "nature": "%s", "etat": "INITIAL", "objet": "Travaux %s",
				 "dateparution": "2026-10-05", "annonce_lie": %s, "donnees": "{}"}
				""".formatted(idweb, nature, idweb, linkedId == null ? "null" : "[\"" + linkedId + "\"]");
		return new SourceRecord(idweb, TODAY, json);
	}

	/** A BOAMP complete rectification citing an initial notice. */
	private static SourceRecord boampRectification(String idweb, String linkedId) {
		String json = """
				{"idweb": "%s", "nature": "APPEL_OFFRE", "etat": "RECTIFICATIF", "objet": "Travaux %s",
				 "dateparution": "2026-10-05", "annonce_lie": ["%s"], "donnees": "{}"}
				""".formatted(idweb, idweb, linkedId);
		return new SourceRecord(idweb, TODAY, json);
	}

	private static SourceRecord test(String id, String type, String linkedId, String deadline) {
		String json = """
				{"id": "%s", "type": "%s", "linked": %s, "title": "Marché %s", "deadline": %s}
				""".formatted(id, type, linkedId == null ? "[]" : "[\"" + linkedId + "\"]", id,
				deadline == null ? "null" : "\"" + deadline + "\"");
		return new SourceRecord(id, TODAY, json);
	}

	@TestConfiguration
	static class Sources {

		@Bean
		TestConnector testConnector() {
			return new TestConnector();
		}

		@Bean
		SourceParser testParser() {
			return new TestParser();
		}

		@Bean
		SourceConnector failingConnector() {
			return new SourceConnector() {

				@Override
				public String source() {
					return "FAILING";
				}

				@Override
				public SourceAccess access() {
					return SourceAccess.AUTOMATED;
				}

				@Override
				public List<SourceRecord> fetchPublishedOn(LocalDate day) {
					throw new IllegalStateException("Source unavailable");
				}

			};
		}

		@Bean
		SourceParser failingParser() {
			return new SourceParser() {

				@Override
				public String source() {
					return "FAILING";
				}

				@Override
				public SourceNotice parse(String payload) {
					throw new IllegalStateException("Nothing to read");
				}

			};
		}

		@Bean
		ProtectedConnector protectedConnector() {
			return new ProtectedConnector();
		}

	}

	/** Publishes the notices given by the tests, day by day. */
	static class TestConnector implements SourceConnector {

		final Map<LocalDate, List<SourceRecord>> published = new ConcurrentHashMap<>();

		void publish(LocalDate day, SourceRecord... records) {
			published.put(day, List.of(records));
		}

		@Override
		public String source() {
			return "TEST";
		}

		@Override
		public SourceAccess access() {
			return SourceAccess.AUTOMATED;
		}

		@Override
		public List<SourceRecord> fetchPublishedOn(LocalDate day) {
			return published.getOrDefault(day, List.of());
		}

	}

	/** Reads {"id", "type", "linked", "title", "deadline"}. */
	static class TestParser implements SourceParser {

		private final JsonMapper mapper = JsonMapper.builder().build();

		@Override
		public String source() {
			return "TEST";
		}

		@Override
		public SourceNotice parse(String payload) {
			JsonNode notice = mapper.readTree(payload);
			List<String> linked = new ArrayList<>();
			notice.get("linked").values().forEach(id -> linked.add(id.asString()));
			String deadline = notice.get("deadline").isNull() ? null : notice.get("deadline").asString();
			TenderDetails details = new TenderDetails(notice.get("title").asString(), null, null, null, null,
					List.of("13"), List.of(MarketType.WORKS), null, List.of(), null, LocalDate.of(2026, 10, 5),
					deadline == null ? null : Instant.parse(deadline), null, null, List.of());
			return new SourceNotice(notice.get("id").asString(), NoticeType.valueOf(notice.get("type").asString()),
					linked, details, null);
		}

	}

	/** A source protected against programs: reading it is a failure of the test. */
	static class ProtectedConnector implements SourceConnector {

		final AtomicInteger calls = new AtomicInteger();

		@Override
		public String source() {
			return "PROTECTED";
		}

		@Override
		public SourceAccess access() {
			return SourceAccess.NOT_AUTOMATABLE;
		}

		@Override
		public List<SourceRecord> fetchPublishedOn(LocalDate day) {
			calls.incrementAndGet();
			return List.of();
		}

	}

}
