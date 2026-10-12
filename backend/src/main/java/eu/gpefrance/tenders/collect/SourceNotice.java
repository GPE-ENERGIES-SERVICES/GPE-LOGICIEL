package eu.gpefrance.tenders.collect;

import java.util.List;
import java.util.Locale;

import eu.gpefrance.tenders.tender.TenderDetails;

/**
 * A notice read by the parser of its source, in the common format. Missing
 * information stays null or empty in the details.
 * @param externalId identifier of the notice in its source
 * @param linkedIds identifiers, in the same source, of the earlier notices
 * this one refers to
 * @param changeDescription what a rectification changes, as written in the notice
 * @param procedureId eForms identifier of the procedure, the same for every
 * notice of the consultation in every source; null when the notice has none.
 * Kept in lower case.
 */
public record SourceNotice(String externalId, NoticeType type, List<String> linkedIds, TenderDetails details,
		String changeDescription, String procedureId) {

	public SourceNotice {
		linkedIds = List.copyOf(linkedIds);
		procedureId = procedureId == null || procedureId.isBlank() ? null
				: procedureId.strip().toLowerCase(Locale.ROOT);
	}

}
