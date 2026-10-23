package eu.gpefrance.tenders.eligibility;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The eligibility of the consultation file of one tender, as last evaluated.
 * One row per tender: evaluating again replaces it. Out of date when the
 * tender changed after it, or a saved search changed (revision).
 */
@Entity
@Table(name = "tender_eligibility")
public class TenderEligibility {

	@Id
	@Column(name = "tender_id")
	private Long tenderId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private EligibilityState state;

	/** Why, without the names of the saved searches, private to their owners. */
	@Column(nullable = false)
	private String reason;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "matched_alert_ids", nullable = false)
	private List<Long> matchedAlertIds = new ArrayList<>();

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "indeterminate_alert_ids", nullable = false)
	private List<Long> indeterminateAlertIds = new ArrayList<>();

	@Column(name = "alert_revision", nullable = false)
	private long alertRevision;

	@Column(name = "evaluated_at", nullable = false)
	private Instant evaluatedAt;

	protected TenderEligibility() {
	}

	TenderEligibility(Long tenderId) {
		this.tenderId = tenderId;
	}

	/**
	 * @return whether the state changed
	 */
	boolean set(EligibilityState newState, String newReason, List<Long> matched, List<Long> indeterminate,
			long revision, Instant now) {
		boolean changed = newState != state;
		this.state = newState;
		this.reason = newReason;
		this.matchedAlertIds = new ArrayList<>(matched);
		this.indeterminateAlertIds = new ArrayList<>(indeterminate);
		this.alertRevision = revision;
		this.evaluatedAt = now;
		return changed;
	}

	public Long getTenderId() {
		return tenderId;
	}

	public EligibilityState getState() {
		return state;
	}

	public String getReason() {
		return reason;
	}

	public List<Long> getMatchedAlertIds() {
		return matchedAlertIds;
	}

	public List<Long> getIndeterminateAlertIds() {
		return indeterminateAlertIds;
	}

	public long getAlertRevision() {
		return alertRevision;
	}

	public Instant getEvaluatedAt() {
		return evaluatedAt;
	}

}
