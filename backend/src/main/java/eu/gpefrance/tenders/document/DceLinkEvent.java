package eu.gpefrance.tenders.document;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A change of status of a {@link DceLink}, with its reason: why a retrieval
 * was queued, skipped, blocked or failed.
 */
@Entity
@Table(name = "dce_link_event")
public class DceLinkEvent {

	private static final int MAX_DETAIL_LENGTH = 2000;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "dce_link_id", nullable = false, updatable = false)
	private Long dceLinkId;

	@Column(name = "occurred_at", nullable = false, updatable = false)
	private Instant occurredAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "from_status", updatable = false)
	private DceLinkStatus fromStatus;

	@Enumerated(EnumType.STRING)
	@Column(name = "to_status", nullable = false, updatable = false)
	private DceLinkStatus toStatus;

	@Column(updatable = false)
	private String detail;

	protected DceLinkEvent() {
	}

	DceLinkEvent(Long dceLinkId, Instant occurredAt, DceLinkStatus fromStatus, DceLinkStatus toStatus, String detail) {
		this.dceLinkId = dceLinkId;
		this.occurredAt = occurredAt;
		this.fromStatus = fromStatus;
		this.toStatus = toStatus;
		this.detail = detail == null || detail.length() <= MAX_DETAIL_LENGTH ? detail
				: detail.substring(0, MAX_DETAIL_LENGTH);
	}

	public Long getId() {
		return id;
	}

	public Long getDceLinkId() {
		return dceLinkId;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}

	public DceLinkStatus getFromStatus() {
		return fromStatus;
	}

	public DceLinkStatus getToStatus() {
		return toStatus;
	}

	public String getDetail() {
		return detail;
	}

}
