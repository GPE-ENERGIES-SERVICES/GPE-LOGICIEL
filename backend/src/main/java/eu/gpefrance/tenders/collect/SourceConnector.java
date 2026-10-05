package eu.gpefrance.tenders.collect;

import java.time.LocalDate;
import java.util.List;

/**
 * Reads the notices of one source. Each source is a Spring bean: the
 * collection, its journal and its schedule find every connector without
 * being changed. The notices are read by the {@link SourceParser} of the same
 * source.
 */
public interface SourceConnector {

	/** Name of the source, stored with its notices and tenders, such as "BOAMP". */
	String source();

	SourceAccess access();

	/**
	 * The notices published on the given day. A source that cannot filter by
	 * day returns its current notices: those already stored are skipped.
	 * Throws when the source cannot be read; the collection then fails and the
	 * next one fetches the same days again.
	 */
	List<SourceRecord> fetchPublishedOn(LocalDate day);

}
