package eu.gpefrance.tenders.collect;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import eu.gpefrance.tenders.collect.boamp.BoampClient;
import eu.gpefrance.tenders.collect.boamp.BoampProperties;
import eu.gpefrance.tenders.collect.boamp.BoampRecord;
import eu.gpefrance.tenders.filter.FilterService;
import eu.gpefrance.tenders.tender.TenderLifecycleService;

/**
 * Collects BOAMP notices published since the last successful collection, or
 * since a chosen day for an initial load, then converts them to tenders and
 * filters them. A failed collection does not move the starting point, so the
 * next one fetches the same days again; notices already stored are skipped.
 * Nothing is ever deleted: a notice no longer published stays with its
 * tender.
 */
@Service
public class CollectService {

	public static final String SOURCE = "BOAMP";

	/** How far back a collection may start. */
	static final int MAX_DAYS_BACK = 366;

	private static final Logger log = LoggerFactory.getLogger(CollectService.class);

	private final BoampClient boampClient;

	private final BoampProperties properties;

	private final RawNoticeRepository rawNotices;

	private final CollectRunRepository runs;

	private final TransactionTemplate transaction;

	private final Clock clock;

	private final TaskExecutor taskExecutor;

	private final NormalizationService normalization;

	private final FilterService filter;

	private final TenderLifecycleService lifecycle;

	private final AtomicBoolean running = new AtomicBoolean();

	public CollectService(BoampClient boampClient, BoampProperties properties, RawNoticeRepository rawNotices,
			CollectRunRepository runs, TransactionTemplate transaction, Clock clock,
			@Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor, NormalizationService normalization,
			FilterService filter, TenderLifecycleService lifecycle) {
		this.boampClient = boampClient;
		this.properties = properties;
		this.rawNotices = rawNotices;
		this.runs = runs;
		this.transaction = transaction;
		this.clock = clock;
		this.taskExecutor = taskExecutor;
		this.normalization = normalization;
		this.filter = filter;
		this.lifecycle = lifecycle;
	}

	/**
	 * Runs left in RUNNING state were interrupted by a restart.
	 */
	@EventListener(ApplicationReadyEvent.class)
	public void closeInterruptedRuns() {
		for (CollectRun run : runs.findAllByStatus(CollectStatus.RUNNING)) {
			run.fail(run.getNoticesReceived(), run.getNoticesNew(), "Interrupted by an application restart",
					clock.instant());
			runs.save(run);
		}
	}

	public CollectRun collect(CollectTrigger trigger) {
		return collect(trigger, null);
	}

	/**
	 * @param since first publication day to fetch, or {@code null} to resume
	 * after the last successful collection
	 */
	public CollectRun collect(CollectTrigger trigger, LocalDate since) {
		checkPeriod(since);
		acquire();
		try {
			return doCollect(trigger, since);
		}
		finally {
			running.set(false);
		}
	}

	/**
	 * Starts a collection in the background. Fails immediately if one is
	 * already running.
	 */
	public void startInBackground(CollectTrigger trigger, LocalDate since) {
		checkPeriod(since);
		acquire();
		try {
			taskExecutor.execute(() -> {
				try {
					doCollect(trigger, since);
				}
				finally {
					running.set(false);
				}
			});
		}
		catch (RuntimeException ex) {
			running.set(false);
			throw ex;
		}
	}

	public boolean isRunning() {
		return running.get();
	}

	private void checkPeriod(LocalDate since) {
		LocalDate today = LocalDate.now(clock);
		if (since != null && (since.isAfter(today) || since.isBefore(today.minusDays(MAX_DAYS_BACK)))) {
			throw new CollectPeriodException(today.minusDays(MAX_DAYS_BACK));
		}
	}

	private void acquire() {
		if (!running.compareAndSet(false, true)) {
			throw new CollectAlreadyRunningException();
		}
	}

	private CollectRun doCollect(CollectTrigger trigger, LocalDate since) {
		LocalDate today = LocalDate.now(clock);
		LocalDate from = since != null ? since : startDate(today);
		CollectRun run = runs.save(new CollectRun(SOURCE, trigger, from, clock.instant()));
		log.info("Collecting {} notices published since {}", SOURCE, from);

		int received = 0;
		int created = 0;
		int rejected = 0;
		try {
			for (LocalDate day = from; !day.isAfter(today); day = day.plusDays(1)) {
				List<BoampRecord> records = boampClient.fetchPublishedOn(day);
				received += records.size();
				rejected += reject(records);
				created += store(records);
			}
			run.succeed(received, created, clock.instant());
		}
		catch (RuntimeException ex) {
			run.fail(received, created, ex.getClass().getSimpleName() + ": " + ex.getMessage(), clock.instant());
			log.error("Collection failed after {} notices received", received, ex);
		}
		run = runs.save(run);
		NormalizationReport report = processCollected();
		log.info("{} import {} in {} s: {} notices received, {} new, {} already known, {} rejected; "
				+ "{} tenders created, {} updated; {} notices ignored, {} errors", SOURCE,
				run.getStatus() == CollectStatus.SUCCESS ? "finished" : "failed",
				Duration.between(run.getStartedAt(), clock.instant()).toSeconds(), received, created,
				received - created - rejected, rejected, report.created(), report.updated(), report.ignored(),
				report.errors());
		return run;
	}

	/**
	 * Runs even after a failed collection: the notices stored before the
	 * failure are usable.
	 */
	private NormalizationReport processCollected() {
		NormalizationReport report = NormalizationReport.NONE;
		try {
			report = normalization.normalizePending();
			filter.filterPending();
			lifecycle.closeExpired();
		}
		catch (RuntimeException ex) {
			log.error("Processing of the collected notices failed", ex);
		}
		return report;
	}

	/**
	 * Starts one day before the last successful collection: notices published
	 * later that day, or made available late by the open data portal, are
	 * caught on the next run. Notices already stored are skipped anyway.
	 */
	private LocalDate startDate(LocalDate today) {
		ZoneId zone = clock.getZone();
		return runs.findFirstBySourceAndStatusOrderByStartedAtDesc(SOURCE, CollectStatus.SUCCESS)
			.map(last -> LocalDate.ofInstant(last.getStartedAt(), zone).minusDays(1))
			.orElse(today.minusDays(properties.initialDays()));
	}

	/**
	 * @return the number of notices that cannot be stored, logged one by one
	 */
	private static int reject(List<BoampRecord> records) {
		List<BoampRecord> invalid = records.stream().filter(record -> !record.isValid()).toList();
		invalid.forEach(record -> log.warn("{} notice without identifier rejected: {}", SOURCE, record.excerpt()));
		return invalid.size();
	}

	private int store(List<BoampRecord> records) {
		Integer created = transaction.execute(status -> records.stream()
			.filter(BoampRecord::isValid)
			.mapToInt(r -> rawNotices.insertIfAbsent(SOURCE, r.idweb(), r.json(), r.publishedAt()))
			.sum());
		return created == null ? 0 : created;
	}

}
