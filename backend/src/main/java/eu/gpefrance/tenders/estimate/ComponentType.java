package eu.gpefrance.tenders.estimate;

/**
 * Kind of cost of an item, each with its own sale coefficient.
 */
public enum ComponentType {

	/** Fourniture. */
	SUPPLY,

	/** Main-d'œuvre. */
	LABOR,

	/** Prestation. */
	SERVICE,

	/** Déplacements et frais. */
	TRAVEL,

	/** Location de matériel. */
	RENTAL,

	/** Sous-traitance. */
	SUBCONTRACT,

	/** Forfait. */
	LUMP_SUM,

	/** Divers. */
	OTHER

}
