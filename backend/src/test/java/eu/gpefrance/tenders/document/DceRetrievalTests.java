package eu.gpefrance.tenders.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.atLeastOnce;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterAll;
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
import org.springframework.web.ErrorResponseException;

import eu.gpefrance.tenders.ClockConfiguration;
import eu.gpefrance.tenders.TestcontainersConfiguration;
import eu.gpefrance.tenders.alert.AlertRequest;
import eu.gpefrance.tenders.alert.AlertService;
import eu.gpefrance.tenders.eligibility.EligibilityService;
import eu.gpefrance.tenders.tender.MarketType;
import eu.gpefrance.tenders.tender.Tender;
import eu.gpefrance.tenders.tender.TenderDetails;
import eu.gpefrance.tenders.tender.TenderRepository;

/**
 * The queue of retrieval from start to end, against a local synthetic server
 * ({@link SyntheticDceServer}) and a retriever of the tests only, for the
 * synthetic platform 127.0.0.1, the only one allowed here. No real platform
 * is contacted and nothing is retrieved for real. The worker is called by
 * the tests (the schedule waits an hour).
 */
@SpringBootTest(properties = { "dce.retrieval.allowed-platforms=127.0.0.1", "dce.retrieval.worker-enabled=true",
		"dce.retrieval.concurrency=2", "dce.retrieval.max-attempts=3", "dce.retrieval.retry-delay=1m",
		"dce.retrieval.max-retry-delay=10m", "dce.retrieval.max-file-size=1MB", "dce.retrieval.quota=3MB",
		"documents.storage-dir=target/test-storage-dce" })
@Import({ TestcontainersConfiguration.class, DceRetrievalTests.Synthetic.class })
class DceRetrievalTests {

	private static final Instant NOW = Instant.parse("2026-10-21T08:00:00Z");

	private static final SyntheticDceServer SERVER = new SyntheticDceServer();

	@Autowired
	private DceRetrievalWorker worker;

	@Autowired
	private DceStorageQuota quota;

	@Autowired
	private DceStatusService status;

	@Autowired
	private EligibilityService eligibility;

	@Autowired
	private AlertService alerts;

	@Autowired
	private TenderRepository tenders;

	@Autowired
	private FileStorage storage;

	@Autowired
	private SyntheticDceRetriever retriever;

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoBean
	private DocumentProcessor processor;

	@TestBean
	private Clock clock;

	static Clock clock() {
		return new SettableClock(NOW);
	}

	@TestConfiguration
	static class Synthetic {

		@Bean
		SyntheticDceRetriever syntheticDceRetriever() {
			return new SyntheticDceRetriever();
		}

	}

	@AfterAll
	static void stopServer() {
		SERVER.stop();
	}

	@BeforeEach
	void cleanDatabase() {
		jdbc.execute("TRUNCATE alert, tender_eligibility, dce_storage_reservation, dce_link_event, document_page, "
				+ "document, dossier, dce_link, tender_follow, tender_notice, lot, tender, raw_notice RESTART IDENTITY CASCADE");
		jdbc.execute("""
				INSERT INTO app_user (id, email, display_name, password_hash, role, enabled, created_at)
				VALUES (1, 'marie@gpe.test', 'Marie Durand', 'x', 'USER', true, now())
				ON CONFLICT DO NOTHING
				""");
		((SettableClock) clock).set(NOW);
		SERVER.reset();
		// The storage folder of these tests only.
		deleteFolder(storage.resolve(""));
		alerts.create(new AlertRequest("Éclairage", List.of("éclairage"), null, List.of("13"),
				List.of(MarketType.WORKS), List.of(), List.of(), null, null, true), 1L);
	}

	@Test
	void retrievesTheFileOfAnEligibleTenderOfAnAllowedPlatformAndKeepsItOnce() throws Exception {
		Long link = eligibleLink("26-1", "/ok.pdf");

		assertThat(status(link)).isEqualTo("ELIGIBLE_PENDING");
		assertThat(worker.claim()).isEqualTo(link);
		worker.process(link);

		assertThat(status(link)).isEqualTo("FETCHED");
		assertThat(jdbc.queryForMap("SELECT file_count, attempts FROM dce_link WHERE id = ?", link))
			.containsEntry("file_count", 1)
			.containsEntry("attempts", 1);
		assertThat(jdbc.queryForMap("SELECT origin, dce_link_id, stored_bytes FROM dossier"))
			.containsEntry("origin", "RETRIEVED")
			.containsEntry("dce_link_id", link)
			.containsEntry("stored_bytes", (long) SyntheticDceServer.PDF.length);
		assertThat(jdbc.queryForObject("SELECT sha256 FROM document", String.class)).isEqualTo(sha256());
		assertThat(quota.used()).isEqualTo(SyntheticDceServer.PDF.length);
		assertThat(quota.reserved()).isZero();
		assertThat(temporaryFiles()).isEmpty();
		assertThat(events(link)).containsExactly("ELIGIBLE_PENDING", "IN_PROGRESS", "FETCHED");
		then(processor).should().startInBackground(anyLong());

		// Evaluated again, a retrieved link stays retrieved and is not queued again.
		jdbc.update("UPDATE alert_revision SET revision = revision + 1");
		eligibility.evaluateOutOfDate();
		assertThat(status(link)).isEqualTo("FETCHED");
		assertThat(worker.claim()).isNull();
		assertThat(SERVER.requests("/ok.pdf")).isEqualTo(1);
	}

	@Test
	void theSameFilesFromAnotherLinkMakeNoNewVersion() {
		Tender tender = tender("26-1", "Travaux d'éclairage public");
		Long first = link(tender, "/ok.pdf");
		Long second = link(tender, "/same.pdf");
		eligibility.evaluateOutOfDate();

		process(worker.claim());
		process(worker.claim());

		assertThat(status(first)).isEqualTo("FETCHED");
		assertThat(status(second)).isEqualTo("FETCHED");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM dossier", Long.class)).isEqualTo(1);
		assertThat(detail(second)).contains("identiques à la version 1");
	}

	@Test
	void anArchiveIsKeptWithItsDocuments() {
		Long link = eligibleLink("26-1", "/dce.zip");

		process(worker.claim());

		assertThat(status(link)).isEqualTo("FETCHED");
		assertThat(jdbc.queryForList("SELECT file_name FROM document ORDER BY file_name", String.class))
			.containsExactly("dce.zip/CCTP.pdf", "dce.zip/RC.pdf");
		assertThat(jdbc.queryForObject("SELECT stored_bytes FROM dossier", Long.class))
			.isGreaterThan(2L * SyntheticDceServer.PDF.length);
	}

	@Test
	void aLoginPageInsteadOfTheFileIsRefusedForGood() {
		Long link = eligibleLink("26-1", "/login.pdf");

		process(worker.claim());

		assertThat(status(link)).isEqualTo("PERMANENT_ERROR");
		assertThat(detail(link)).contains("Page web");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM dossier", Long.class)).isZero();
		assertThat(temporaryFiles()).isEmpty();
		assertThat(worker.claim()).isNull();
	}

	@Test
	void anAccessRequiredIsNeverTriedAgainAutomatically() {
		Long link = eligibleLink("26-1", "/denied");

		process(worker.claim());
		((SettableClock) clock).set(NOW.plus(Duration.ofDays(1)));

		assertThat(status(link)).isEqualTo("ACCESS_REQUIRED");
		assertThat(worker.claim()).isNull();
		assertThat(SERVER.requests("/denied")).isEqualTo(1);
	}

	@Test
	void anUnavailableLinkDoesNotStopTheOthers() {
		Long missing = eligibleLink("26-1", "/missing");
		Long ok = eligibleLink("26-2", "/ok.pdf");

		process(worker.claim());
		process(worker.claim());

		assertThat(status(missing)).isEqualTo("LINK_UNAVAILABLE");
		assertThat(status(ok)).isEqualTo("FETCHED");
	}

	@Test
	void aTemporaryErrorIsTriedAgainLaterThenGivenUp() {
		Long link = eligibleLink("26-1", "/flaky");
		SettableClock settable = (SettableClock) clock;

		process(worker.claim());
		assertThat(status(link)).isEqualTo("TEMPORARY_ERROR");
		assertThat(worker.claim()).as("not before its delay").isNull();

		settable.set(NOW.plus(Duration.ofMinutes(1)));
		process(worker.claim());
		assertThat(status(link)).isEqualTo("TEMPORARY_ERROR");
		assertThat(jdbc.queryForObject("SELECT next_attempt_at FROM dce_link", java.sql.Timestamp.class).toInstant())
			.isEqualTo(NOW.plus(Duration.ofMinutes(1)).plus(Duration.ofMinutes(2)));

		settable.set(NOW.plus(Duration.ofMinutes(3)));
		process(worker.claim());
		assertThat(status(link)).isEqualTo("PERMANENT_ERROR");
		assertThat(detail(link)).startsWith("Échec après 3 tentatives");
		settable.set(NOW.plus(Duration.ofDays(1)));
		assertThat(worker.claim()).isNull();
		assertThat(SERVER.requests("/flaky")).isEqualTo(3);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM dce_link", Long.class)).isEqualTo(1);
	}

	@Test
	void aFileTooLargeIsStoppedDuringTheTransferWithoutLeftover() {
		Long link = eligibleLink("26-1", "/big.pdf");

		process(worker.claim());

		assertThat(status(link)).isEqualTo("PERMANENT_ERROR");
		assertThat(detail(link)).contains("Taille maximale dépassée");
		assertThat(temporaryFiles()).isEmpty();
		assertThat(quota.reserved()).isZero();
	}

	@Test
	void aRedirectionToAPrivateAddressIsNeverFollowed() {
		Long link = eligibleLink("26-1", "/redirect-private");

		process(worker.claim());

		assertThat(status(link)).isEqualTo("PERMANENT_ERROR");
		assertThat(detail(link)).contains("Adresse privée, locale ou réservée refusée", "10.1.2.3");
	}

	@Test
	void aPlatformNotAllowedIsNeverContactedEvenForAnAddressLookingLikeAFile() {
		Tender tender = tender("26-1", "Travaux d'éclairage public");
		Long other = linkOn(tender, "http://localhost:" + SERVER.port() + "/ok.pdf", "localhost");
		eligibility.evaluateOutOfDate();
		assertThat(status(other)).isEqualTo("AUTOMATED_ACCESS_NOT_AUTHORIZED");

		// Even forced into the queue, it is checked again before any request.
		jdbc.update("UPDATE dce_link SET status = 'ELIGIBLE_PENDING' WHERE id = ?", other);
		process(worker.claim());

		assertThat(status(other)).isEqualTo("AUTOMATED_ACCESS_NOT_AUTHORIZED");
		assertThat(SERVER.totalRequests()).isZero();
	}

	@Test
	void aTenderNoLongerEligibleIsNotRetrieved() {
		Long link = eligibleLink("26-1", "/ok.pdf");
		Long claimed = worker.claim();
		jdbc.update("UPDATE tender_eligibility SET state = 'NOT_ELIGIBLE'");

		worker.process(claimed);

		assertThat(status(link)).isEqualTo("NOT_ELIGIBLE");
		assertThat(SERVER.totalRequests()).isZero();
	}

	@Test
	void twoWorkersNeverTakeTheSameLink() throws Exception {
		eligibleLink("26-1", "/ok.pdf");
		ExecutorService threads = Executors.newFixedThreadPool(8);
		try {
			List<Future<Long>> claims = new ArrayList<>();
			for (int i = 0; i < 8; i++) {
				claims.add(threads.submit((Callable<Long>) worker::claim));
			}
			List<Long> taken = new ArrayList<>();
			for (Future<Long> claim : claims) {
				if (claim.get() != null) {
					taken.add(claim.get());
				}
			}
			assertThat(taken).hasSize(1);
		}
		finally {
			threads.shutdownNow();
		}
	}

	@Test
	void anAddressInProgressForATenderIsNotTakenForAnother() {
		Tender first = tender("26-1", "Travaux d'éclairage public");
		Tender second = tender("26-2", "Travaux d'éclairage du port");
		Long a = link(first, "/ok.pdf");
		Long b = link(second, "/ok.pdf");
		eligibility.evaluateOutOfDate();

		Long taken = worker.claim();

		assertThat(taken).isIn(a, b);
		assertThat(worker.claim()).isNull();
		process(taken);
		assertThat(worker.claim()).isNotNull().isNotEqualTo(taken);
	}

	@Test
	void theWorkerRetrievesAsManyLinksAtOnceAsAllowedOnItsOwnThreads() throws Exception {
		SERVER.slow = new CountDownLatch(1);
		eligibleLink("26-1", "/slow.pdf?1");
		eligibleLink("26-2", "/slow.pdf?2");
		eligibleLink("26-3", "/slow.pdf?3");

		worker.poll();
		try {
			assertThat(count("IN_PROGRESS")).isEqualTo(2);
			assertThat(count("ELIGIBLE_PENDING")).isEqualTo(1);
		}
		finally {
			SERVER.slow.countDown();
		}
		awaitNo("IN_PROGRESS");
		worker.poll();
		awaitNo("IN_PROGRESS");
		assertThat(count("FETCHED")).isEqualTo(3);
		assertThat(retriever.threads).isNotEmpty().allMatch(name -> name.startsWith("dce-retrieval-"));
	}

	@Test
	void aRetrievalInterruptedByAStopIsQueuedAgainAndItsReservationReleased() {
		Long link = eligibleLink("26-1", "/ok.pdf");
		worker.claim();
		quota.reserve(link, 1_000_000);
		assertThat(quota.reserved()).isEqualTo(1_000_000);

		worker.recoverInterrupted();

		assertThat(status(link)).isEqualTo("ELIGIBLE_PENDING");
		assertThat(quota.reserved()).isZero();
		assertThat(detail(link)).contains("interrompue");
		process(worker.claim());
		assertThat(status(link)).isEqualTo("FETCHED");
	}

	@Test
	void reservationsInParallelNeverExceedTheQuota() throws Exception {
		Tender tender = tender("26-1", "Travaux");
		List<Long> links = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			links.add(link(tender, "/ok.pdf?" + i));
		}
		ExecutorService threads = Executors.newFixedThreadPool(10);
		try {
			List<Future<Boolean>> reservations = new ArrayList<>();
			for (Long link : links) {
				reservations.add(threads.submit(() -> quota.reserve(link, 1_000_000)));
			}
			int granted = 0;
			for (Future<Boolean> reservation : reservations) {
				granted += reservation.get() ? 1 : 0;
			}
			// 3 MB = 3 × 1,048,576 bytes: three reservations of 1,000,000 bytes, not four.
			assertThat(granted).isEqualTo(3);
			assertThat(quota.reserved()).isEqualTo(3_000_000);
		}
		finally {
			threads.shutdownNow();
		}
	}

	@Test
	void aFullQuotaKeepsTheLinkWaitingAndNeverDeletesWhatIsKept() {
		Long first = eligibleLink("26-1", "/ok.pdf");
		process(worker.claim());
		// Another retrieved dossier fills the quota.
		jdbc.update("UPDATE dossier SET stored_bytes = 2500000");
		Long second = eligibleLink("26-2", "/dce.zip");

		process(worker.claim());

		assertThat(status(second)).isEqualTo("STORAGE_LIMIT_REACHED");
		assertThat(status(first)).isEqualTo("FETCHED");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM dossier", Long.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM document", Long.class)).isEqualTo(1);
		assertThat(worker.claim()).isNull();
	}

	@Test
	void aPersonMayTryAgainAFailedLinkOnlyWithTheControlsOfTheRetrieval() {
		Long link = eligibleLink("26-1", "/flaky");
		jdbc.update("UPDATE dce_link SET status = 'PERMANENT_ERROR', attempts = 3 WHERE id = ?", link);

		DceLinkResponse retried = status.retry(link, 1L, "Marie Durand");

		assertThat(retried.status()).isEqualTo(DceLinkStatus.ELIGIBLE_PENDING);
		assertThat(retried.attempts()).isZero();
		assertThat(detail(link)).isEqualTo("Nouvelle tentative demandée par Marie Durand");
		assertThatThrownBy(() -> status.retry(link, 1L, "Marie Durand")).isInstanceOf(ErrorResponseException.class)
			.hasMessageContaining("Seul un lien en erreur");

		jdbc.update("UPDATE dce_link SET status = 'PERMANENT_ERROR' WHERE id = ?", link);
		jdbc.update("UPDATE tender_eligibility SET state = 'NOT_ELIGIBLE'");
		assertThatThrownBy(() -> status.retry(link, 1L, "Marie Durand")).isInstanceOf(ErrorResponseException.class)
			.hasMessageContaining("aucune veille active");
	}

	@Test
	void theStateOfTheRetrievalIsShownOnTheTenderAndInTheSummary() {
		Long link = eligibleLink("26-1", "/ok.pdf");
		process(worker.claim());
		Long tenderId = jdbc.queryForObject("SELECT tender_id FROM dce_link WHERE id = ?", Long.class, link);

		DceLinkResponse shown = status.linksOf(tenderId, 1L).getFirst();
		DceSummaryResponse summary = status.summary();

		assertThat(shown.eligibility()).isEqualTo(eu.gpefrance.tenders.eligibility.EligibilityState.ELIGIBLE);
		assertThat(shown.yourAlerts()).containsExactly("Éclairage");
		assertThat(shown.platformAllowed()).isTrue();
		assertThat(shown.fileCount()).isEqualTo(1);
		assertThat(shown.actionRequired()).isFalse();
		assertThat(status.linksOf(tenderId, 99L).getFirst().yourAlerts()).isEmpty();
		assertThat(summary.links().get(DceLinkStatus.FETCHED)).isEqualTo(1);
		assertThat(summary.usedBytes()).isEqualTo(SyntheticDceServer.PDF.length);
		assertThat(summary.quotaBytes()).isEqualTo(3L * 1024 * 1024);
		assertThat(summary.allowedPlatforms()).containsExactly("127.0.0.1");
		assertThat(status.history(link)).extracting(DceEventResponse::toStatus)
			.containsExactly(DceLinkStatus.FETCHED, DceLinkStatus.IN_PROGRESS, DceLinkStatus.ELIGIBLE_PENDING);
		then(processor).should(atLeastOnce()).startInBackground(anyLong());
	}

	private void process(Long link) {
		assertThat(link).as("a link to retrieve").isNotNull();
		worker.process(link);
	}

	private Long eligibleLink(String externalId, String path) {
		Long link = link(tender(externalId, "Travaux d'éclairage public " + externalId), path);
		eligibility.evaluateOutOfDate();
		return link;
	}

	private Tender tender(String externalId, String title) {
		return tenders.save(new Tender("BOAMP", externalId,
				new TenderDetails(title, null, null, null, null, List.of("13"), List.of(MarketType.WORKS), null,
						List.of(), new BigDecimal("200000"), NOW.atZone(ClockConfiguration.ZONE).toLocalDate(),
						NOW.plus(Duration.ofDays(30)), null, null, List.of()),
				NOW));
	}

	private Long link(Tender tender, String path) {
		return linkOn(tender, SERVER.url(path), SyntheticDceRetriever.PLATFORM);
	}

	private Long linkOn(Tender tender, String url, String platform) {
		return jdbc.queryForObject("""
				INSERT INTO dce_link (tender_id, source, url, platform, status, first_seen_at, last_seen_at)
				VALUES (?, 'BOAMP', ?, ?, 'AVAILABLE', now(), now()) RETURNING id""", Long.class, tender.getId(), url,
				platform);
	}

	private String status(Long link) {
		return jdbc.queryForObject("SELECT status FROM dce_link WHERE id = ?", String.class, link);
	}

	private String detail(Long link) {
		return jdbc.queryForObject("SELECT status_detail FROM dce_link WHERE id = ?", String.class, link);
	}

	private List<String> events(Long link) {
		return jdbc.queryForList("SELECT to_status FROM dce_link_event WHERE dce_link_id = ? ORDER BY id", String.class,
				link);
	}

	private long count(String linkStatus) {
		return jdbc.queryForObject("SELECT count(*) FROM dce_link WHERE status = ?", Long.class, linkStatus);
	}

	private void awaitNo(String linkStatus) throws InterruptedException {
		long end = System.nanoTime() + Duration.ofSeconds(30).toNanos();
		while (count(linkStatus) > 0 && System.nanoTime() < end) {
			Thread.sleep(50);
		}
		assertThat(count(linkStatus)).isZero();
	}

	private List<Path> temporaryFiles() {
		Path directory = storage.resolve(DceRetrievalWorker.TEMPORARY_DIRECTORY);
		if (!Files.exists(directory)) {
			return List.of();
		}
		try (var paths = Files.walk(directory)) {
			return paths.filter(Files::isRegularFile).toList();
		}
		catch (IOException ex) {
			throw new java.io.UncheckedIOException(ex);
		}
	}

	private static void deleteFolder(Path folder) {
		if (!Files.exists(folder)) {
			return;
		}
		try (var paths = Files.walk(folder)) {
			paths.sorted(java.util.Comparator.reverseOrder()).filter(path -> !path.equals(folder)).forEach(path -> path.toFile().delete());
		}
		catch (IOException ex) {
			throw new java.io.UncheckedIOException(ex);
		}
	}

	private static String sha256() throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(SyntheticDceServer.PDF));
	}

	/** A clock of Paris that a test sets. */
	static final class SettableClock extends Clock {

		private volatile Instant now;

		SettableClock(Instant now) {
			this.now = now;
		}

		void set(Instant instant) {
			this.now = instant;
		}

		@Override
		public ZoneId getZone() {
			return ClockConfiguration.ZONE;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Instant instant() {
			return now;
		}

	}

}
