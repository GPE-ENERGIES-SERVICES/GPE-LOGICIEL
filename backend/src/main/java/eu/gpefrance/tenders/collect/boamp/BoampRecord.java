package eu.gpefrance.tenders.collect.boamp;

import java.time.LocalDate;

/**
 * One notice returned by the BOAMP API, kept as raw JSON.
 * @param idweb identifier of the notice, null when the API did not give one
 */
public record BoampRecord(String idweb, LocalDate publishedAt, String json) {

	/**
	 * Without identifier, a notice cannot be told apart from the others: it is
	 * not stored.
	 */
	public boolean isValid() {
		return idweb != null;
	}

	/** The start of the JSON, enough to find the notice in the logs. */
	public String excerpt() {
		return json.length() <= 200 ? json : json.substring(0, 200) + "...";
	}

}
