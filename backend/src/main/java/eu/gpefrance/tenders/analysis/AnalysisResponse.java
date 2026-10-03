package eu.gpefrance.tenders.analysis;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import eu.gpefrance.tenders.document.DocumentState;
import eu.gpefrance.tenders.document.PieceType;

/**
 * An analysis with its answers and the documents of the dossier, read or
 * not. In the history of a tender, answers and documents are left empty.
 * @param cost cost of the call to the model in euros, when prices are set
 * @param noticeDeadline deadline published in the notice, shown next to the
 * one read in the documents
 */
public record AnalysisResponse(Long id, Long dossierId, int dossierVersion, AnalysisStatus status, String model,
		String promptVersion, Instant createdAt, Instant finishedAt, String errorMessage, Integer inputTokens,
		Integer outputTokens, BigDecimal cost, Instant noticeDeadline, List<FieldResponse> fields,
		List<DocumentResponse> documents) {

	/**
	 * @param documentName file of the source, to show next to the page
	 */
	public record FieldResponse(Long id, String key, String value, String quote, Long documentId,
			String documentName, Integer pageNumber, FieldState state) {
	}

	/**
	 * @param analysed whether the document was given to the model
	 * @param readable whether a PDF version can be opened at a given page
	 */
	public record DocumentResponse(Long id, String fileName, PieceType pieceType, DocumentState state,
			String stateDetail, boolean analysed, boolean readable) {
	}

	/**
	 * Everything the summary sheet of a tender needs.
	 * @param configured whether an AI provider is configured
	 * @param latest the latest analysis, or null
	 * @param history every analysis of the tender, latest first
	 */
	public record TenderAnalysis(boolean configured, List<GridField> grid, AnalysisResponse latest,
			List<AnalysisResponse> history) {
	}

}
