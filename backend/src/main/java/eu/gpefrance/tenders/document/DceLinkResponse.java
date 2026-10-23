package eu.gpefrance.tenders.document;

import java.time.Instant;
import java.util.List;

import eu.gpefrance.tenders.eligibility.EligibilityState;

/**
 * A link where the consultation file of a tender can be obtained, with the
 * eligibility of the tender and where its retrieval stands.
 * @param source the source of the notice that gave the address
 * @param platform host of the address, without "www."
 * @param checkedAt last attempt to retrieve the files, null if never tried
 * @param eligibility the eligibility of the tender, null before its first
 * evaluation
 * @param eligibilityReason why, without the names of the saved searches
 * @param yourAlerts the saved searches of the logged-in user that match the
 * tender
 * @param platformAllowed whether the automated retrieval is allowed for the
 * platform
 * @param actionRequired a person has to obtain the file, or to decide
 */
public record DceLinkResponse(Long id, String source, String url, String platform, DceLinkStatus status,
		String statusDetail, Instant firstSeenAt, Instant lastSeenAt, Instant checkedAt, int attempts,
		Instant nextAttemptAt, Instant fetchedAt, Integer fileCount, EligibilityState eligibility,
		String eligibilityReason, List<String> yourAlerts, boolean platformAllowed, boolean actionRequired) {

	static DceLinkResponse from(DceLink link, EligibilityState eligibility, String eligibilityReason,
			List<String> yourAlerts, boolean platformAllowed) {
		boolean actionRequired = switch (link.getStatus()) {
			case AVAILABLE, AUTOMATED_ACCESS_NOT_AUTHORIZED, ACCESS_REQUIRED, LINK_UNAVAILABLE, PERMANENT_ERROR,
					STORAGE_LIMIT_REACHED -> true;
			default -> false;
		};
		return new DceLinkResponse(link.getId(), link.getSource(), link.getUrl(), link.getPlatform(),
				link.getStatus(), link.getStatusDetail(), link.getFirstSeenAt(), link.getLastSeenAt(),
				link.getCheckedAt(), link.getAttempts(), link.getNextAttemptAt(), link.getFetchedAt(),
				link.getFileCount(), eligibility, eligibilityReason, yourAlerts, platformAllowed, actionRequired);
	}

}
