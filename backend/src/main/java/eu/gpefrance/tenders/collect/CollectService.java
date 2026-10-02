package eu.gpefrance.tenders.collect;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import eu.gpefrance.tenders.collect.boamp.BoampClient;
import eu.gpefrance.tenders.collect.boamp.BoampProperties;
import eu.gpefrance.tenders.collect.boamp.BoampRecord;

/**
 * Collects BOAMP notices published since the last successful collection.
 * A failed collection does not move the starting point, so the next one
 * fetches the same days again; notices already stored are skipped.
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

	private final AtomicBoolean running = new AtomicBoolean();

	public CollectService(BoampClient boampClient, BoampProperties properties, RawNoticeRepository rawNotices,
			CollectRunRepository runs, TransactionTemplate transaction, Clock clock) {
		this.boampClient = boampClient;
		this.properties = properties;
		this.rawNotices = rawNotices;
		this.runs = runs;
		this.transaction = transaction;
		this.clock = clock;
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
		if (!running.compareAndSet(false, true)) {
			throw new CollectAlreadyRunningException();
		}
		try {
			return doCollect(trigger);
		}
		finally {
			running.set(false);
		}
	}

	public boolean isRunning() {
		return running.get();
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
		return runs.save(run);
	}

	/**
	 * The day of the last successful collection is fetched again: notices
	 * published later that same day were not available yet.
	 */
	private LocalDate startDate(LocalDate today) {
		ZoneId zone = clock.getZone();
		return runs.findFirstBySourceAndStatusOrderByStartedAtDesc(SOURCE, CollectStatus.SUCCESS)
			.map(last -> LocalDate.ofInstant(last.getStartedAt(), zone))
			.orElse(today.minusDays(properties.initialDays()));
	}

	private int store(List<BoampRecord> records) {
		Integer created = transaction.execute(status -> records.stream()
			.mapToInt(r -> rawNotices.insertIfAbsent(SOURCE, r.idweb(), r.json(), r.publishedAt()))
			.sum());
		return created == null ? 0 : created;
	}

}
