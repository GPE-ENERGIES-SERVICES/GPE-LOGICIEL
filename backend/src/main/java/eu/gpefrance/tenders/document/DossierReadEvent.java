package eu.gpefrance.tenders.document;

/**
 * Published when all the documents of a dossier have been read.
 */
public record DossierReadEvent(Long dossierId) {
}
