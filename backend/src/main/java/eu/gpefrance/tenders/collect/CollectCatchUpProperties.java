package eu.gpefrance.tenders.collect;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Catch-up of the collections missed while the application was stopped, and
 * the limits of every collection that resumes on its own.
 * @param enabled whether missed collections are caught up
 * @param delay wait after the start of the application before the first
 * check, so that it is fully started
 * @param cron when the application checks that no scheduled collection was
 * missed (after a sleep of the machine, for instance), "-" for never
 * @param maxDaysPerRun publication days at most in a collection that resumes
 * after the last successful one; the following days are asked by the next
 * collection. A collection from a chosen day is not limited.
 * @param maxRuns collections at most per source in one catch-up
 * @param pause wait between two collections of a source in one catch-up
 * @param alertWorkingHours working hours (Monday to Friday, Paris time)
 * without a successful collection after which a source is shown late
 */
@ConfigurationProperties("collect.catch-up")
public record CollectCatchUpProperties(@DefaultValue("true") boolean enabled, @DefaultValue("2m") Duration delay,
		@DefaultValue("0 30 * * * *") String cron, @DefaultValue("14") int maxDaysPerRun,
		@DefaultValue("5") int maxRuns, @DefaultValue("1m") Duration pause,
		@DefaultValue("24") int alertWorkingHours) {

	public CollectCatchUpProperties {
		if (maxDaysPerRun < 2) {
			throw new IllegalArgumentException("collect.catch-up.max-days-per-run must be at least 2");
		}
	}

}
