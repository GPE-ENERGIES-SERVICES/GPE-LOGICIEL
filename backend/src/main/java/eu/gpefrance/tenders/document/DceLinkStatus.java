package eu.gpefrance.tenders.document;

/**
 * Where the retrieval of the consultation file from a link stands.
 */
public enum DceLinkStatus {

	/** The address is known; the file is obtained by hand on the platform. */
	AVAILABLE,

	/** To be retrieved automatically by a {@link DceRetriever} of the platform. */
	TO_FETCH,

	/** Retrieved: the files are a dossier of the tender. */
	FETCHED,

	/** The retrieval failed; the detail says why. */
	FAILED,

	/** The platform asks for an account, an identification or a CAPTCHA: obtained by hand. */
	ACCESS_REQUIRED

}
