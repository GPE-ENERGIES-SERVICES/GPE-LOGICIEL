package eu.gpefrance.tenders.document;

import java.time.Instant;

/**
 * @param source the source of the notice that gave the address
 * @param platform host of the address, without "www."
 * @param checkedAt last attempt to retrieve the files, null if never tried
 */
public record DceLinkResponse(Long id, String source, String url, String platform, DceLinkStatus status,
		String statusDetail, Instant firstSeenAt, Instant lastSeenAt, Instant checkedAt) {

	static DceLinkResponse from(DceLink link) {
		return new DceLinkResponse(link.getId(), link.getSource(), link.getUrl(), link.getPlatform(),
				link.getStatus(), link.getStatusDetail(), link.getFirstSeenAt(), link.getLastSeenAt(),
				link.getCheckedAt());
	}

}
