package eu.gpefrance.tenders.collect;

import java.util.List;

import eu.gpefrance.tenders.tender.TenderDetails;

/**
 * A notice read by the parser of its source, in the common format. Missing
 * information stays null or empty in the details.
 * @param externalId identifier of the notice in its source
 * @param linkedIds identifiers, in the same source, of the earlier notices
 * this one refers to
 * @param changeDescription what a rectification changes, as written in the notice
 */
public record SourceNotice(String externalId, NoticeType type, List<String> linkedIds, TenderDetails details,
		String changeDescription) {

	public SourceNotice {
		linkedIds = List.copyOf(linkedIds);
	}

}
