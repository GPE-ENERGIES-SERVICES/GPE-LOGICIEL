package eu.gpefrance.tenders.collect;

public enum NoticeType {

	/** Initial call for tenders. */
	CALL_FOR_TENDERS,

	/** Rectification that only describes the change in free text. */
	RECTIFICATION,

	/** Rectification republished as a complete call for tenders (eForms change notice). */
	COMPLETE_RECTIFICATION,

	AWARD,

	CANCELLATION,

	/** Prior information, contract modification... not handled. */
	OTHER

}
