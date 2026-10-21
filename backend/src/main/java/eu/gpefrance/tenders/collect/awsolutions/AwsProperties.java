package eu.gpefrance.tenders.collect.awsolutions;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Limits of the reading of marches-publics.info. Its terms of use call a
 * massive download, which leads to the blocking of the reader, more than 100
 * notices seen per hour or 500 per day: every page read, list or notice,
 * counts as one, and the limits stay below theirs.
 * @param baseUrl address of the site
 * @param natures natures of contract searched: T (works), S (services), F
 * (supplies)
 * @param pause wait before each page read
 * @param maxPagesPerHour pages read in the last hour after which the reading
 * stops
 * @param maxPagesPerDay pages read in the last 24 hours after which the
 * reading stops
 */
@ConfigurationProperties("aw-solutions")
public record AwsProperties(String baseUrl, @DefaultValue("T") List<String> natures,
		@DefaultValue("40s") Duration pause, @DefaultValue("90") int maxPagesPerHour,
		@DefaultValue("450") int maxPagesPerDay) {
}
