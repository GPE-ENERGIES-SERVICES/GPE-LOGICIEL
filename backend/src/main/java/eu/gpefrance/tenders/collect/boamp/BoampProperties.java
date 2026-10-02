package eu.gpefrance.tenders.collect.boamp;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param baseUrl URL of the BOAMP dataset on the DILA open data portal
 * @param initialDays number of days fetched when no collection has succeeded yet
 */
@ConfigurationProperties("boamp")
public record BoampProperties(String baseUrl, int initialDays) {
}
