package eu.gpefrance.tenders.analysis;

/**
 * Expected form of a value, used to check it against its quote.
 */
public enum FieldKind {

	/** Free text: only the quote is checked. */
	TEXT,

	/** A date, written yyyy-MM-dd. */
	DATE,

	/** A date with an optional time, written yyyy-MM-dd'T'HH:mm or yyyy-MM-dd. */
	DATE_TIME,

	/** An amount in euros excluding tax, digits only. */
	AMOUNT,

	/** One of a few fixed answers. */
	CHOICE

}
