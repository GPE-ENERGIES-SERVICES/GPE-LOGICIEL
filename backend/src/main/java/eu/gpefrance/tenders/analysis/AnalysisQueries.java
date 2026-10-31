package eu.gpefrance.tenders.analysis;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import eu.gpefrance.tenders.analysis.AnalysisResponse.DocumentResponse;
import eu.gpefrance.tenders.analysis.AnalysisResponse.FinancialAnalysis;
import eu.gpefrance.tenders.analysis.AnalysisResponse.FieldResponse;
import eu.gpefrance.tenders.analysis.AnalysisResponse.TenderAnalysis;
import eu.gpefrance.tenders.document.Document;
import eu.gpefrance.tenders.document.DocumentPageRepository;
import eu.gpefrance.tenders.document.DocumentTriage;
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

	private final DocumentPageRepository pages;

	public AnalysisQueries(AnalysisRepository analyses, DossierRepository dossiers, TenderRepository tenders,
			AnalysisGate gate, AnalysisProperties properties, DocumentPageRepository pages) {
		this.pages = pages;
		this.analyses = analyses;
		this.dossiers = dossiers;
		this.tenders = tenders;
		this.gate = gate;
		this.properties = properties;
	}

	@Transactional(readOnly = true)
	public TenderAnalysis ofTender(Long tenderId) {
		Tender tender = tenders.findById(tenderId).orElseThrow(() -> new TenderNotFoundException(tenderId));
		List<Analysis> all = analyses.findByTenderIdAndKindOrderByIdDesc(tenderId, AnalysisKind.DCE_SUMMARY);
		AnalysisResponse latest = all.isEmpty() ? null : detail(all.getFirst(), tender.getDeadline());
		AnalysisGate.Status status = gate.status();
		return new TenderAnalysis(status.ready(), status.ready() ? null : status.message(), ExtractionGrid.FIELDS,
				latest, all.stream().map(this::summary).toList(), typicalSeconds(AnalysisKind.DCE_SUMMARY));
	}

	/**
	 * The analysis of the price schedules of a tender: the financial pieces of
	 * its latest dossier, and the latest analysis of them with its result.
	 */
	@Transactional(readOnly = true)
	public FinancialAnalysis financialOfTender(Long tenderId) {
		tenders.findById(tenderId).orElseThrow(() -> new TenderNotFoundException(tenderId));
		List<Analysis> all = analyses.findByTenderIdAndKindOrderByIdDesc(tenderId, AnalysisKind.FINANCIAL);
		Analysis latest = all.isEmpty() ? null : all.getFirst();
		List<DocumentResponse> pieces = dossiers.findByTenderIdOrderByVersionDesc(tenderId)
			.stream()
			.findFirst()
			.map(dossier -> {
				Map<Long, DocumentTriage.Decision> decisions = DocumentTriage.decide(dossier.getDocuments(), pages);
				return dossier.getDocuments()
				.stream()
				.filter(document -> AnalysisService.FINANCIAL_PIECES.contains(document.getPieceType()))
				.map(document -> new DocumentResponse(document.getId(), document.getFileName(),
						document.getPieceType(), document.getState(), document.getStateDetail(),
						latest != null && latest.getDocumentIds().contains(document.getId()),
						document.getPdfPath() != null, reason(decisions, document)))
				.toList();
			})
			.orElse(List.of());
		AnalysisGate.Status status = gate.status();
		return new FinancialAnalysis(status.ready(), status.ready() ? null : status.message(), pieces,
				latest == null ? null : detail(latest, null), latest == null ? null : latest.getFinancial(),
				Math.max(0, all.size() - 1), typicalSeconds(AnalysisKind.FINANCIAL));
	}

	/**
	 * Where an analysis stands, without its answers nor any text.
	 */
	@Transactional(readOnly = true)
	public AnalysisResponse summary(Long analysisId) {
		return summary(analyses.findById(analysisId).orElseThrow(() -> new AnalysisNotFoundException(analysisId)));
	}

	private AnalysisResponse summary(Analysis analysis) {
		return response(analysis, dossier(analysis), null, null, List.of(), List.of());
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
		Map<Long, DocumentTriage.Decision> decisions = DocumentTriage.decide(dossier.getDocuments(), pages);
		List<DocumentResponse> documentResponses = dossier.getDocuments()
			.stream()
			.map(document -> new DocumentResponse(document.getId(), document.getFileName(), document.getPieceType(),
					document.getState(), document.getStateDetail(),
					analysis.getDocumentIds().contains(document.getId()), document.getPdfPath() != null,
					reason(decisions, document)))
			.toList();
		return response(analysis, dossier, noticeDeadline, analysis.getDecision(), fields, documentResponses);
	}

	private AnalysisResponse response(Analysis analysis, Dossier dossier, Instant noticeDeadline, Decision decision,
			List<FieldResponse> fields, List<DocumentResponse> documents) {
		return new AnalysisResponse(analysis.getId(), analysis.getDossierId(), dossier.getVersion(),
				analysis.getStatus(), analysis.getProvider(), analysis.getModel(), analysis.getPromptVersion(),
				analysis.getErrorKind(), analysis.getAttempts(), analysis.getNextAttemptAt(), analysis.getStartedAt(),
				analysis.getRequestedBy(),
				analysis.getCreatedAt(),
				analysis.getFinishedAt(), analysis.getErrorMessage(), analysis.getInputTokens(),
				analysis.getOutputTokens(), cost(analysis), noticeDeadline, analysis.getValidatedBy(),
				analysis.getValidatedAt(), decision, fields, documents);
	}

	/** Why the rules send a document to the AI or not. */
	private static String reason(Map<Long, DocumentTriage.Decision> decisions, Document document) {
		DocumentTriage.Decision decision = decisions.get(document.getId());
		return decision == null ? null : decision.reason();
	}

	/**
	 * How long the latest analyses of a kind took once sent, the middle one:
	 * what to expect for the next one. Null before any.
	 */
	private Long typicalSeconds(AnalysisKind kind) {
		List<Long> seconds = analyses.findTop20ByKindAndStatusAndStartedAtNotNullOrderByIdDesc(kind, AnalysisStatus.DONE)
			.stream()
			.filter(analysis -> analysis.getFinishedAt() != null)
			.map(analysis -> Duration.between(analysis.getStartedAt(), analysis.getFinishedAt()).toSeconds())
			.sorted()
			.toList();
		return seconds.isEmpty() ? null : seconds.get(seconds.size() / 2);
	}

	private Dossier dossier(Analysis analysis) {
		return dossiers.findById(analysis.getDossierId()).orElseThrow();
	}

	private BigDecimal cost(Analysis analysis) {
		return analysis.getInputTokens() == null ? null
				: properties.cost(analysis.getInputTokens(), analysis.getOutputTokens());
	}

}
