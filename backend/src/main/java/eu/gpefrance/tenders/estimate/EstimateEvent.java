package eu.gpefrance.tenders.estimate;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * What happened to an estimate, never changed once written.
 */
@Entity
@Table(name = "estimate_event")
public class EstimateEvent {

	public enum Kind {

		CREATED, NEW_VERSION, IMPORTED, RATES_COPIED, VALIDATED, SUPERSEDED, SUGGESTION_ACCEPTED

	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "estimate_id", nullable = false, updatable = false)
	private Long estimateId;

	@Column(nullable = false, updatable = false)
	private String kind;

	@Column(updatable = false)
	private String detail;

	@Column(name = "created_by", updatable = false)
	private String createdBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected EstimateEvent() {
	}

	EstimateEvent(Long estimateId, Kind kind, String detail, String user, Instant now) {
		this.estimateId = estimateId;
		this.kind = kind.name();
		this.detail = detail;
		this.createdBy = user;
		this.createdAt = now;
	}

	public record Response(Long id, String kind, String detail, String createdBy, Instant createdAt) {
	}

	Response response() {
		return new Response(id, kind, detail, createdBy, createdAt);
	}

}
