package eu.gpefrance.tenders.eligibility;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import eu.gpefrance.tenders.alert.Alert;
import eu.gpefrance.tenders.alert.AlertRepository;
import eu.gpefrance.tenders.document.DceQueueService;
import eu.gpefrance.tenders.filter.AlertMatch;
import eu.gpefrance.tenders.filter.AlertMatcher;
import eu.gpefrance.tenders.tender.Tender;
import eu.gpefrance.tenders.tender.TenderRepository;
import eu.gpefrance.tenders.tender.TenderStatus;
import eu.gpefrance.tenders.user.UserStatusChangedEvent;

/**
 * Decides whether the consultation file of each tender may be retrieved: it
 * is eligible when the tender is open and matches at least one active saved
 * search of an enabled account, of any user: the team is the whole company,
 * the saved searches are personal and the files are kept once for the company. The
 * criteria of the company (tender.retained) play no part, and are never
 * changed. Several matching saved searches make one eligibility, so one
 * retrieval at most of each link.
 * <p>
 * The tenders whose eligibility is missing or out of date (the tender changed,
 * or a saved search did) are evaluated by batches, in the order of their
 * identifiers, each batch in its own transaction: an evaluation stopped by a
 * restart goes on at the next one, and evaluating again changes nothing.
 * One evaluation runs at a time, in the background, after each collection,
 * after each change of a saved search, and every few minutes
 * ({@code dce.eligibility.sweep-interval}). It never contacts a platform.
 */
@Service
public class EligibilityService {

	private static final Logger log = LoggerFactory.getLogger(EligibilityService.class);

	private final TenderRepository tenders;

	private final AlertRepository alerts;

	private final TenderEligibilityRepository eligibilities;

	private final DceQueueService dceQueue;

	private final TransactionTemplate transaction;

	private final TaskExecutor executor;

	private final Clock clock;

	private final int batchSize;

	private final boolean autoEvaluation;

	private final AtomicBoolean running = new AtomicBoolean();

	/** A new request came while an evaluation was running: one more pass follows. */
	private final AtomicBoolean requestedAgain = new AtomicBoolean();

	public EligibilityService(TenderRepository tenders, AlertRepository alerts,
			TenderEligibilityRepository eligibilities, DceQueueService dceQueue, TransactionTemplate transaction,
			@Qualifier("applicationTaskExecutor") TaskExecutor executor, Clock clock,
			EligibilityProperties properties) {
		this.tenders = tenders;
		this.alerts = alerts;
		this.eligibilities = eligibilities;
		this.dceQueue = dceQueue;
		this.transaction = transaction;
		this.executor = executor;
		this.clock = clock;
		this.batchSize = properties.batchSize();
		this.autoEvaluation = properties.autoEvaluation();
	}

	/**
	 * Starts an evaluation of the out-of-date tenders in the background, or
	 * asks the running one for one more pass.
	 */
	public void requestEvaluation() {
		if (!autoEvaluation) {
			return;
		}
		if (!running.compareAndSet(false, true)) {
			requestedAgain.set(true);
			return;
		}
		try {
			executor.execute(this::runPasses);
		}
		catch (RuntimeException ex) {
			running.set(false);
			throw ex;
		}
	}

	/**
	 * An account disabled or enabled again, once the change is committed: its
	 * active saved searches stop or start counting. Only the tenders that may
	 * change are marked out of date: when disabled, those its saved searches
	 * matched or could not decide; when enabled, the open tenders (only they
	 * can become eligible). Nothing is marked for an account without active
	 * saved search. The saved searches themselves are never changed.
	 * @return the number of tenders marked out of date
	 */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public int onUserStatusChanged(UserStatusChangedEvent event) {
		TransactionTemplate separate = new TransactionTemplate(transaction.getTransactionManager());
		separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		Integer marked = separate.execute(status -> {
			List<Alert> owned = alerts.findByUserIdAndActiveTrue(event.userId());
			if (owned.isEmpty()) {
				return 0;
			}
			if (event.enabled()) {
				return eligibilities.markOpenTendersOutOfDate();
			}
			return owned.stream().mapToInt(alert -> eligibilities.markOutOfDateReferencing(alert.getId())).sum();
		});
		int count = marked == null ? 0 : marked;
		log.info("Account {} {}: {} consultation files to evaluate again", event.userId(),
				event.enabled() ? "enabled" : "disabled", count);
		if (count > 0) {
			requestEvaluation();
		}
		return count;
	}

	@Scheduled(fixedDelayString = "${dce.eligibility.sweep-interval:10m}",
			initialDelayString = "${dce.eligibility.sweep-interval:10m}")
	public void sweep() {
		requestEvaluation();
	}

	public boolean isRunning() {
		return running.get();
	}

	/** Tenders whose eligibility is missing or out of date. */
	public long outOfDate() {
		return eligibilities.countOutOfDate(alerts.currentRevision());
	}

	private void runPasses() {
		try {
			do {
				requestedAgain.set(false);
				evaluateOutOfDate();
			}
			while (requestedAgain.get());
		}
		catch (RuntimeException ex) {
			log.error("Evaluation of the eligibility of the consultation files failed", ex);
		}
		finally {
			running.set(false);
		}
	}

	/**
	 * Evaluates every out-of-date tender, now, in the calling thread.
	 * @return the number of tenders evaluated
	 */
	public int evaluateOutOfDate() {
		long revision = alerts.currentRevision();
		List<Alert> active = alerts.findActiveOfEnabledUsers();
		int evaluated = 0;
		long afterId = 0;
		while (true) {
			List<Long> batch = eligibilities.findOutOfDate(revision, afterId, batchSize);
			if (batch.isEmpty()) {
				break;
			}
			transaction.executeWithoutResult(status -> batch.forEach(id -> evaluate(id, active, revision)));
			evaluated += batch.size();
			afterId = batch.getLast();
		}
		if (evaluated > 0) {
			log.info("Eligibility of {} consultation files evaluated with {} active saved searches", evaluated,
					active.size());
		}
		return evaluated;
	}

	private void evaluate(Long tenderId, List<Alert> active, long revision) {
		Tender tender = tenders.findById(tenderId).orElse(null);
		if (tender == null) {
			return;
		}
		Result result = decide(tender, active);
		TenderEligibility eligibility = eligibilities.findById(tenderId).orElseGet(() -> new TenderEligibility(tenderId));
		Instant now = clock.instant();
		// Never older than the tender, so that it is not out of date at once.
		Instant evaluatedAt = tender.getUpdatedAt() != null && tender.getUpdatedAt().isAfter(now) ? tender.getUpdatedAt()
				: now;
		boolean changed = eligibility.set(result.state(), result.reason(), result.matched(), result.indeterminate(),
				revision, evaluatedAt);
		eligibilities.save(eligibility);
		dceQueue.applyEligibility(tenderId, result.state(), result.reason());
		if (changed) {
			log.debug("Tender {}: consultation file {} ({})", tenderId, result.state(), result.reason());
		}
	}

	/**
	 * The eligibility of an open tender by the active saved searches: one
	 * match is enough; otherwise one indeterminate result makes it
	 * indeterminate.
	 */
	static Result decide(Tender tender, List<Alert> active) {
		if (tender.getStatus() != TenderStatus.ACTIVE) {
			return new Result(EligibilityState.NOT_ELIGIBLE, "Consultation non ouverte (" + statusLabel(tender) + ")",
					List.of(), List.of());
		}
		if (active.isEmpty()) {
			return new Result(EligibilityState.NOT_ELIGIBLE, "Aucune veille active", List.of(), List.of());
		}
		List<Long> matched = new ArrayList<>();
		List<Long> indeterminate = new ArrayList<>();
		List<String> unknown = new ArrayList<>();
		for (Alert alert : active) {
			AlertMatch match = AlertMatcher.match(tender, alert.criteria());
			switch (match.outcome()) {
				case MATCH -> matched.add(alert.getId());
				case INDETERMINATE -> {
					indeterminate.add(alert.getId());
					unknown.add(match.reason());
				}
				case NO_MATCH -> {
				}
			}
		}
		if (!matched.isEmpty()) {
			return new Result(EligibilityState.ELIGIBLE,
					"Correspond à " + matched.size() + (matched.size() > 1 ? " veilles actives" : " veille active"),
					matched, indeterminate);
		}
		if (!indeterminate.isEmpty()) {
			return new Result(EligibilityState.INDETERMINATE, "Données insuffisantes pour " + indeterminate.size()
					+ (indeterminate.size() > 1 ? " veilles" : " veille") + " : "
					+ String.join(" ; ", unknown.stream().distinct().toList()), matched, indeterminate);
		}
		return new Result(EligibilityState.NOT_ELIGIBLE, "Ne correspond à aucune veille active", matched,
				indeterminate);
	}

	private static String statusLabel(Tender tender) {
		return switch (tender.getStatus()) {
			case CLOSED -> "close";
			case CANCELLED -> "annulée";
			case UNSUCCESSFUL -> "sans suite";
			case ACTIVE -> "ouverte";
		};
	}

	record Result(EligibilityState state, String reason, List<Long> matched, List<Long> indeterminate) {
	}

}
