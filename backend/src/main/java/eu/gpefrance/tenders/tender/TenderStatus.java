package eu.gpefrance.tenders.tender;

/**
 * Life cycle of a tender. A tender is never deleted, only its status changes.
 */
public enum TenderStatus {
	ACTIVE, CLOSED, CANCELLED, UNSUCCESSFUL
}
