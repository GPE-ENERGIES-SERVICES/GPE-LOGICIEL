package eu.gpefrance.tenders.tender;

import java.util.EnumSet;
import java.util.Set;

/**
 * Step of the company's work on a tender, shared by the team. Independent of
 * the life cycle of the tender itself ({@link TenderStatus}).
 * <p>
 * Before the answer is sent, the team moves freely between the first steps.
 * Once the answer is prepared or sent, the result: AWARDED (the contract is won, its award
 * recorded) or REJECTED, then SIGNED once the contract is signed. A result
 * may be corrected: won and rejected swap, or back to waiting for it.
 */
public enum FollowUpStatus {

	NEW,

	/** To analyse. */
	TO_STUDY,

	/** Answer in preparation (GO). */
	RESPONDING,

	/** Answer sent to the buyer, waiting for the result. */
	SUBMITTED,

	/** NO GO: the company decided not to answer. */
	DISMISSED,

	/** The contract is won: its award is recorded. */
	AWARDED,

	/** The contract won is signed. */
	SIGNED,

	/** The offer or the application was rejected by the buyer. */
	REJECTED;

	private static final Set<FollowUpStatus> BEFORE_RESULT = EnumSet.of(NEW, TO_STUDY, RESPONDING, SUBMITTED,
			DISMISSED);

	/** @return whether the tender may go from this status to {@code next}; staying the same is always allowed */
	public boolean canBecome(FollowUpStatus next) {
		if (next == this) {
			return true;
		}
		return switch (this) {
			case NEW, TO_STUDY, DISMISSED -> BEFORE_RESULT.contains(next);
			// The result may come while the answer is still marked in preparation.
			case RESPONDING, SUBMITTED -> BEFORE_RESULT.contains(next) || next == AWARDED || next == REJECTED;
			case AWARDED -> next == SIGNED || next == REJECTED || next == SUBMITTED;
			case REJECTED -> next == AWARDED || next == SUBMITTED;
			case SIGNED -> next == AWARDED;
		};
	}

}
