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
 * @param validatedBy who validated the whole sheet, or null
 * @param errorKind why it failed or waits for a new attempt
 * @param nextAttemptAt when it is tried again, while waiting
 * @param startedAt when it was last sent to the AI, null before
 * @param requestedBy who asked for it, null when automatic
 * @param decision the decision help, null in the history and for the analyses made
 * before it existed
 */
public record AnalysisResponse(Long id, Long dossierId, int dossierVersion, AnalysisStatus status, String provider,
		String model, String promptVersion, FailureKind errorKind, int attempts, Instant nextAttemptAt,
		Instant startedAt, String requestedBy, Instant createdAt, Instant finishedAt, String errorMessage,
		Integer inputTokens, Integer outputTokens,
		BigDecimal cost, Instant noticeDeadline, String validatedBy, Instant validatedAt, Decision decision,
		List<FieldResponse> fields, List<DocumentResponse> documents) {

	/**
	 * @param value the value of the model, kept even when corrected
	 * @param documentName file of the source, to show next to the page
	 * @param decision what a user decided, which prevails over the model
	 */
	public record FieldResponse(Long id, String key, String value, String quote, Long documentId,
			String documentName, Integer pageNumber, FieldState state, FieldDecision decision,
			String correctedValue, String decidedBy, Instant decidedAt) {
	}

	/**
	 * @param analysed whether the document was given to the model
	 * @param readable whether a PDF version can be opened at a given page
	 * @param aiReason why the rules send it to the AI or not
	 */
	public record DocumentResponse(Long id, String fileName, PieceType pieceType, DocumentState state,
			String stateDetail, boolean analysed, boolean readable, String aiReason) {
	}

	/**
	 * Everything the summary sheet of a tender needs.
	 * @param configured whether an analysis can be started now
	 * @param unavailableReason why not, when not configured
	 * @param latest the latest analysis, or null
	 * @param history every analysis of the tender, latest first
	 * @param typicalSeconds how long an analysis of this kind usually takes once
	 * sent, null before any
	 */
	public record TenderAnalysis(boolean configured, String unavailableReason, List<GridField> grid,
			AnalysisResponse latest, List<AnalysisResponse> history, Long typicalSeconds) {
	}

	/**
	 * The analysis of the price schedules of a tender.
	 * @param configured whether an analysis can be started now
	 * @param unavailableReason why not, when not configured
	 * @param pieces the DPGF, BPU and DQE of the latest dossier, read or not;
	 * analysed tells whether the latest analysis used them
	 * @param latest the latest analysis of the prices, or null
	 * @param result its result, once done
	 * @param previous earlier analyses of the prices, kept
	 * @param typicalSeconds how long an analysis of the prices usually takes once
	 * sent, null before any
	 */
	public record FinancialAnalysis(boolean configured, String unavailableReason, List<DocumentResponse> pieces,
			AnalysisResponse latest, FinancialResult result, int previous, Long typicalSeconds) {
	}

}
