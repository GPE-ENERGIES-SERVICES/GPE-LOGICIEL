package eu.gpefrance.tenders.document;

public enum DocumentState {

	/** Waiting to be read. */
	PENDING,

	/** Text extracted from every page. */
	READ,

	/**
	 * Some or all pages have no text: probably scanned, optical character
	 * recognition (OCR) needed for them. Not done by the application.
	 */
	SCANNED,

	/** The file could not be read. */
	FAILED,

	/** Kept but not analysed in this version (spreadsheet, image, plan...). */
	NOT_ANALYZED

}
