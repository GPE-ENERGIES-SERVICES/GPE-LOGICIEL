package eu.gpefrance.tenders.document;

import java.time.Instant;

/**
 * A change of status of a link of consultation file.
 * @param tenderId the tender of the link, null when not asked
 * @param platform the platform of the link, null when not asked
 */
public record DceEventResponse(Long id, Long dceLinkId, Long tenderId, String platform, Instant occurredAt,
		DceLinkStatus fromStatus, DceLinkStatus toStatus, String detail) {

	static DceEventResponse from(DceLinkEvent event, DceLink link) {
		return new DceEventResponse(event.getId(), event.getDceLinkId(), link == null ? null : link.getTenderId(),
				link == null ? null : link.getPlatform(), event.getOccurredAt(), event.getFromStatus(),
				event.getToStatus(), event.getDetail());
	}

}
