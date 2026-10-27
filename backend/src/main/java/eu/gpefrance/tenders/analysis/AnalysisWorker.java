package eu.gpefrance.tenders.analysis;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import eu.gpefrance.tenders.analysis.AnalysisGate.Status;
import eu.gpefrance.tenders.analysis.AnswerReader.Proposal;
import eu.gpefrance.tenders.analysis.AnswerReader.UnreadableAnswerException;
import eu.gpefrance.tenders.analysis.ExtractionClient.ModelAnswer;
import eu.gpefrance.tenders.analysis.ExtractionPrompt.SourceDocument;
import eu.gpefrance.tenders.document.DocumentPageRepository;
import eu.gpefrance.tenders.document.Dossier;
import eu.gpefrance.tenders.document.DossierRepository;
import eu.gpefrance.tenders.tender.Tender;
import eu.gpefrance.tenders.tender.TenderRepository;
import jakarta.annotation.PreDestroy;

/**
 * Runs the queued analyses in the background, on its own threads
 * (analysis-*), never on those of the collections nor of the web requests.
 * <p>
 * An analysis is taken by one worker at a time (row lock, then PROCESSING),
 * those asked by a user first, then the oldest. Just before the call, the
 * {@link AnalysisGate} is asked again: a configuration changed or a ceiling
 * reached closes the analysis without any call. A failure that may pass
 * (rate limit, timeout, server error) is tried again later, waiting longer
 * each time with a random part, up to {@code analysis.max-attempts}; any
 * other failure is final. At start, nothing is analysed: the analyses left in
 * progress by a stop are queued again, and when the analysis is not allowed
 * the waiting ones are closed instead of waiting for ever.
 */
@Component
public class AnalysisWorker {

	private static final Logger log = LoggerFactory.getLogger(AnalysisWorker.class);

	private final AnalysisRepository analyses;

	private final DossierRepository dossiers;

	private final DocumentPageRepository pages;

	private final TenderRepository tenders;

	private final AnalysisGate gate;

	private final AnalysisProperties properties;

	private final JdbcTemplate jdbc;

	private final TransactionTemplate transaction;

	private final Clock clock;

	/** Threads of the calls to the provider only. */
	private final ThreadPoolTaskExecutor executor;

	private final String workerName = "worker-" + UUID.randomUUID().toString().substring(0, 8);

	private final AtomicInteger active = new AtomicInteger();

	public AnalysisWorker(AnalysisRepository analyses, DossierRepository dossiers, DocumentPageRepository pages,
			TenderRepository tenders, AnalysisGate gate, AnalysisProperties properties, JdbcTemplate jdbc,
			TransactionTemplate transaction, Clock clock) {
		this.analyses = analyses;
		this.dossiers = dossiers;
		this.pages = pages;
		this.tenders = tenders;
		this.gate = gate;
		this.properties = properties;
		this.jdbc = jdbc;
		this.transaction = transaction;
		this.clock = clock;
		ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
		pool.setCorePoolSize(properties.maxConcurrentRequests());
		pool.setMaxPoolSize(properties.maxConcurrentRequests());
		// One more slot for a wake-up that looks at the queue.
		pool.setQueueCapacity(properties.maxConcurrentRequests() + 1);
		pool.setThreadNamePrefix("analysis-");
		pool.setWaitForTasksToCompleteOnShutdown(false);
		pool.initialize();
		this.executor = pool;
	}

	@PreDestroy
	void stop() {
		executor.shutdown();
	}

	/**
	 * Nothing is analysed at start. The analyses left in progress by a stop
	 * are queued again; when the analysis is not allowed, the waiting ones
	 * are closed with the reason, so that none waits for ever.
	 */
	@EventListener(ApplicationReadyEvent.class)
	public void recoverInterrupted() {
		int released = releaseInProgress(null);
		Status status = gate.status();
		int closed = 0;
		if (!status.ready()) {
			closed = closeWaiting(status);
		}
		if (released > 0 || closed > 0) {
			log.info("AI analysis: {} analyses interrupted by a stop queued again, {} closed ({})", released, closed,
					status.state());
		}
	}

	/**
	 * Looks at the queue after a request, without waiting for the next poll.
	 */
	public void wakeUp() {
		try {
			executor.execute(this::poll);
		}
		catch (RuntimeException ex) {
			// Busy: the next poll takes the analysis.
		}
	}

	@Scheduled(fixedDelayString = "${analysis.poll-interval:30s}", initialDelayString = "${analysis.poll-interval:30s}")
	public synchronized void poll() {
		if (!properties.enabled()) {
			return;
		}
		releaseInProgress(clock.instant().minus(properties.lease()));
		while (active.get() < properties.maxConcurrentRequests()) {
			Long analysisId = claim();
			if (analysisId == null) {
				return;
			}
			active.incrementAndGet();
			try {
				executor.execute(() -> {
					try {
						process(analysisId);
					}
					finally {
						active.decrementAndGet();
					}
				});
			}
			catch (RuntimeException ex) {
				active.decrementAndGet();
				transaction.executeWithoutResult(tx -> analyses.findById(analysisId).orElseThrow().release(clock.instant()));
				return;
			}
		}
	}

	/**
	 * Takes the next analysis to run, or none.
	 * @return its id, now PROCESSING for this worker
	 */
	Long claim() {
		return transaction.execute(status -> {
			Instant now = clock.instant();
			List<Long> ids = jdbc.queryForList("""
					SELECT id FROM analysis
					WHERE status IN ('PENDING', 'RETRY_WAITING')
					  AND (next_attempt_at IS NULL OR next_attempt_at <= ?)
					ORDER BY (requested_by IS NULL), id
					LIMIT 1
					FOR UPDATE SKIP LOCKED
					""", Long.class, java.sql.Timestamp.from(now));
			if (ids.isEmpty()) {
				return null;
			}
			Analysis analysis = analyses.findById(ids.getFirst()).orElseThrow();
			analysis.claim(workerName, now);
			return analysis.getId();
		});
	}

	/**
	 * Runs one analysis taken by {@link #claim()}: the gate is asked again,
	 * the provider called outside any transaction, its answer read, checked
	 * against the documents and stored.
	 */
	void process(Long analysisId) {
		Status status = gate.status();
		if (!status.ready()) {
			fail(analysisId, status.failureKind(), status.message(), null);
			return;
		}
		Prepared prepared = transaction.execute(tx -> prepare(analysisId));
		if (prepared == null) {
			return;
		}
		if (!gate.fitsBudget(prepared.characters())) {
			fail(analysisId, FailureKind.BUDGET_REACHED,
					"Le plafond mensuel de dépense ne permet pas cette analyse : aucun appel n'a été fait.", null);
			return;
		}
		ExtractionClient client = status.client();
		ModelAnswer answer = null;
		try {
			answer = client.complete(new ExtractionRequest(ExtractionRequest.DCE_SUMMARY, ExtractionPrompt.VERSION,
					ExtractionPrompt.instructions(), ExtractionPrompt.documents(prepared.sources()),
					ExtractionPrompt.outputSchema(), properties.maxOutputTokens(), 0, properties.requestTimeout()));
			Map<Long, Map<Integer, String>> texts = prepared.sources()
				.stream()
				.collect(Collectors.toMap(SourceDocument::id, SourceDocument::pages));
			List<Proposal> proposals = AnswerChecker.check(AnswerReader.read(answer.text(), texts.keySet()), texts,
					prepared.noticeDeadline());
			ModelAnswer usage = answer;
			transaction.executeWithoutResult(tx -> {
				Analysis analysis = analyses.findById(analysisId).orElseThrow();
				proposals.forEach(proposal -> analysis.addField(proposal.key(), proposal.value(), proposal.quote(),
						proposal.documentId(), proposal.page(), proposal.state()));
				analysis.finish(usage.inputTokens(), usage.outputTokens(), clock.instant());
			});
			log.info("Analysis {} done ({} fields, {} / {} tokens)", analysisId, proposals.size(), usage.inputTokens(),
					usage.outputTokens());
		}
		catch (ProviderException ex) {
			failed(analysisId, ex.kind(), SecretRedactor.redact(ex.getMessage()), answer);
		}
		catch (UnreadableAnswerException ex) {
			fail(analysisId, FailureKind.INVALID_RESPONSE, ex.getMessage(), answer);
		}
		catch (RuntimeException ex) {
			log.error("Analysis {} failed unexpectedly: {}", analysisId, ex.getClass().getName());
			fail(analysisId, FailureKind.UNEXPECTED,
					"Erreur inattendue (" + ex.getClass().getSimpleName() + ") : voir le journal technique.", answer);
		}
	}

	private Prepared prepare(Long analysisId) {
		Analysis analysis = analyses.findById(analysisId).orElse(null);
		if (analysis == null || analysis.getStatus() != AnalysisStatus.PROCESSING
				|| !workerName.equals(analysis.getLockedBy())) {
			return null;
		}
		Dossier dossier = dossiers.findById(analysis.getDossierId()).orElseThrow();
		List<SourceDocument> sources = AnalysisService.sorted(dossier.getDocuments()
			.stream()
			.filter(document -> analysis.getDocumentIds().contains(document.getId()))
			.toList()).stream().map(document -> AnalysisService.load(document, pages)).toList();
		Instant noticeDeadline = tenders.findById(analysis.getTenderId()).map(Tender::getDeadline).orElse(null);
		return new Prepared(sources, sources.stream().mapToLong(SourceDocument::length).sum(), noticeDeadline);
	}

	/**
	 * A failure that may pass is tried again later, up to the maximum
	 * attempts; any other one is final.
	 */
	private void failed(Long analysisId, FailureKind kind, String detail, ModelAnswer usage) {
		String message = kind.label() + (detail == null || detail.isBlank() ? "" : " : " + detail);
		transaction.executeWithoutResult(tx -> {
			Analysis analysis = analyses.findById(analysisId).orElseThrow();
			if (usage != null) {
				analysis.recordUsage(usage.inputTokens(), usage.outputTokens());
			}
			Instant now = clock.instant();
			if (kind.retryable() && analysis.getAttempts() < properties.maxAttempts()) {
				Instant next = now.plus(delayAfter(analysis.getAttempts(), ThreadLocalRandom.current().nextDouble()));
				analysis.retryAt(kind, message + ". Nouvelle tentative après " + next, next);
			}
			else {
				analysis.fail(kind, kind.retryable()
						? message + " (après " + analysis.getAttempts() + " tentatives)" : message, now);
			}
		});
		log.warn("Analysis {} failed: {} ({})", analysisId, kind, message);
	}

	private void fail(Long analysisId, FailureKind kind, String message, ModelAnswer usage) {
		transaction.executeWithoutResult(tx -> {
			Analysis analysis = analyses.findById(analysisId).orElseThrow();
			if (usage != null) {
				analysis.recordUsage(usage.inputTokens(), usage.outputTokens());
			}
			analysis.fail(kind, message, clock.instant());
		});
		log.warn("Analysis {} failed: {} ({})", analysisId, kind, message);
	}

	/**
	 * The wait after the given number of attempts: doubled each time, never
	 * more than the maximum, between 75 % and 125 % of it so that analyses
	 * failed together are not tried again together.
	 * @param random a number between 0 and 1
	 */
	Duration delayAfter(int attempts, double random) {
		Duration delay = properties.retryDelay();
		for (int i = 1; i < attempts && delay.compareTo(properties.maxRetryDelay()) < 0; i++) {
			delay = delay.multipliedBy(2);
		}
		if (delay.compareTo(properties.maxRetryDelay()) > 0) {
			delay = properties.maxRetryDelay();
		}
		Duration jittered = Duration.ofMillis(Math.round(delay.toMillis() * (0.75 + 0.5 * random)));
		return jittered.compareTo(properties.maxRetryDelay()) > 0 ? properties.maxRetryDelay() : jittered;
	}

	/**
	 * Queues again the analyses left in progress, all of them at start
	 * (null), or those taken before the given time; one interrupted too
	 * many times is closed.
	 */
	private int releaseInProgress(Instant takenBefore) {
		Integer count = transaction.execute(status -> {
			int released = 0;
			Instant now = clock.instant();
			for (Analysis analysis : analyses.findByStatus(AnalysisStatus.PROCESSING)) {
				if (takenBefore != null && analysis.getLockedAt() != null && analysis.getLockedAt().isAfter(takenBefore)) {
					continue;
				}
				if (analysis.getAttempts() >= properties.maxAttempts()) {
					analysis.fail(FailureKind.INTERRUPTED,
							FailureKind.INTERRUPTED.label() + " (" + analysis.getAttempts() + " fois)", now);
				}
				else {
					analysis.release(now);
				}
				released++;
			}
			return released;
		});
		return count == null ? 0 : count;
	}

	private int closeWaiting(Status status) {
		Integer count = transaction.execute(tx -> {
			int closed = 0;
			for (AnalysisStatus waiting : List.of(AnalysisStatus.PENDING, AnalysisStatus.RETRY_WAITING)) {
				for (Analysis analysis : analyses.findByStatus(waiting)) {
					analysis.fail(status.failureKind(), "Non lancée : " + status.message(), clock.instant());
					closed++;
				}
			}
			return closed;
		});
		return count == null ? 0 : count;
	}

	private record Prepared(List<SourceDocument> sources, long characters, Instant noticeDeadline) {
	}

}
