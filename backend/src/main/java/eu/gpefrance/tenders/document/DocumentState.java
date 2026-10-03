package eu.gpefrance.tenders.document;

public enum DocumentState {

	/** Waiting to be read. */
	PENDING,

	/** Text extracted from every page. */
	READ,

	/** Text extracted, but some pages have none: probably scanned. */
	SCANNED,

	/** The file could not be read. */
	FAILED,

	/** Kept but not analysed in this version (spreadsheet, image, plan...). */
	NOT_ANALYZED

}
