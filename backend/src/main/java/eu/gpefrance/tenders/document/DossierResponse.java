package eu.gpefrance.tenders.document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import eu.gpefrance.tenders.document.DocumentTriage.Decision;

/**
 * @param autoAnalysis whether its analysis may start by itself once read
 */
public record DossierResponse(Long id, int version, DossierOrigin origin, Long dceLinkId, DossierStatus status,
		String errorMessage, String uploadedBy, Instant uploadedAt, Instant processedAt, boolean autoAnalysis,
		List<DocumentResponse> documents) {

	/**
	 * @param decisions what is sent to the AI, by document
	 */
	static DossierResponse from(Dossier dossier, Map<Long, Decision> decisions) {
		return new DossierResponse(dossier.getId(), dossier.getVersion(), dossier.getOrigin(),
				dossier.getDceLinkId(), dossier.getStatus(), dossier.getErrorMessage(), dossier.getUploadedBy(),
				dossier.getUploadedAt(), dossier.getProcessedAt(), dossier.isAutoAnalysis(), dossier.getDocuments()
					.stream()
					.map(document -> DocumentResponse.from(document, decisions.get(document.getId())))
					.toList());
	}

	/**
	 * @param readable whether a PDF version can be opened at a given page
	 * @param aiUse whether it is sent to the AI, and for what
	 * @param aiReason why
	 * @param aiChoice the choice of a user, which prevails, or null
	 */
	public record DocumentResponse(Long id, String fileName, long sizeBytes, PieceType pieceType,
			boolean pieceTypeCorrected, DocumentState state, String stateDetail, Integer pageCount,
			boolean readable, DocumentTriage.Use aiUse, String aiReason, AiChoice aiChoice) {

		static DocumentResponse from(Document document, Decision decision) {
			return new DocumentResponse(document.getId(), document.getFileName(), document.getSizeBytes(),
					document.getPieceType(), document.isPieceTypeCorrected(), document.getState(),
					document.getStateDetail(), document.getPageCount(), document.getPdfPath() != null,
					decision == null ? null : decision.use(), decision == null ? null : decision.reason(),
					document.getAiChoice());
		}

	}

}
