package eu.gpefrance.tenders.estimate;

/**
 * How the unit price of an item is set.
 */
public enum PriceMode {

	/** From its costs and the coefficients. */
	COMPOSED,

	/** Entered by a user (lump sum, price imposed); its costs only show the margin. */
	FIXED,

	/** Written as words (PM, inclus, sans objet): amount 0. */
	TEXT

}
