package eu.gpefrance.tenders.document;

/**
 * How the text of a page was obtained: native text of a PDF (Word documents
 * are converted to PDF first), recognition of a scanned page, or a section
 * of a plain text or HTML file, or part of a sheet of a spreadsheet (rows and
 * cells with their coordinates).
 */
public enum PageMethod {
	NATIVE, OCR, TEXT, HTML, SHEET
}
