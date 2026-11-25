package eu.gpefrance.tenders.tender;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Changes the follow-up status of a tender by the rules of
 * {@link FollowUpStatus#canBecome}, and keeps a trace of each change.
 */
@Component
public class FollowUpHistory {

	private final TenderStatusChangeRepository changes;

	public FollowUpHistory(TenderStatusChangeRepository changes) {
		this.changes = changes;
	}

	/**
	 * @param comment the comment of the follow-up after the change
	 * @param reason why, kept in the history; may be null
	 * @throws FollowUpTransitionException when the rules forbid it
	 */
	void change(Tender tender, FollowUpStatus next, TenderStatusChange.Origin origin, String comment, String reason,
			String user, Instant now) {
		FollowUpStatus current = tender.getFollowUpStatus();
		if (!current.canBecome(next)) {
			throw new FollowUpTransitionException(current, next);
		}
		tender.followUp(next, comment, user, now);
		if (current != next) {
			changes.save(new TenderStatusChange(tender.getId(), current, next, origin, user, reason, now));
		}
	}

	/** The changes of a tender, the latest first. */
	List<TenderStatusChange> of(Long tenderId) {
		return changes.findByTenderIdOrderByChangedAtDescIdDesc(tenderId);
	}

}
