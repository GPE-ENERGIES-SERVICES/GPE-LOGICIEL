package eu.gpefrance.tenders.company;

/**
 * What a file of the company proves or illustrates.
 */
public enum FileOwner {

	LOGO, DOCUMENT, CERTIFICATION, CREDENTIAL, REFERENCE, EQUIPMENT;

	/** Folder of its files, under the storage directory. */
	String folder() {
		return "company/" + name().toLowerCase();
	}

}
