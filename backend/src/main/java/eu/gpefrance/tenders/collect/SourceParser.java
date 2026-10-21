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

	/**
	 * Whether the source defers to the other sources for the consultations
	 * they publish too: a notice that has no tender in its source, whose
	 * procedure identifier is the one of a single tender of another source,
	 * is only added to the history of that tender, which it never changes.
	 * With several such tenders, the notice is set aside for a person. The
	 * other sources are unchanged: when they publish later a consultation of
	 * a tender of this source, a match is proposed instead.
	 */
	default boolean defersToOtherSources() {
		return false;
	}

	/**
	 * Whether the source publishes no rectification but republishes its whole
	 * notice, under the same identifier, each time it changes: a new version
	 * of a notice whose tender is known then updates it like a complete
	 * rectification, when no later version was applied, and is kept in its
	 * history as such. Only for a source without rectification notices.
	 */
	default boolean republishesChangedNotices() {
		return false;
	}

}
