package eu.gpefrance.tenders.document;

/**
 * Choice of a user on sending a document to the AI, which prevails over the
 * rules of {@link DocumentTriage}. No choice: the rules decide.
 */
public enum AiChoice {

	/** Always sent, read by OCR when its pages have no text. */
	INCLUDE,

	/** Never sent. */
	EXCLUDE

}
