package eu.gpefrance.tenders.document;

/**
 * How the text of a page was obtained: native text of a PDF (Word documents
 * are converted to PDF first), recognition of a scanned page, or a section
 * of a plain text or HTML file.
 */
public enum PageMethod {
	NATIVE, OCR, TEXT, HTML
}
