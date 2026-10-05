package eu.gpefrance.tenders.collect;

/**
 * Reads the stored notices of one source into the common format. Kept apart
 * from the {@link SourceConnector}: the stored notices can be read again at
 * any time without contacting the source.
 */
public interface SourceParser {

	/** Name of the source, as given by its connector. */
	String source();

	/**
	 * @param payload the notice as stored
	 * @throws RuntimeException when the notice cannot be read; it is then
	 * recorded in error and tried again at the next collection
	 */
	SourceNotice parse(String payload);

}
