package eu.gpefrance.tenders.analysis;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import eu.gpefrance.tenders.analysis.AnalysisResponse.DocumentResponse;
import eu.gpefrance.tenders.analysis.AnalysisResponse.FieldResponse;
import eu.gpefrance.tenders.analysis.AnalysisResponse.TenderAnalysis;
import eu.gpefrance.tenders.document.Document;
import eu.gpefrance.tenders.document.Dossier;
import eu.gpefrance.tenders.document.DossierRepository;
import eu.gpefrance.tenders.tender.Tender;
import eu.gpefrance.tenders.tender.TenderNotFoundException;
import eu.gpefrance.tenders.tender.TenderRepository;

/**
 * Consultation of the analyses.
 */
@Service
public class AnalysisQueries {

	private final AnalysisRepository analyses;

	private final DossierRepository dossiers;

	private final TenderRepository tenders;

	private final AnalysisGate gate;

	private final AnalysisProperties properties;

	public AnalysisQueries(AnalysisRepository analyses, DossierRepository dossiers, TenderRepository tenders,
			AnalysisGate gate, AnalysisProperties properties) {
		this.analyses = analyses;
		this.dossiers = dossiers;
		this.tenders = tenders;
		this.gate = gate;
		this.properties = properties;
	}

	@Transactional(readOnly = true)
	public TenderAnalysis ofTender(Long tenderId) {
		Tender tender = tenders.findById(tenderId).orElseThrow(() -> new TenderNotFoundException(tenderId));
		List<Analysis> all = analyses.findByTenderIdOrderByIdDesc(tenderId);
		AnalysisResponse latest = all.isEmpty() ? null : detail(all.getFirst(), tender.getDeadline());
		AnalysisGate.Status status = gate.status();
		return new TenderAnalysis(status.ready(), status.ready() ? null : status.message(), ExtractionGrid.FIELDS,
				latest, all.stream().map(this::summary).toList());
	}

	/**
	 * Where an analysis stands, without its answers nor any text.
	 */
	@Transactional(readOnly = true)
	public AnalysisResponse summary(Long analysisId) {
		return summary(analyses.findById(analysisId).orElseThrow(() -> new AnalysisNotFoundException(analysisId)));
	}

	private AnalysisResponse summary(Analysis analysis) {
		return response(analysis, dossier(analysis), null, List.of(), List.of());
	}

	private AnalysisResponse detail(Analysis analysis, Instant noticeDeadline) {
		Dossier dossier = dossier(analysis);
		Map<Long, Document> documents = dossier.getDocuments()
			.stream()
			.collect(Collectors.toMap(Document::getId, Function.identity()));
		List<FieldResponse> fields = analysis.getFields()
			.stream()
			.map(field -> new FieldResponse(field.getId(), field.getFieldKey(), field.getValue(), field.getQuote(),
					field.getDocumentId(),
					field.getDocumentId() == null || !documents.containsKey(field.getDocumentId()) ? null
							: documents.get(field.getDocumentId()).getFileName(),
					field.getPageNumber(), field.getState(), field.getDecision(), field.getCorrectedValue(),
					field.getDecidedBy(), field.getDecidedAt()))
			.toList();
		List<DocumentResponse> documentResponses = dossier.getDocuments()
			.stream()
			.map(document -> new DocumentResponse(document.getId(), document.getFileName(), document.getPieceType(),
					document.getState(), document.getStateDetail(),
					analysis.getDocumentIds().contains(document.getId()), document.getPdfPath() != null))
			.toList();
		return response(analysis, dossier, noticeDeadline, fields, documentResponses);
	}

	private AnalysisResponse response(Analysis analysis, Dossier dossier, Instant noticeDeadline,
			List<FieldResponse> fields, List<DocumentResponse> documents) {
		return new AnalysisResponse(analysis.getId(), analysis.getDossierId(), dossier.getVersion(),
				analysis.getStatus(), analysis.getProvider(), analysis.getModel(), analysis.getPromptVersion(),
				analysis.getErrorKind(), analysis.getAttempts(), analysis.getNextAttemptAt(), analysis.getRequestedBy(),
				analysis.getCreatedAt(),
				analysis.getFinishedAt(), analysis.getErrorMessage(), analysis.getInputTokens(),
				analysis.getOutputTokens(), cost(analysis), noticeDeadline, analysis.getValidatedBy(),
				analysis.getValidatedAt(), fields, documents);
	}

	private Dossier dossier(Analysis analysis) {
		return dossiers.findById(analysis.getDossierId()).orElseThrow();
	}

	private BigDecimal cost(Analysis analysis) {
		return analysis.getInputTokens() == null ? null
				: properties.cost(analysis.getInputTokens(), analysis.getOutputTokens());
	}

}
