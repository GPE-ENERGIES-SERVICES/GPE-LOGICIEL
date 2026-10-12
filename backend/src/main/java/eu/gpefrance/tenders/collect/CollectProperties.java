package eu.gpefrance.tenders.collect;

import java.time.Duration;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param initialDays number of days fetched from a source never collected
 * successfully; an import may start earlier with its own date
 * @param disabledSources sources not collected, by name
 * @param retryDelay pause before the single new attempt of a failed request
 * to a source, see {@link HttpRetry}
 */
@ConfigurationProperties("collect")
public record CollectProperties(@DefaultValue("7") int initialDays, @DefaultValue Set<String> disabledSources,
		@DefaultValue("10s") Duration retryDelay) {
}
