package eu.gpefrance.tenders.collect;

import java.time.LocalDate;

/**
 * One notice as received from a source, kept as raw JSON.
 * @param externalId identifier of the notice in its source, null when the
 * source did not give one
 * @param payload the notice as JSON; a source that does not publish JSON
 * (HTML page, file) gives what it read as JSON
 */
public record SourceRecord(String externalId, LocalDate publishedAt, String payload) {

	/**
	 * Without identifier, a notice cannot be told apart from the others: it is
	 * not stored.
	 */
	public boolean isValid() {
		return externalId != null;
	}

	/** The start of the payload, enough to find the notice in the logs. */
	public String excerpt() {
		return payload.length() <= 200 ? payload : payload.substring(0, 200) + "...";
	}

}
