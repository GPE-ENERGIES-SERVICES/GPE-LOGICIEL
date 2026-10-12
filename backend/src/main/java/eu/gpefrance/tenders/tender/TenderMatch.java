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
 * Two tenders of different sources that are probably the same consultation,
 * found by a rule when the later one was created. Only a proposal: a person
 * confirms or rejects it, and nothing is merged automatically.
 */
@Entity
@Table(name = "tender_match")
public class TenderMatch {

	/** Same eForms procedure identifier in both sources. */
	public static final String SAME_PROCEDURE = "SAME_PROCEDURE";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** The tender created later. */
	@Column(name = "tender_id", nullable = false, updatable = false)
	private Long tenderId;

	/** The tender of another source that existed before. */
	@Column(name = "matched_tender_id", nullable = false, updatable = false)
	private Long matchedTenderId;

	@Column(nullable = false, updatable = false)
	private String rule;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private MatchStatus status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "decided_by")
	private String decidedBy;

	@Column(name = "decided_at")
	private Instant decidedAt;

	protected TenderMatch() {
	}

	public TenderMatch(Long tenderId, Long matchedTenderId, String rule, Instant now) {
		this.tenderId = tenderId;
		this.matchedTenderId = matchedTenderId;
		this.rule = rule;
		this.status = MatchStatus.PROPOSED;
		this.createdAt = now;
	}

	public Long getId() {
		return id;
	}

	public Long getTenderId() {
		return tenderId;
	}

	public Long getMatchedTenderId() {
		return matchedTenderId;
	}

	public String getRule() {
		return rule;
	}

	public MatchStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public String getDecidedBy() {
		return decidedBy;
	}

	public Instant getDecidedAt() {
		return decidedAt;
	}

	public enum MatchStatus {

		/** Found by the rule, waiting for a person. */
		PROPOSED,

		/** The same consultation, confirmed by a person. */
		CONFIRMED,

		/** Two different consultations, as decided by a person. */
		REJECTED

	}

}
