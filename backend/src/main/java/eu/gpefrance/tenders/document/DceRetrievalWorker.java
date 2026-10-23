package eu.gpefrance.tenders.document;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import eu.gpefrance.tenders.document.DceRetriever.AccessRequiredException;
import eu.gpefrance.tenders.document.DceRetriever.LinkUnavailableException;
import eu.gpefrance.tenders.document.DceRetriever.RetrievedFile;
import eu.gpefrance.tenders.document.DossierService.ReceivedFile;
import eu.gpefrance.tenders.document.DossierService.RetrievedDossier;
import eu.gpefrance.tenders.document.DossierService.StorageLimitException;
import eu.gpefrance.tenders.document.FileTypeValidator.FileRejectedException;
import eu.gpefrance.tenders.document.SafeHttpFetcher.ForbiddenAddressException;
import eu.gpefrance.tenders.eligibility.EligibilityState;
import eu.gpefrance.tenders.eligibility.TenderEligibility;
import eu.gpefrance.tenders.eligibility.TenderEligibilityRepository;
import jakarta.annotation.PreDestroy;

/**
 * Retrieves the consultation files of the queued links, in the background,
 * on its own threads (dce-retrieval-*): never on those of the
 * collections nor of the scheduler, which it only uses to look at the queue.
 * Disabled by default ({@code dce.retrieval.worker-enabled}); with no allowed
 * platform, no link is ever queued anyway.
 * <p>
 * A link is taken by one worker at a time (row lock, then IN_PROGRESS), never
 * while another link of the same address is in progress, the most recently
 * published tenders first. Just before the retrieval, the eligibility of the
 * tender and the authorisation of the platform are checked again, and the
 * space reserved in the quota. The files are copied to a temporary folder
 * within the size limit, validated by their content, then kept as a dossier;
 * the temporary folder is always deleted and the reservation released. A
 * failure that may pass is tried again later, waiting longer each time, up
 * to {@code max-attempts}; an access refused or requiring a person is never
 * tried again automatically. At start, the links left in progress by a stop
 * are queued again and their reservations released: nothing is retrieved at
 * start.
 */
@Component
public class DceRetrievalWorker {

	static final String TEMPORARY_DIRECTORY = "dce-tmp";

	private static final Logger log = LoggerFactory.getLogger(DceRetrievalWorker.class);

	private final DceLinkRepository links;

	private final DceQueueService queue;

	private final DcePlatformPolicy policy;

	private final DceStorageQuota quota;

	private final TenderEligibilityRepository eligibilities;

	private final DossierService dossierService;

	private final DocumentProcessor processor;

	private final FileStorage storage;

	private final DocumentsProperties documents;

	private final DceRetrievalProperties properties;

	private final JdbcTemplate jdbc;

	private final TransactionTemplate transaction;

	/** Threads of the retrievals only, never those of the collections. */
	private final ThreadPoolTaskExecutor executor;

	private final Clock clock;

	private final String workerName = "worker-" + UUID.randomUUID().toString().substring(0, 8);

	private final AtomicInteger active = new AtomicInteger();

	public DceRetrievalWorker(DceLinkRepository links, DceQueueService queue, DcePlatformPolicy policy,
			DceStorageQuota quota, TenderEligibilityRepository eligibilities, DossierService dossierService,
			DocumentProcessor processor, FileStorage storage, DocumentsProperties documents,
			DceRetrievalProperties properties, JdbcTemplate jdbc, TransactionTemplate transaction, Clock clock) {
		this.links = links;
		this.queue = queue;
		this.policy = policy;
		this.quota = quota;
		this.eligibilities = eligibilities;
		this.dossierService = dossierService;
		this.processor = processor;
		this.storage = storage;
		this.documents = documents;
		this.properties = properties;
		this.jdbc = jdbc;
		this.transaction = transaction;
		ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
		pool.setCorePoolSize(properties.concurrency());
		pool.setMaxPoolSize(properties.concurrency());
		pool.setQueueCapacity(properties.concurrency());
		pool.setThreadNamePrefix("dce-retrieval-");
		pool.setWaitForTasksToCompleteOnShutdown(false);
		pool.initialize();
		this.executor = pool;
		this.clock = clock;
	}

	@PreDestroy
	void stop() {
		executor.shutdown();
	}

	/**
	 * The links left in progress by a stop are queued again; the
	 * reservations and temporary files of their retrievals are dropped.
	 */
	@EventListener(ApplicationReadyEvent.class)
	public void recoverInterrupted() {
		int recovered = releaseLinks(null);
		int released = quota.releaseAbandoned();
		deleteDirectory(storage.resolve(TEMPORARY_DIRECTORY));
		if (recovered > 0 || released > 0) {
			log.info("DCE retrieval: {} links interrupted by a stop queued again, {} reservations released", recovered,
					released);
		}
	}

	@Scheduled(fixedDelayString = "${dce.retrieval.poll-interval:30s}",
			initialDelayString = "${dce.retrieval.poll-interval:30s}")
	public void poll() {
		if (!properties.workerEnabled()) {
			return;
		}
		releaseLinks(clock.instant().minus(properties.lease()));
		while (active.get() < properties.concurrency()) {
			Long linkId = claim();
			if (linkId == null) {
				return;
			}
			active.incrementAndGet();
			try {
				executor.execute(() -> {
					try {
						process(linkId);
					}
					finally {
						active.decrementAndGet();
					}
				});
			}
			catch (RuntimeException ex) {
				active.decrementAndGet();
				release(linkId, "Non lancé : " + ex.getMessage());
				throw ex;
			}
		}
	}

	/**
	 * Takes the next link to retrieve, or none.
	 * @return its id, now IN_PROGRESS for this worker
	 */
	Long claim() {
		return transaction.execute(status -> {
			Instant now = clock.instant();
			List<Long> ids = jdbc.queryForList("""
					SELECT l.id FROM dce_link l JOIN tender t ON t.id = l.tender_id
					WHERE l.status IN ('ELIGIBLE_PENDING', 'TEMPORARY_ERROR')
					  AND (l.next_attempt_at IS NULL OR l.next_attempt_at <= ?)
					  AND NOT EXISTS (SELECT 1 FROM dce_link o WHERE o.url = l.url AND o.status = 'IN_PROGRESS')
					ORDER BY t.published_at DESC NULLS LAST, l.id
					LIMIT 1
					FOR UPDATE OF l SKIP LOCKED
					""", Long.class, java.sql.Timestamp.from(now));
			if (ids.isEmpty()) {
				return null;
			}
			DceLink link = links.findById(ids.getFirst()).orElseThrow();
			queue.change(link, DceLinkStatus.IN_PROGRESS, "Récupération en cours (tentative " + (link.getAttempts() + 1)
					+ " sur " + properties.maxAttempts() + ")", now);
			link.attempted(now);
			link.lock(workerName, now);
			return link.getId();
		});
	}

	/**
	 * Retrieves one link taken by {@link #claim()}.
	 */
	void process(Long linkId) {
		Path temporary = storage.resolve(TEMPORARY_DIRECTORY + "/" + linkId + "-" + UUID.randomUUID());
		boolean reserved = false;
		try {
			Plan plan = transaction.execute(status -> plan(linkId));
			if (plan == null || plan.retriever() == null) {
				return;
			}
			reserved = quota.reserve(linkId, properties.maxFileSize().toBytes());
			if (!reserved) {
				finish(linkId, DceLinkStatus.STORAGE_LIMIT_REACHED,
						"Quota des DCE récupérés atteint : en attente d'espace libéré à la main ou d'un quota plus grand.");
				return;
			}
			List<ReceivedFile> received = receive(plan, temporary);
			RetrievedDossier dossier = dossierService.storeRetrieved(plan.tenderId(), linkId, received,
					bytes -> quota.fits(linkId, bytes));
			String detail = dossier.dossierId() == null
					? dossier.fileCount() + " fichiers identiques à la version " + dossier.version() + " : pas de nouvelle version"
					: dossier.fileCount() + " fichiers récupérés (version " + dossier.version() + ")";
			transaction.executeWithoutResult(status -> {
				DceLink link = links.findById(linkId).orElseThrow();
				queue.change(link, DceLinkStatus.FETCHED, detail, clock.instant());
				link.fetched(dossier.fileCount(), clock.instant());
				link.unlock();
			});
			log.info("DCE link {} retrieved: {}", linkId, detail);
			if (dossier.dossierId() != null) {
				processor.startInBackground(dossier.dossierId());
			}
		}
		catch (AccessRequiredException ex) {
			finish(linkId, DceLinkStatus.ACCESS_REQUIRED, "Accès requis : " + ex.getMessage() + ". Action manuelle.");
		}
		catch (LinkUnavailableException ex) {
			finish(linkId, DceLinkStatus.LINK_UNAVAILABLE, "Lien indisponible : " + ex.getMessage());
		}
		catch (ForbiddenAddressException | FileRejectedException ex) {
			finish(linkId, DceLinkStatus.PERMANENT_ERROR, "Refusé : " + ex.getMessage());
		}
		catch (StorageLimitException ex) {
			finish(linkId, DceLinkStatus.STORAGE_LIMIT_REACHED, ex.getMessage());
		}
		catch (RuntimeException ex) {
			failTemporarily(linkId, ex);
		}
		finally {
			deleteDirectory(temporary);
			if (reserved) {
				quota.release(linkId);
			}
		}
	}

	/**
	 * Checks again, just before the retrieval, that the tender is eligible and
	 * the platform allowed.
	 * @return what to retrieve; null retriever when it may not be
	 */
	private Plan plan(Long linkId) {
		DceLink link = links.findById(linkId).orElse(null);
		if (link == null || link.getStatus() != DceLinkStatus.IN_PROGRESS) {
			return null;
		}
		Instant now = clock.instant();
		EligibilityState state = eligibilities.findById(link.getTenderId())
			.map(TenderEligibility::getState)
			.orElse(EligibilityState.INDETERMINATE);
		if (state != EligibilityState.ELIGIBLE) {
			queue.change(link, state == EligibilityState.NOT_ELIGIBLE ? DceLinkStatus.NOT_ELIGIBLE : DceLinkStatus.AVAILABLE,
					"Consultation devenue non éligible avant la récupération : rien n'est récupéré.", now);
			link.unlock();
			return new Plan(link.getTenderId(), link, null);
		}
		DcePlatformPolicy.Decision decision = policy.decide(link);
		if (!decision.isAllowed()) {
			queue.change(link, DceLinkStatus.AUTOMATED_ACCESS_NOT_AUTHORIZED, decision.reason(), now);
			link.unlock();
			return new Plan(link.getTenderId(), link, null);
		}
		return new Plan(link.getTenderId(), link, decision.retriever());
	}

	/**
	 * Copies the files of the platform to the temporary folder, never more
	 * than the largest retrieval allowed in all, then validates each one by
	 * its content.
	 */
	private List<ReceivedFile> receive(Plan plan, Path temporary) {
		long limit = properties.maxFileSize().toBytes();
		long written = 0;
		List<ReceivedFile> received = new ArrayList<>();
		try {
			Files.createDirectories(temporary);
			List<RetrievedFile> files = plan.retriever().retrieve(plan.link());
			if (files.isEmpty()) {
				throw new FileRejectedException("Aucun fichier reçu");
			}
			int index = 0;
			for (RetrievedFile file : files) {
				Path target = temporary.resolve("file-" + index++);
				try (InputStream in = file.content(); OutputStream out = Files.newOutputStream(target)) {
					written += copyWithin(in, out, limit - written);
				}
				FileTypeValidator.Validated validated = FileTypeValidator.validate(target, file.fileName(),
						documents.maxArchiveEntries(), documents.maxExtractedSize().toBytes());
				received.add(new ReceivedFile(validated.fileName(), target));
			}
			return received;
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/**
	 * @return the bytes copied
	 * @throws FileRejectedException beyond the bytes allowed; the copy stops
	 */
	static long copyWithin(InputStream in, OutputStream out, long allowed) throws IOException {
		byte[] buffer = new byte[64 * 1024];
		long copied = 0;
		int read;
		while ((read = in.read(buffer)) != -1) {
			copied += read;
			if (copied > allowed) {
				throw new FileRejectedException("Taille maximale dépassée pendant le transfert");
			}
			out.write(buffer, 0, read);
		}
		return copied;
	}

	private void failTemporarily(Long linkId, RuntimeException ex) {
		transaction.executeWithoutResult(status -> {
			DceLink link = links.findById(linkId).orElseThrow();
			Instant now = clock.instant();
			String reason = ex.getClass().getSimpleName() + (ex.getMessage() == null ? "" : " : " + ex.getMessage());
			if (link.getAttempts() >= properties.maxAttempts()) {
				queue.change(link, DceLinkStatus.PERMANENT_ERROR,
						"Échec après " + link.getAttempts() + " tentatives : " + reason, now);
			}
			else {
				Instant next = now.plus(delayAfter(link.getAttempts()));
				queue.change(link, DceLinkStatus.TEMPORARY_ERROR, "Erreur temporaire (" + reason + "), nouvelle tentative après "
						+ next, now);
				link.retryAt(next);
			}
			link.unlock();
		});
		log.warn("DCE link {} failed: {}", linkId, ex.toString());
	}

	/** The wait after the given number of attempts: doubled each time, never more than the maximum. */
	Duration delayAfter(int attempts) {
		Duration delay = properties.retryDelay();
		for (int i = 1; i < attempts && delay.compareTo(properties.maxRetryDelay()) < 0; i++) {
			delay = delay.multipliedBy(2);
		}
		return delay.compareTo(properties.maxRetryDelay()) > 0 ? properties.maxRetryDelay() : delay;
	}

	private void finish(Long linkId, DceLinkStatus status, String detail) {
		transaction.executeWithoutResult(tx -> {
			DceLink link = links.findById(linkId).orElseThrow();
			queue.change(link, status, detail, clock.instant());
			link.unlock();
		});
		log.info("DCE link {}: {} ({})", linkId, status, detail);
	}

	private void release(Long linkId, String detail) {
		transaction.executeWithoutResult(tx -> {
			DceLink link = links.findById(linkId).orElseThrow();
			queue.change(link, DceLinkStatus.ELIGIBLE_PENDING, detail, clock.instant());
			link.unlock();
		});
	}

	/**
	 * Queues again the links left in progress, all of them at start (null),
	 * or those taken before the given time.
	 */
	private int releaseLinks(Instant takenBefore) {
		Integer count = transaction.execute(status -> {
			int released = 0;
			for (DceLink link : links.findByStatus(DceLinkStatus.IN_PROGRESS)) {
				if (takenBefore != null && link.getLockedAt() != null && link.getLockedAt().isAfter(takenBefore)) {
					continue;
				}
				Instant now = clock.instant();
				if (link.getAttempts() >= properties.maxAttempts()) {
					queue.change(link, DceLinkStatus.PERMANENT_ERROR,
							"Interrompue " + link.getAttempts() + " fois : abandon de la récupération", now);
				}
				else {
					queue.change(link, DceLinkStatus.ELIGIBLE_PENDING,
							"Récupération interrompue (arrêt ou délai dépassé) : remise en attente", now);
					link.retryAt(now);
				}
				link.unlock();
				released++;
			}
			return released;
		});
		return count == null ? 0 : count;
	}

	private static void deleteDirectory(Path directory) {
		if (!Files.exists(directory)) {
			return;
		}
		try (var paths = Files.walk(directory)) {
			paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
		}
		catch (IOException ex) {
			log.warn("Temporary DCE folder {} not deleted: {}", directory, ex.getMessage());
		}
	}

	private record Plan(Long tenderId, DceLink link, DceRetriever retriever) {
	}

}
