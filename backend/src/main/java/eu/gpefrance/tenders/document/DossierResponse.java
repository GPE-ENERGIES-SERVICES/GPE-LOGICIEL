package eu.gpefrance.tenders.document;

import java.time.Instant;
import java.util.List;

public record DossierResponse(Long id, int version, DossierStatus status, String errorMessage, String uploadedBy,
		Instant uploadedAt, Instant processedAt, List<DocumentResponse> documents) {

	static DossierResponse from(Dossier dossier) {
		return new DossierResponse(dossier.getId(), dossier.getVersion(), dossier.getStatus(),
				dossier.getErrorMessage(), dossier.getUploadedBy(), dossier.getUploadedAt(), dossier.getProcessedAt(),
				dossier.getDocuments().stream().map(DocumentResponse::from).toList());
	}

	/**
	 * @param readable whether a PDF version can be opened at a given page
	 */
	public record DocumentResponse(Long id, String fileName, long sizeBytes, PieceType pieceType,
			boolean pieceTypeCorrected, DocumentState state, String stateDetail, Integer pageCount,
			boolean readable) {

		static DocumentResponse from(Document document) {
			return new DocumentResponse(document.getId(), document.getFileName(), document.getSizeBytes(),
					document.getPieceType(), document.isPieceTypeCorrected(), document.getState(),
					document.getStateDetail(), document.getPageCount(), document.getPdfPath() != null);
		}

	}

}
