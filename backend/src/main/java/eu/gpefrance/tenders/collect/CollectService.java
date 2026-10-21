package eu.gpefrance.tenders.collect;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import eu.gpefrance.tenders.filter.FilterService;
import eu.gpefrance.tenders.tender.TenderLifecycleService;

/**
 * Collects the notices of each source published since its last successful
 * collection, or since a chosen day for an initial load, then converts them
 * to tenders and filters them. Every {@link SourceConnector} is a source; the
 * active ones are those collected and not disabled. Each source has its own
 * run, journal entry and report: a failing source does not stop the others.
 * A failed collection does not move the starting point of its source, so the
 * next one fetches the same days again; notices already stored are skipped.
 * Nothing is ever deleted: a notice no longer published stays with its
 * tender.
 */
@Service
public class CollectService {

	/** How far back a collection may start. */
	static final int MAX_DAYS_BACK = 366;

	/** Error of a run left running by a stop of the application. */
	static final String INTERRUPTED = "Interrupted by an application restart";

	/** How far back the last scheduled collection is looked for. */
	private static final Duration SLOT_LOOKBACK = Duration.ofDays(8);

	private static final Logger log = LoggerFactory.getLogger(CollectService.class);

	private final Map<String, SourceConnector> connectors = new LinkedHashMap<>();

	private final CollectProperties properties;

	private final RawNoticeRepository rawNotices;

	private final CollectRunRepository runs;

	private final TransactionTemplate transaction;

	private final Clock clock;

	private final TaskExecutor taskExecutor;

	private final NormalizationService normalization;

	private final FilterService filter;

	private final TenderLifecycleService lifecycle;

	private final CollectCatchUpProperties catchUp;

	/** The scheduled collections, null when there are none. */
	private final CronExpression schedule;

	/** Sources being collected. */
	private final Set<String> running = ConcurrentHashMap.newKeySet();

	public CollectService(List<SourceConnector> connectors, CollectProperties properties,
			RawNoticeRepository rawNotices, CollectRunRepository runs, TransactionTemplate transaction, Clock clock,
			@Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor, NormalizationService normalization,
			FilterService filter, TenderLifecycleService lifecycle, CollectCatchUpProperties catchUp,
			@Value("${collect.cron}") String cron) {
		this.catchUp = catchUp;
		this.schedule = Scheduled.CRON_DISABLED.equals(cron) ? null : CronExpression.parse(cron);
		for (SourceConnector connector : connectors) {
			if (this.connectors.putIfAbsent(connector.source(), connector) != null) {
				throw new IllegalStateException("Two connectors for the source " + connector.source());
			}
		}
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
			run.fail(run.getNoticesReceived(), run.getNoticesNew(), INTERRUPTED, clock.instant());
			runs.save(run);
		}
	}

	/**
	 * The sources collected by the schedule and by default: collected
	 * automatically and not disabled.
	 */
	public List<String> activeSources() {
		return active().stream().map(SourceConnector::source).toList();
	}

	/**
	 * Collects every active source one after the other. A source already being
	 * collected or failing does not prevent the others.
	 * @return the run of each source collected
	 */
	public List<CollectRun> collectActive(CollectTrigger trigger) {
		List<CollectRun> done = new ArrayList<>();
		for (SourceConnector connector : active()) {
			try {
				done.add(collect(connector.source(), trigger, null));
			}
			catch (CollectAlreadyRunningException ex) {
				log.warn("{} collection skipped: one is already running", connector.source());
			}
			catch (RuntimeException ex) {
				log.error("{} collection failed", connector.source(), ex);
			}
		}
		return done;
	}

	/**
	 * @param since first publication day to fetch, or {@code null} to resume
	 * after the last successful collection of the source
	 */
	public CollectRun collect(String source, CollectTrigger trigger, LocalDate since) {
		SourceConnector connector = collected(source);
		checkPeriod(since);
		acquire(List.of(connector));
		try {
			return doCollect(connector, trigger, since);
		}
		finally {
			running.remove(source);
		}
	}

	/**
	 * Starts in the background the collection of a source, or of every active
	 * source one after the other. Fails immediately if one of them is already
	 * being collected.
	 * @param source the source to collect, or {@code null} for the active ones
	 */
	public void startInBackground(String source, CollectTrigger trigger, LocalDate since) {
		List<SourceConnector> targets = source == null ? active() : List.of(collected(source));
		checkPeriod(since);
		acquire(targets);
		try {
			taskExecutor.execute(() -> {
				for (SourceConnector connector : targets) {
					try {
						doCollect(connector, trigger, since);
					}
					catch (RuntimeException ex) {
						log.error("{} collection failed", connector.source(), ex);
					}
					finally {
						running.remove(connector.source());
					}
				}
			});
		}
		catch (RuntimeException ex) {
			targets.forEach(connector -> running.remove(connector.source()));
			throw ex;
		}
	}

	public boolean isRunning() {
		return !running.isEmpty();
	}

	/**
	 * Every known source, collected or not, with its last run and its last
	 * successful one.
	 */
	public List<SourceState> sources() {
		List<String> active = activeSources();
		Instant now = clock.instant();
		Instant nextRun = nextSlot(now);
		return connectors.values()
			.stream()
			.map(connector -> {
				String source = connector.source();
				boolean isActive = active.contains(source);
				CollectRun lastSuccess = runs.findFirstBySourceAndStatusOrderByStartedAtDescIdDesc(source,
						CollectStatus.SUCCESS)
					.orElse(null);
				Duration late = lastSuccess == null ? null
						: WorkingHours.between(lastSuccess.getStartedAt(), now, clock.getZone());
				boolean overdue = isActive
						&& (late == null || late.compareTo(Duration.ofHours(catchUp.alertWorkingHours())) > 0);
				return new SourceState(source, connector.access(), isActive, running.contains(source),
						runs.findFirstBySourceOrderByStartedAtDescIdDesc(source).orElse(null), lastSuccess,
						runs.findFirstBySourceAndStatusOrderByStartedAtDescIdDesc(source, CollectStatus.FAILED)
							.orElse(null),
						isActive ? nextRun : null, late == null ? null : late.toHours(), overdue);
			})
			.toList();
	}

	/**
	 * @param active collected by the schedule
	 * @param lastRun most recent run, whatever its outcome, if any
	 * @param lastSuccess most recent successful run, if any; it gives the
	 * period covered
	 * @param lastFailure most recent failed run, if any
	 * @param nextRun next scheduled collection, null for a source not collected
	 * @param workingHoursSinceSuccess working hours (see {@link WorkingHours})
	 * since the start of the last successful run, null without one
	 * @param overdue an active source without successful collection for more
	 * than {@code collect.catch-up.alert-working-hours}, or never collected
	 */
	public record SourceState(String source, SourceAccess access, boolean active, boolean running, CollectRun lastRun,
			CollectRun lastSuccess, CollectRun lastFailure, Instant nextRun, Long workingHoursSinceSuccess,
			boolean overdue) {

		public SourceState(String source, SourceAccess access, boolean active, boolean running, CollectRun lastRun,
				CollectRun lastSuccess) {
			this(source, access, active, running, lastRun, lastSuccess, null, null, null, false);
		}

	}

	/**
	 * The active sources that missed the last scheduled collection: no run
	 * started since then, other than one interrupted by a restart, and no
	 * successful one either. A source whose last scheduled run failed is
	 * not one: it is tried again at the next scheduled collection.
	 */
	public List<String> sourcesMissingTheLastSlot() {
		Instant now = clock.instant();
		Instant slot = previousSlot(now);
		if (slot == null) {
			return List.of();
		}
		return activeSources().stream()
			.filter(source -> !runs.existsAttemptSince(source, slot, INTERRUPTED))
			.toList();
	}

	/**
	 * Whether the last successful collection of the source asked publication
	 * days up to today.
	 */
	public boolean isUpToDate(String source) {
		LocalDate today = LocalDate.now(clock);
		return runs.findFirstBySourceAndStatusOrderByStartedAtDescIdDesc(source, CollectStatus.SUCCESS)
			.map(last -> !coveredUntil(last).isBefore(today))
			.orElse(false);
	}

	/** The next scheduled collection after the given time, null without schedule. */
	Instant nextSlot(Instant after) {
		if (schedule == null) {
			return null;
		}
		ZonedDateTime next = schedule.next(after.atZone(clock.getZone()));
		return next == null ? null : next.toInstant();
	}

	/** The last scheduled collection at or before the given time, within 8 days. */
	Instant previousSlot(Instant now) {
		if (schedule == null) {
			return null;
		}
		Instant previous = null;
		ZonedDateTime candidate = schedule.next(now.minus(SLOT_LOOKBACK).atZone(clock.getZone()));
		while (candidate != null && !candidate.toInstant().isAfter(now)) {
			previous = candidate.toInstant();
			candidate = schedule.next(candidate);
		}
		return previous;
	}

	private List<SourceConnector> active() {
		return connectors.values()
			.stream()
			.filter(connector -> connector.access().isCollected())
			.filter(connector -> !properties.disabledSources().contains(connector.source()))
			.toList();
	}

	private SourceConnector collected(String source) {
		SourceConnector connector = connectors.get(source);
		if (connector == null) {
			throw CollectSourceException.unknown(source);
		}
		if (!connector.access().isCollected() || properties.disabledSources().contains(source)) {
			throw CollectSourceException.notCollected(source);
		}
		return connector;
	}

	private void checkPeriod(LocalDate since) {
		LocalDate today = LocalDate.now(clock);
		if (since != null && (since.isAfter(today) || since.isBefore(today.minusDays(MAX_DAYS_BACK)))) {
			throw new CollectPeriodException(today.minusDays(MAX_DAYS_BACK));
		}
	}

	/**
	 * Takes all the sources or none of them.
	 */
	private void acquire(List<SourceConnector> targets) {
		List<String> taken = new ArrayList<>();
		for (SourceConnector connector : targets) {
			if (!running.add(connector.source())) {
				taken.forEach(running::remove);
				throw new CollectAlreadyRunningException();
			}
			taken.add(connector.source());
		}
	}

	private CollectRun doCollect(SourceConnector connector, CollectTrigger trigger, LocalDate since) {
		String source = connector.source();
		LocalDate today = LocalDate.now(clock);
		LocalDate from = since != null ? since : startDate(source, today);
		// Resuming on its own, a collection asks a limited number of days; the next one goes on.
		LocalDate until = since != null ? today : min(today, from.plusDays(catchUp.maxDaysPerRun() - 1L));
		CollectRun run = runs.save(new CollectRun(source, trigger, from, until, clock.instant()));
		log.info("Collecting {} notices published from {} to {}", source, from, until);

		int received = 0;
		int created = 0;
		int rejected = 0;
		try {
			for (LocalDate day = from; !day.isAfter(until); day = day.plusDays(1)) {
				List<SourceRecord> records = connector.fetchPublishedOn(day);
				received += records.size();
				rejected += reject(source, records);
				created += store(source, records);
			}
			run.succeed(received, created, clock.instant());
		}
		catch (RuntimeException ex) {
			run.fail(received, created, ex.getClass().getSimpleName() + ": " + ex.getMessage(), clock.instant());
			log.error("{} collection failed after {} notices received", source, received, ex);
		}
		run = runs.save(run);
		NormalizationReport report = processCollected(source);
		run.report(rejected, report, clock.instant());
		run = runs.save(run);
		log.info("{} import {} in {} s: {} notices received, {} new, {} already known, {} rejected; "
				+ "{} tenders created, {} updated; {} notices ignored, {} errors", source,
				run.getStatus() == CollectStatus.SUCCESS ? "finished" : "failed",
				Duration.between(run.getStartedAt(), run.getFinishedAt()).toSeconds(), received, created,
				run.getNoticesKnown(), rejected, report.created(), report.updated(), report.ignored(),
				report.errors());
		return run;
	}

	/**
	 * Runs even after a failed collection: the notices stored before the
	 * failure are usable.
	 */
	private NormalizationReport processCollected(String source) {
		NormalizationReport report = NormalizationReport.NONE;
		try {
			report = normalization.normalizePending(source);
			filter.filterPending();
			lifecycle.closeExpired();
		}
		catch (RuntimeException ex) {
			log.error("Processing of the collected {} notices failed", source, ex);
		}
		return report;
	}

	/**
	 * Starts one day before the last day asked by the last successful
	 * collection of the source: notices published later that day, or made
	 * available late, are caught on the next run. Notices already stored are
	 * skipped anyway. Never more than {@link #MAX_DAYS_BACK} days ago.
	 */
	private LocalDate startDate(String source, LocalDate today) {
		LocalDate start = runs.findFirstBySourceAndStatusOrderByStartedAtDescIdDesc(source, CollectStatus.SUCCESS)
			.map(last -> coveredUntil(last).minusDays(1))
			.orElse(today.minusDays(properties.initialDays()));
		LocalDate earliest = today.minusDays(MAX_DAYS_BACK);
		return start.isBefore(earliest) ? earliest : start;
	}

	/**
	 * The last publication day a run asked; for a run made before it was
	 * recorded, the day it started.
	 */
	private LocalDate coveredUntil(CollectRun run) {
		return run.getUntilDate() != null ? run.getUntilDate()
				: LocalDate.ofInstant(run.getStartedAt(), clock.getZone());
	}

	private static LocalDate min(LocalDate a, LocalDate b) {
		return a.isBefore(b) ? a : b;
	}

	/**
	 * @return the number of notices that cannot be stored, logged one by one
	 */
	private static int reject(String source, List<SourceRecord> records) {
		List<SourceRecord> invalid = records.stream().filter(record -> !record.isValid()).toList();
		invalid.forEach(record -> log.warn("{} notice without identifier rejected: {}", source, record.excerpt()));
		return invalid.size();
	}

	private int store(String source, List<SourceRecord> records) {
		Integer created = transaction.execute(status -> records.stream()
			.filter(SourceRecord::isValid)
			.mapToInt(r -> rawNotices.insertIfAbsent(source, r.externalId(), r.payload(), r.publishedAt()))
			.sum());
		return created == null ? 0 : created;
	}

}
