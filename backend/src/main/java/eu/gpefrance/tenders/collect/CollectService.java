package eu.gpefrance.tenders.collect;

import java.time.Clock;
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
import eu.gpefrance.tenders.tender.TenderLifecycleService;

/**
 * Collects BOAMP notices published since the last successful collection,
 * then converts them to tenders. A failed collection does not move the
 * starting point, so the next one fetches the same days again; notices
 * already stored are skipped.
 */
@Service
public class CollectService {

	public static final String SOURCE = "BOAMP";

	private static final Logger log = LoggerFactory.getLogger(CollectService.class);

	private final BoampClient boampClient;

	private final BoampProperties properties;

	private final RawNoticeRepository rawNotices;

	private final CollectRunRepository runs;

	private final TransactionTemplate transaction;

	private final Clock clock;

	private final TaskExecutor taskExecutor;

	private final NormalizationService normalization;

	private final TenderLifecycleService lifecycle;

	private final AtomicBoolean running = new AtomicBoolean();

	public CollectService(BoampClient boampClient, BoampProperties properties, RawNoticeRepository rawNotices,
			CollectRunRepository runs, TransactionTemplate transaction, Clock clock,
			@Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor, NormalizationService normalization,
			TenderLifecycleService lifecycle) {
		this.boampClient = boampClient;
		this.properties = properties;
		this.rawNotices = rawNotices;
		this.runs = runs;
		this.transaction = transaction;
		this.clock = clock;
		this.taskExecutor = taskExecutor;
		this.normalization = normalization;
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
		acquire();
		try {
			return doCollect(trigger);
		}
		finally {
			running.set(false);
		}
	}

	/**
	 * Starts a collection in the background. Fails immediately if one is
	 * already running.
	 */
	public void startInBackground(CollectTrigger trigger) {
		acquire();
		try {
			taskExecutor.execute(() -> {
				try {
					doCollect(trigger);
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

	private void acquire() {
		if (!running.compareAndSet(false, true)) {
			throw new CollectAlreadyRunningException();
		}
	}

	private CollectRun doCollect(CollectTrigger trigger) {
		LocalDate today = LocalDate.now(clock);
		CollectRun run = runs.save(new CollectRun(SOURCE, trigger, startDate(today), clock.instant()));
		log.info("Collecting {} notices published since {}", SOURCE, run.getSinceDate());

		int received = 0;
		int created = 0;
		try {
			for (LocalDate day = run.getSinceDate(); !day.isAfter(today); day = day.plusDays(1)) {
				List<BoampRecord> records = boampClient.fetchPublishedOn(day);
				received += records.size();
				created += store(records);
			}
			run.succeed(received, created, clock.instant());
			log.info("Collection succeeded: {} notices received, {} new", received, created);
		}
		catch (RuntimeException ex) {
			run.fail(received, created, ex.getClass().getSimpleName() + ": " + ex.getMessage(), clock.instant());
			log.error("Collection failed after {} notices received", received, ex);
		}
		run = runs.save(run);
		processCollected();
		return run;
	}

	/**
	 * Runs even after a failed collection: the notices stored before the
	 * failure are usable.
	 */
	private void processCollected() {
		try {
			normalization.normalizePending();
			lifecycle.closeExpired();
		}
		catch (RuntimeException ex) {
			log.error("Processing of the collected notices failed", ex);
		}
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

	private int store(List<BoampRecord> records) {
		Integer created = transaction.execute(status -> records.stream()
			.mapToInt(r -> rawNotices.insertIfAbsent(SOURCE, r.idweb(), r.json(), r.publishedAt()))
			.sum());
		return created == null ? 0 : created;
	}

}
