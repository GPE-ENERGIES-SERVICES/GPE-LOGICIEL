package eu.gpefrance.tenders.estimate;

import java.time.Instant;
import java.util.List;

/**
 * The technical solution of an item, as the technical choices decide it: what
 * is installed and how, what the documents say (each fact with its source,
 * its quote found in the document), what is missing or to confirm. A proposal
 * of the AI until a user changes or validates it.
 * @param status how sure the information is; null when nothing is decided
 * @param origin AI while a user did not change it, USER otherwise
 */
public record TechnicalSolution(String type, String characteristics, String method, Status status, List<Fact> facts,
		List<String> missing, String origin, String validatedBy, Instant validatedAt) {

	public enum Status {

		/** Enough is known to estimate the item. */
		COMPLETE,

		/** A part is supposed: to confirm before the offer. */
		TO_CONFIRM,

		/** Too little is known to estimate the item. */
		INSUFFICIENT

	}

	/**
	 * What a document says of the item.
	 * @param documentId the document, null when not cited
	 * @param quote the exact words, found on the page
	 */
	public record Fact(String text, Long documentId, String fileName, Integer page, String quote) {
	}

	/** What a user writes: the facts read in the documents stay as read. */
	public record Request(String type, String characteristics, String method, Status status, List<String> missing) {
	}

}
