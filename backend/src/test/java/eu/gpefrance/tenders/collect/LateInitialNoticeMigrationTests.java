package eu.gpefrance.tenders.collect;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import eu.gpefrance.tenders.TestcontainersConfiguration;
import eu.gpefrance.tenders.tender.LotDetails;
import eu.gpefrance.tenders.tender.MarketType;
import eu.gpefrance.tenders.tender.NoticeKind;
import eu.gpefrance.tenders.tender.Tender;
import eu.gpefrance.tenders.tender.TenderDetails;
import eu.gpefrance.tenders.tender.TenderNotice;
import eu.gpefrance.tenders.tender.TenderRepository;

/**
 * V14 merges the tender made by a late initial notice into the tender of the
 * complete rectification citing it. The data reproduce the 16 pairs found in
 * the database: 12 initial notices alone, 1 initial notice with a later
 * rectification, and 3 chains of complete rectifications, which are not
 * merged.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class LateInitialNoticeMigrationTests {

	private static final Instant NOW = Instant.parse("2026-10-05T08:00:00Z");

	private static final LocalDate RECTIFIED = LocalDate.of(2026, 10, 2);

	private static final LocalDate INITIAL = LocalDate.of(2026, 9, 20);

	@Autowired
	private TenderRepository tenders;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private TransactionTemplate transaction;

	@BeforeEach
	void cleanDatabase() {
		jdbc.execute("TRUNCATE tender_follow, tender_notice, lot, tender, raw_notice RESTART IDENTITY CASCADE");
		jdbc.execute("""
				INSERT INTO app_user (id, email, display_name, password_hash, role, enabled, created_at)
				VALUES (1, 'marie@gpe.test', 'Marie Durand', 'x', 'USER', true, now())
				ON CONFLICT DO NOTHING
				""");
	}

	@Test
	void mergesTheTendersOfLateInitialNoticesAndLeavesTheChainsOfRectifications() {
		List<Long> kept = new ArrayList<>();
		List<Long> removed = new ArrayList<>();
		for (int i = 1; i <= 12; i++) {
			// Lots on both sides, on neither, or on the initial notice only.
			int rectificationLots = i % 3 == 0 ? 0 : (i % 3 == 1 ? 4 : 0);
			int initialLots = i % 3 == 0 ? 3 : (i % 3 == 1 ? 4 : 0);
			kept.add(tender("26-9" + i, NoticeKind.RECTIFICATION, null, RECTIFIED, rectificationLots, "26-1" + i).getId());
			removed.add(tender("26-1" + i, NoticeKind.INITIAL, "Ville " + i, INITIAL, initialLots).getId());
		}
		Long keptWithRectification = tender("26-920", NoticeKind.RECTIFICATION, null, RECTIFIED, 0, "26-120").getId();
		Tender initialWithRectification = tender("26-120", NoticeKind.INITIAL, "Ville 20", INITIAL, 0);
		attach(initialWithRectification.getId(), "26-121", NoticeKind.RECTIFICATION, "26-120");
		List<Long> chains = new ArrayList<>();
		for (int i = 1; i <= 3; i++) {
			chains.add(tender("26-95" + i, NoticeKind.RECTIFICATION, null, RECTIFIED, 2, "26-85" + i).getId());
			chains.add(tender("26-85" + i, NoticeKind.RECTIFICATION, "Ville", INITIAL, 2, "26-75" + i).getId());
		}
		long notices = count("tender_notice");
		long rawNotices = count("raw_notice");
		assertThat(count("tender")).isEqualTo(32);

		migrate();

		assertThat(count("tender")).isEqualTo(32 - 13);
		assertThat(count("tender_notice")).isEqualTo(notices);
		assertThat(count("raw_notice")).isEqualTo(rawNotices);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM tender WHERE id IN (" + ids(removed) + ")", Long.class))
			.isZero();
		inTransaction(() -> {
			for (int i = 1; i <= 12; i++) {
				Tender tender = tenders.findById(kept.get(i - 1)).orElseThrow();
				assertThat(tender.getNotices()).extracting(TenderNotice::getExternalId)
					.containsExactly("26-1" + i, "26-9" + i);
				assertThat(tender.getLots()).hasSize(i % 3 == 0 ? 3 : (i % 3 == 1 ? 4 : 0));
				assertThat(tender.getBuyer()).isEqualTo("Ville " + i);
				assertThat(tender.getPublishedAt()).isEqualTo(INITIAL);
				assertThat(tender.getExternalId()).isEqualTo("26-9" + i);
			}
			assertThat(tenders.findById(keptWithRectification).orElseThrow().getNotices())
				.extracting(TenderNotice::getExternalId)
				.containsExactlyInAnyOrder("26-120", "26-121", "26-920");
			for (Long chain : chains) {
				assertThat(tenders.findById(chain).orElseThrow().getNotices()).hasSize(1);
			}
		});
		assertThat(jdbc.queryForObject("SELECT count(*) FROM lot l WHERE NOT EXISTS (SELECT 1 FROM tender t WHERE t.id = l.tender_id)",
				Long.class))
			.isZero();

		// A second run finds nothing more to merge.
		migrate();
		assertThat(count("tender")).isEqualTo(32 - 13);
	}

	@Test
	void neverMergesAPairThatHoldsDataOfTheTeamOrDiffers() {
		// The kept tender may hold a decision: it is kept as it is.
		Long decided = tender("26-901", NoticeKind.RECTIFICATION, null, RECTIFIED, 0, "26-101").getId();
		tender("26-101", NoticeKind.INITIAL, "Ville", INITIAL, 0);
		jdbc.update("UPDATE tender SET follow_up_status = 'TO_STUDY', follow_up_comment = 'Visite' WHERE id = ?",
				decided);
		jdbc.update("INSERT INTO tender_follow (user_id, tender_id, created_at) VALUES (1, ?, now())", decided);
		// Refused: decision, follow, status, lot missing from the rectification, cited twice.
		tender("26-902", NoticeKind.RECTIFICATION, null, RECTIFIED, 0, "26-102");
		Long withDecision = tender("26-102", NoticeKind.INITIAL, "Ville", INITIAL, 0).getId();
		jdbc.update("UPDATE tender SET follow_up_status = 'DISMISSED' WHERE id = ?", withDecision);
		tender("26-903", NoticeKind.RECTIFICATION, null, RECTIFIED, 0, "26-103");
		Long followed = tender("26-103", NoticeKind.INITIAL, "Ville", INITIAL, 0).getId();
		jdbc.update("INSERT INTO tender_follow (user_id, tender_id, created_at) VALUES (1, ?, now())", followed);
		tender("26-904", NoticeKind.RECTIFICATION, null, RECTIFIED, 0, "26-104");
		Long closed = tender("26-104", NoticeKind.INITIAL, "Ville", INITIAL, 0).getId();
		jdbc.update("UPDATE tender SET status = 'CLOSED' WHERE id = ?", closed);
		tender("26-905", NoticeKind.RECTIFICATION, null, RECTIFIED, 2, "26-105");
		tender("26-105", NoticeKind.INITIAL, "Ville", INITIAL, 3);
		tender("26-906", NoticeKind.RECTIFICATION, null, RECTIFIED, 0, "26-106");
		tender("26-907", NoticeKind.RECTIFICATION, null, RECTIFIED, 0, "26-106");
		tender("26-106", NoticeKind.INITIAL, "Ville", INITIAL, 0);

		migrate();

		assertThat(count("tender")).isEqualTo(13 - 1);
		assertThat(tenders.findBySourceAndExternalId("BOAMP", "26-101")).isEmpty();
		inTransaction(() -> {
			Tender tender = tenders.findById(decided).orElseThrow();
			assertThat(tender.getNotices()).hasSize(2);
			assertThat(tender.getFollowUpStatus().name()).isEqualTo("TO_STUDY");
			assertThat(tender.getFollowUpComment()).isEqualTo("Visite");
		});
		assertThat(jdbc.queryForObject("SELECT count(*) FROM tender_follow", Long.class)).isEqualTo(2);
		for (String refused : List.of("26-102", "26-103", "26-104", "26-105", "26-106")) {
			assertThat(tenders.findBySourceAndExternalId("BOAMP", refused)).as(refused).isPresent();
		}
	}

	private Tender tender(String externalId, NoticeKind kind, String buyer, LocalDate publishedAt, int lots,
			String... cited) {
		long rawId = raw(externalId, cited);
		List<LotDetails> lotDetails = IntStream.rangeClosed(1, lots)
			.mapToObj(i -> new LotDetails(String.valueOf(i), "Lot " + i, null))
			.toList();
		Tender tender = new Tender("BOAMP", externalId,
				new TenderDetails("Travaux " + externalId, null, buyer, null, null, List.of("13"),
						List.of(MarketType.WORKS), null, List.of(), null, publishedAt, NOW.plus(Duration.ofDays(30)),
						null, null, lotDetails),
				NOW);
		tender.addNotice(rawId, externalId, kind, publishedAt, null, null);
		return tenders.save(tender);
	}

	private void attach(Long tenderId, String externalId, NoticeKind kind, String... cited) {
		long rawId = raw(externalId, cited);
		jdbc.update("""
				INSERT INTO tender_notice (tender_id, raw_notice_id, external_id, kind, published_at, source)
				VALUES (?, ?, ?, ?, DATE '2026-09-25', 'BOAMP')""", tenderId, rawId, externalId, kind.name());
	}

	private long raw(String externalId, String... linkedIds) {
		return jdbc.queryForObject("""
				INSERT INTO raw_notice (source, external_id, payload, published_at, normalized_at, linked_ids)
				VALUES ('BOAMP', ?, '{}', DATE '2026-10-01', now(), CAST(? AS VARCHAR[])) RETURNING id""", Long.class,
				externalId, "{" + String.join(",", linkedIds) + "}");
	}

	private void migrate() {
		jdbc.execute(resource("/db/migration/V14__merge_tenders_of_late_initial_notices.sql"));
	}

	private long count(String table) {
		return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
	}

	private static String ids(List<Long> ids) {
		return String.join(",", ids.stream().map(String::valueOf).toList());
	}

	private void inTransaction(Runnable assertions) {
		transaction.executeWithoutResult(status -> assertions.run());
	}

	private static String resource(String path) {
		try (InputStream in = LateInitialNoticeMigrationTests.class.getResourceAsStream(path)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
