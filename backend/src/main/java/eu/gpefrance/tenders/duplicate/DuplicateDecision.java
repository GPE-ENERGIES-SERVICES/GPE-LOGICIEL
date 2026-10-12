package eu.gpefrance.tenders.duplicate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The decision of a person on two tenders that may be the same consultation,
 * kept as a trace. A merge also keeps what is needed to undo it exactly.
 */
@Entity
@Table(name = "duplicate_decision")
public class DuplicateDecision {

	public enum Kind {

		/** The other tender was merged into the kept one. */
		MERGE,

		/** Two different consultations: no longer proposed together. */
		NOT_DUPLICATE

	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private Kind kind;

	@Column(name = "kept_tender_id", nullable = false, updatable = false)
	private Long keptTenderId;

	@Column(name = "other_tender_id", nullable = false, updatable = false)
	private Long otherTenderId;

	/** Why the two tenders were considered duplicates, at the time of the decision. */
	@Column(nullable = false, updatable = false)
	private String reason;

	@Column(name = "decided_by", nullable = false, updatable = false)
	private String decidedBy;

	@Column(name = "decided_at", nullable = false, updatable = false)
	private Instant decidedAt;

	/** For a merge: the tender kept, as it was before (a TenderState). */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "kept_before", updatable = false)
	private String keptBefore;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "moved_notice_ids", updatable = false)
	private List<Long> movedNoticeIds;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "moved_link_ids", updatable = false)
	private List<Long> movedLinkIds;

	/** For a merge: notice id to the kind it had before being read again. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "relabeled_kinds", updatable = false)
	private Map<Long, String> relabeledKinds;

	@Column(name = "undone_by")
	private String undoneBy;

	@Column(name = "undone_at")
	private Instant undoneAt;

	protected DuplicateDecision() {
	}

	static DuplicateDecision merge(Long keptTenderId, Long otherTenderId, String reason, String user,
			String keptBefore, List<Long> movedNoticeIds, List<Long> movedLinkIds, Map<Long, String> relabeledKinds,
			Instant now) {
		DuplicateDecision decision = new DuplicateDecision(Kind.MERGE, keptTenderId, otherTenderId, reason, user, now);
		decision.keptBefore = keptBefore;
		decision.movedNoticeIds = new ArrayList<>(movedNoticeIds);
		decision.movedLinkIds = new ArrayList<>(movedLinkIds);
		decision.relabeledKinds = new HashMap<>(relabeledKinds);
		return decision;
	}

	static DuplicateDecision notDuplicate(Long firstTenderId, Long secondTenderId, String reason, String user,
			Instant now) {
		return new DuplicateDecision(Kind.NOT_DUPLICATE, firstTenderId, secondTenderId, reason, user, now);
	}

	private DuplicateDecision(Kind kind, Long keptTenderId, Long otherTenderId, String reason, String user,
			Instant now) {
		this.kind = kind;
		this.keptTenderId = keptTenderId;
		this.otherTenderId = otherTenderId;
		this.reason = reason;
		this.decidedBy = user;
		this.decidedAt = now;
	}

	void undo(String user, Instant now) {
		this.undoneBy = user;
		this.undoneAt = now;
	}

	public boolean isActive() {
		return undoneAt == null;
	}

	public Long getId() {
		return id;
	}

	public Kind getKind() {
		return kind;
	}

	public Long getKeptTenderId() {
		return keptTenderId;
	}

	public Long getOtherTenderId() {
		return otherTenderId;
	}

	public String getReason() {
		return reason;
	}

	public String getDecidedBy() {
		return decidedBy;
	}

	public Instant getDecidedAt() {
		return decidedAt;
	}

	public String getKeptBefore() {
		return keptBefore;
	}

	public List<Long> getMovedNoticeIds() {
		return movedNoticeIds == null ? List.of() : movedNoticeIds;
	}

	public List<Long> getMovedLinkIds() {
		return movedLinkIds == null ? List.of() : movedLinkIds;
	}

	public Map<Long, String> getRelabeledKinds() {
		return relabeledKinds == null ? Map.of() : relabeledKinds;
	}

	public String getUndoneBy() {
		return undoneBy;
	}

	public Instant getUndoneAt() {
		return undoneAt;
	}

}
