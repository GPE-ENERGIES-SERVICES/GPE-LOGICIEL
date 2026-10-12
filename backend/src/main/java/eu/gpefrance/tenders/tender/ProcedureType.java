package eu.gpefrance.tenders.tender;

public enum ProcedureType {
	OPEN, RESTRICTED, NEGOTIATED, COMPETITIVE_DIALOGUE, ADAPTED, CONCESSION,

	/**
	 * Qualification system of an entity of networks (energy, transport...):
	 * companies apply to be qualified, the contracts are then awarded among
	 * the qualified ones.
	 */
	QUALIFICATION_SYSTEM,

	/**
	 * Prior information notice used as a call for competition: companies
	 * express their interest, then are invited to tender.
	 */
	PRIOR_INFORMATION_CALL,

	OTHER
}
