package eu.gpefrance.tenders.document;

/**
 * Where the retrieval of the consultation file from a link stands. The
 * eligibility of the tender (it matches an active saved search) is kept
 * apart, in the tender eligibility; this status says what was done, or not,
 * with the link. A link is never a file: the files obtained are dossiers.
 * TO_FETCH and FAILED, used before V23, became ELIGIBLE_PENDING and
 * PERMANENT_ERROR.
 */
public enum DceLinkStatus {

	/** The address is known; nothing is decided: the file is obtained by hand on the platform. */
	AVAILABLE,

	/** The tender matches no active saved search: the file is not retrieved. */
	NOT_ELIGIBLE,

	/** The tender is eligible and the platform allowed: waiting for its retrieval. */
	ELIGIBLE_PENDING,

	/** Being retrieved by a worker. */
	IN_PROGRESS,

	/** Retrieved and validated: the files are a dossier of the tender. */
	FETCHED,

	/** The retrieval failed for a reason that may pass: tried again later, a limited number of times. */
	TEMPORARY_ERROR,

	/** The retrieval failed for good (invalid file, too many attempts...): tried again only by hand. */
	PERMANENT_ERROR,

	/** The address answers that the file does not exist (or no longer). */
	LINK_UNAVAILABLE,

	/** The platform asks for an account, an identification or a CAPTCHA: obtained by hand. */
	ACCESS_REQUIRED,

	/** The tender is eligible but no automated access to the platform is allowed: obtained by hand. */
	AUTOMATED_ACCESS_NOT_AUTHORIZED,

	/** The quota of the retrieved files is reached: waits for space freed by hand or a larger quota. */
	STORAGE_LIMIT_REACHED;

	/** Statuses that a new evaluation of the eligibility never changes. */
	public boolean isSettled() {
		return this == IN_PROGRESS || this == FETCHED || this == PERMANENT_ERROR || this == LINK_UNAVAILABLE
				|| this == ACCESS_REQUIRED;
	}

}
