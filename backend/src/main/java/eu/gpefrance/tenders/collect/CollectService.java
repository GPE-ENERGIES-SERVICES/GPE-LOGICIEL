package eu.gpefrance.tenders.collect;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
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

	/** Sources being collected. */
	private final Set<String> running = ConcurrentHashMap.newKeySet();

	public CollectService(List<SourceConnector> connectors, CollectProperties properties,
			RawNoticeRepository rawNotices, CollectRunRepository runs, TransactionTemplate transaction, Clock clock,
			@Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor, NormalizationService normalization,
			FilterService filter, TenderLifecycleService lifecycle) {
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
			run.fail(run.getNoticesReceived(), run.getNoticesNew(), "Interrupted by an application restart",
					clock.instant());
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
		return connectors.values()
			.stream()
			.map(connector -> new SourceState(connector.source(), connector.access(),
					active.contains(connector.source()), running.contains(connector.source()),
					runs.findFirstBySourceOrderByStartedAtDesc(connector.source()).orElse(null),
					runs.findFirstBySourceAndStatusOrderByStartedAtDesc(connector.source(), CollectStatus.SUCCESS)
						.orElse(null)))
			.toList();
	}

	/**
	 * @param active collected by the schedule
	 * @param lastRun most recent run, whatever its outcome, if any
	 * @param lastSuccess most recent successful run, if any
	 */
	public record SourceState(String source, SourceAccess access, boolean active, boolean running, CollectRun lastRun,
			CollectRun lastSuccess) {
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
		CollectRun run = runs.save(new CollectRun(source, trigger, from, clock.instant()));
		log.info("Collecting {} notices published since {}", source, from);

		int received = 0;
		int created = 0;
		int rejected = 0;
		try {
			for (LocalDate day = from; !day.isAfter(today); day = day.plusDays(1)) {
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
	 * Starts one day before the last successful collection of the source:
	 * notices published later that day, or made available late, are caught on
	 * the next run. Notices already stored are skipped anyway.
	 */
	private LocalDate startDate(String source, LocalDate today) {
		ZoneId zone = clock.getZone();
		return runs.findFirstBySourceAndStatusOrderByStartedAtDesc(source, CollectStatus.SUCCESS)
			.map(last -> LocalDate.ofInstant(last.getStartedAt(), zone).minusDays(1))
			.orElse(today.minusDays(properties.initialDays()));
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
