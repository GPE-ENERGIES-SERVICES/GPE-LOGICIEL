package eu.gpefrance.tenders.tender;

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
 * One change of the follow-up status of a tender, kept for good.
 */
@Entity
@Table(name = "tender_status_change")
public class TenderStatusChange {

	public enum Origin {

		/** Chosen by a user. */
		USER,

		/** A published award notice, confirmed by a user. */
		NOTICE

	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "tender_id", nullable = false, updatable = false)
	private Long tenderId;

	@Enumerated(EnumType.STRING)
	@Column(name = "old_status", nullable = false, updatable = false)
	private FollowUpStatus oldStatus;

	@Enumerated(EnumType.STRING)
	@Column(name = "new_status", nullable = false, updatable = false)
	private FollowUpStatus newStatus;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private Origin origin;

	@Column(name = "changed_by", updatable = false)
	private String changedBy;

	@Column(updatable = false)
	private String reason;

	@Column(name = "changed_at", nullable = false, updatable = false)
	private Instant changedAt;

	protected TenderStatusChange() {
	}

	TenderStatusChange(Long tenderId, FollowUpStatus oldStatus, FollowUpStatus newStatus, Origin origin,
			String changedBy, String reason, Instant changedAt) {
		this.tenderId = tenderId;
		this.oldStatus = oldStatus;
		this.newStatus = newStatus;
		this.origin = origin;
		this.changedBy = changedBy;
		this.reason = reason;
		this.changedAt = changedAt;
	}

	public Long getId() {
		return id;
	}

	public FollowUpStatus getOldStatus() {
		return oldStatus;
	}

	public FollowUpStatus getNewStatus() {
		return newStatus;
	}

	public Origin getOrigin() {
		return origin;
	}

	public String getChangedBy() {
		return changedBy;
	}

	public String getReason() {
		return reason;
	}

	public Instant getChangedAt() {
		return changedAt;
	}

}
