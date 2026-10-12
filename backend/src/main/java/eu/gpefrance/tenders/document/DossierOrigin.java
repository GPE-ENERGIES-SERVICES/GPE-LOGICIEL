package eu.gpefrance.tenders.document;

/**
 * How the files of a dossier were obtained.
 */
public enum DossierOrigin {

	/** Uploaded by a user. */
	UPLOAD,

	/** Retrieved from a {@link DceLink} by a {@link DceRetriever}. */
	RETRIEVED

}
