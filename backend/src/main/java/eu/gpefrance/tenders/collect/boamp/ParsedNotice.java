package eu.gpefrance.tenders.collect.boamp;

import java.util.List;

import eu.gpefrance.tenders.tender.TenderDetails;

/**
 * @param linkedIds idweb of the earlier notices this one refers to
 * @param changeDescription what a rectification changes, as written in the notice
 */
public record ParsedNotice(String idweb, BoampNoticeType type, List<String> linkedIds, TenderDetails details,
		String changeDescription) {

	public ParsedNotice {
		linkedIds = List.copyOf(linkedIds);
	}

}
