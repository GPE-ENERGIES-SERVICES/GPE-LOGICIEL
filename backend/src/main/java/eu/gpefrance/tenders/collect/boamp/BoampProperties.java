package eu.gpefrance.tenders.collect.boamp;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param baseUrl URL of the BOAMP dataset on the DILA open data portal
 */
@ConfigurationProperties("boamp")
public record BoampProperties(String baseUrl) {
}
