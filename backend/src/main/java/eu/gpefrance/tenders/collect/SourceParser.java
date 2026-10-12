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

	/**
	 * Whether the notices of the source are linked by their procedure
	 * identifier when the notices they cite do not give their tender: a
	 * change, an award or a cancellation then joins the only tender of the
	 * same source and procedure, and an initial notice the tender made before
	 * it by its change. Only for a source whose procedure identifiers are
	 * reliable; never across sources.
	 */
	default boolean linksNoticesByProcedure() {
		return false;
	}

}
