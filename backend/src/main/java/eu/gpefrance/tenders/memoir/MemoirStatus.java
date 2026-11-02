package eu.gpefrance.tenders.memoir;

/**
 * Where a memoir stands. The outline is never final until a user validates
 * it; the last two states are chosen by a user.
 */
public enum MemoirStatus {

	/** Created, outline empty. */
	DRAFT,

	/** An outline exists, not validated. */
	OUTLINE_TO_VALIDATE,

	/** Outline validated by a user. */
	READY_TO_WRITE,

	/** Sections being written. */
	WRITING,

	TO_REVIEW,

	DONE

}
