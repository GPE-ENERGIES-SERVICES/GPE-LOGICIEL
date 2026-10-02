package eu.gpefrance.tenders.collect.boamp;

import java.time.LocalDate;

/**
 * One notice returned by the BOAMP API, kept as raw JSON.
 */
public record BoampRecord(String idweb, LocalDate publishedAt, String json) {
}
