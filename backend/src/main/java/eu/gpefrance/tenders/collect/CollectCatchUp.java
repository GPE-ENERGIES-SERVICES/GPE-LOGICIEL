package eu.gpefrance.tenders.collect;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Catches up the scheduled collections missed while the application was
 * stopped or the machine asleep: shortly after the start, then every hour
 * ({@code collect.catch-up.cron}), the active sources that have no run since
 * the last scheduled collection are collected, one after the other in the
 * order of the schedule, in the background: the scheduler is never held.
 * <p>
 * A source is collected again, after a pause, while its last collection did
 * not reach today (each one asks {@code max-days-per-run} days at most), up to
 * {@code max-runs} collections; the next scheduled collection goes on. A
 * failure stops the catch-up of the source: the next scheduled collection
 * tries again. A source already being collected is skipped, and one catch-up
 * runs at a time. The normal schedule is unchanged.
 */
@Component
public class CollectCatchUp {

	private static final Logger log = LoggerFactory.getLogger(CollectCatchUp.class);

	private final CollectService collectService;

	private final CollectCatchUpProperties properties;

	private final TaskScheduler scheduler;

	private final TaskExecutor executor;

	private final AtomicBoolean busy = new AtomicBoolean();

	public CollectCatchUp(CollectService collectService, CollectCatchUpProperties properties, TaskScheduler scheduler,
			@Qualifier("applicationTaskExecutor") TaskExecutor executor) {
		this.collectService = collectService;
		this.properties = properties;
		this.scheduler = scheduler;
		this.executor = executor;
	}

	/**
	 * After the runs interrupted by the stop are closed.
	 */
	@EventListener(ApplicationReadyEvent.class)
	@Order(Ordered.LOWEST_PRECEDENCE)
	public void afterStart() {
		if (properties.enabled()) {
			scheduler.schedule(this::start, scheduler.getClock().instant().plus(properties.delay()));
		}
	}

	@Scheduled(cron = "${collect.catch-up.cron:0 30 * * * *}", zone = "Europe/Paris")
	public void check() {
		if (properties.enabled()) {
			start();
		}
	}

	/**
	 * Starts in the background the catch-up of the sources that missed the
	 * last scheduled collection, unless one is running.
	 * @return whether a catch-up was started
	 */
	public boolean start() {
		List<String> missing = collectService.sourcesMissingTheLastSlot();
		warnLateSources();
		if (missing.isEmpty() || !busy.compareAndSet(false, true)) {
			return false;
		}
		log.info("Scheduled collection missed by {}: catching up", missing);
		try {
			executor.execute(() -> {
				try {
					missing.forEach(this::catchUp);
				}
				finally {
					busy.set(false);
				}
			});
		}
		catch (RuntimeException ex) {
			busy.set(false);
			throw ex;
		}
		return true;
	}

	public boolean isRunning() {
		return busy.get();
	}

	private void catchUp(String source) {
		for (int done = 1; done <= properties.maxRuns(); done++) {
			CollectRun run;
			try {
				run = collectService.collect(source, CollectTrigger.CATCH_UP, null);
			}
			catch (CollectAlreadyRunningException ex) {
				log.info("{} catch-up skipped: a collection is already running", source);
				return;
			}
			catch (RuntimeException ex) {
				log.error("{} catch-up failed", source, ex);
				return;
			}
			if (run.getStatus() != CollectStatus.SUCCESS) {
				log.warn("{} catch-up stopped after a failed collection; the next scheduled one tries again", source);
				return;
			}
			if (collectService.isUpToDate(source)) {
				return;
			}
			if (done < properties.maxRuns() && !pause(properties.pause())) {
				return;
			}
		}
		log.warn("{} catch-up stopped after {} collections; the next scheduled ones go on", source,
				properties.maxRuns());
	}

	private void warnLateSources() {
		for (CollectService.SourceState state : collectService.sources()) {
			if (state.overdue()) {
				log.warn("{}: no successful collection for {} working hours (alert above {})", state.source(),
						state.workingHoursSinceSuccess() == null ? "ever" : state.workingHoursSinceSuccess(),
						properties.alertWorkingHours());
			}
		}
	}

	/** @return false when interrupted: the catch-up stops */
	private static boolean pause(Duration pause) {
		if (pause.isZero()) {
			return true;
		}
		try {
			Thread.sleep(pause);
			return true;
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

}
