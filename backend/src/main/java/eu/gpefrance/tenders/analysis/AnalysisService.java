package eu.gpefrance.tenders.analysis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import eu.gpefrance.tenders.analysis.AnswerReader.Proposal;
import eu.gpefrance.tenders.analysis.ExtractionClient.ModelAnswer;
import eu.gpefrance.tenders.analysis.ExtractionPrompt.SourceDocument;
import eu.gpefrance.tenders.document.Document;
import eu.gpefrance.tenders.document.DocumentNotFoundException;
import eu.gpefrance.tenders.document.DocumentPage;
import eu.gpefrance.tenders.document.DocumentPageRepository;
import eu.gpefrance.tenders.document.DocumentState;
import eu.gpefrance.tenders.document.Dossier;
import eu.gpefrance.tenders.document.DossierReadEvent;
import eu.gpefrance.tenders.document.DossierRepository;
import eu.gpefrance.tenders.document.DossierStatus;
import eu.gpefrance.tenders.document.PieceType;
import eu.gpefrance.tenders.tender.Tender;
import eu.gpefrance.tenders.tender.TenderRepository;

/**
 * Runs the AI analysis of a dossier in the background: the consultation rules
 * and the notice are sent to the model, its answers are read, checked against
 * the documents and stored. A failure is recorded and shown, never silent.
 */
@Service
public class AnalysisService {

	/**
	 * Pieces sent to the model, in this order. CCAP and CCTP come in a later
	 * version; spreadsheets and plans are not read.
	 */
	static final List<PieceType> ANALYSED_PIECES = List.of(PieceType.RC, PieceType.NOTICE, PieceType.AE,
			PieceType.OTHER);

	private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

	private static final List<AnalysisStatus> UNFINISHED = List.of(AnalysisStatus.PENDING, AnalysisStatus.PROCESSING);

	private final AnalysisRepository analyses;

	private final DossierRepository dossiers;

	private final DocumentPageRepository pages;

	private final TenderRepository tenders;

	private final ExtractionClient client;

	private final AnalysisProperties properties;

	private final TransactionTemplate transaction;

	private final TaskExecutor taskExecutor;

	private final Clock clock;

	public AnalysisService(AnalysisRepository analyses, DossierRepository dossiers, DocumentPageRepository pages,
			TenderRepository tenders, ExtractionClient client, AnalysisProperties properties,
			TransactionTemplate transaction, @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor,
			Clock clock) {
		this.analyses = analyses;
		this.dossiers = dossiers;
		this.pages = pages;
		this.tenders = tenders;
		this.client = client;
		this.properties = properties;
		this.transaction = transaction;
		this.taskExecutor = taskExecutor;
		this.clock = clock;
	}

	public boolean isConfigured() {
		return client.model() != null;
	}

	/**
	 * Starts the analysis of a dossier. The same content already analysed
	 * with the same model and instructions is not analysed again unless
	 * forced (rule RM9), and a tender has only one analysis running at a time.
	 * @return the id of the analysis to follow: the new one or the existing one
	 */
	public Long request(Long dossierId, boolean force) {
		if (!isConfigured()) {
			throw new AnalysisRejectedException(
					"Aucun fournisseur d'IA n'est configuré : l'analyse ne peut pas être lancée.");
		}
		Requested requested = transaction.execute(status -> {
			Dossier dossier = dossiers.findById(dossierId).orElseThrow(() -> new DocumentNotFoundException(dossierId));
			if (dossier.getStatus() != DossierStatus.DONE) {
				throw new AnalysisRejectedException(
						"La lecture du dossier n'est pas terminée : l'analyse sera possible ensuite.");
			}
			Optional<Analysis> running = analyses.findFirstByTenderIdAndStatusInOrderByIdDesc(dossier.getTenderId(),
					UNFINISHED);
			if (running.isPresent()) {
				return new Requested(running.get().getId(), false);
			}
			List<Document> selected = select(dossier);
			if (selected.isEmpty()) {
				throw new AnalysisRejectedException("Aucun document analysable : il faut un règlement de consultation, "
						+ "un avis ou un acte d'engagement lu. Vérifiez le type des pièces.");
			}
			String fingerprint = fingerprint(selected);
			if (!force) {
				Optional<Analysis> done = analyses.findFirstByTenderIdAndInputFingerprintAndStatusOrderByIdDesc(
						dossier.getTenderId(), fingerprint, AnalysisStatus.DONE);
				if (done.isPresent()) {
					return new Requested(done.get().getId(), false);
				}
			}
			Analysis analysis = analyses.save(new Analysis(dossier.getTenderId(), dossierId, client.model(),
					ExtractionPrompt.VERSION, selected.stream().map(Document::getId).toList(), fingerprint,
					clock.instant()));
			return new Requested(analysis.getId(), true);
		});
		if (requested.started()) {
			startInBackground(requested.id());
		}
		return requested.id();
	}

	/**
	 * The analysis starts by itself once the documents are read, only for the
	 * tenders kept by the filter, to control the cost (rule RM8).
	 */
	@EventListener
	public void onDossierRead(DossierReadEvent event) {
		if (!isConfigured()) {
			return;
		}
		Boolean retained = transaction.execute(status -> dossiers.findById(event.dossierId())
			.flatMap(dossier -> tenders.findById(dossier.getTenderId()))
			.map(Tender::getRetained)
			.orElse(null));
		if (!Boolean.TRUE.equals(retained)) {
			return;
		}
		try {
			request(event.dossierId(), false);
		}
		catch (AnalysisRejectedException ex) {
			log.info("Dossier {} not analysed: {}", event.dossierId(), ex.getBody().getDetail());
		}
	}

	/**
	 * Analyses interrupted by a restart are run again.
	 */
	@EventListener(ApplicationReadyEvent.class)
	public void resumeUnfinished() {
		for (Analysis analysis : analyses.findByStatusIn(UNFINISHED)) {
			if (isConfigured()) {
				startInBackground(analysis.getId());
			}
			else {
				transaction.executeWithoutResult(status -> analyses.findById(analysis.getId())
					.orElseThrow()
					.fail("Aucun fournisseur d'IA n'est configuré", clock.instant()));
			}
		}
	}

	private void startInBackground(Long analysisId) {
		taskExecutor.execute(() -> run(analysisId));
	}

	public void run(Long analysisId) {
		Prepared prepared = transaction.execute(status -> {
			Analysis analysis = analyses.findById(analysisId).orElseThrow();
			analysis.startProcessing();
			Dossier dossier = dossiers.findById(analysis.getDossierId()).orElseThrow();
			List<SourceDocument> sources = sorted(dossier.getDocuments().stream()
				.filter(document -> analysis.getDocumentIds().contains(document.getId()))
				.toList()).stream().map(this::load).toList();
			Instant noticeDeadline = tenders.findById(analysis.getTenderId()).map(Tender::getDeadline).orElse(null);
			return new Prepared(sources, noticeDeadline);
		});
		ModelAnswer answer = null;
		try {
			answer = client.complete(ExtractionPrompt.instructions(), ExtractionPrompt.documents(prepared.sources()));
			Map<Long, Map<Integer, String>> texts = prepared.sources()
				.stream()
				.collect(Collectors.toMap(SourceDocument::id, SourceDocument::pages));
			List<Proposal> proposals = AnswerChecker.check(AnswerReader.read(answer.text(), texts.keySet()), texts,
					prepared.noticeDeadline());
			ModelAnswer usage = answer;
			transaction.executeWithoutResult(status -> {
				Analysis analysis = analyses.findById(analysisId).orElseThrow();
				proposals.forEach(proposal -> analysis.addField(proposal.key(), proposal.value(), proposal.quote(),
						proposal.documentId(), proposal.page(), proposal.state()));
				analysis.finish(usage.inputTokens(), usage.outputTokens(), clock.instant());
			});
		}
		catch (RuntimeException ex) {
			log.error("Analysis {} failed", analysisId, ex);
			ModelAnswer usage = answer;
			transaction.executeWithoutResult(status -> {
				Analysis analysis = analyses.findById(analysisId).orElseThrow();
				if (usage != null) {
					analysis.recordUsage(usage.inputTokens(), usage.outputTokens());
				}
				analysis.fail("Analyse impossible : " + ex.getMessage(), clock.instant());
			});
		}
	}

	/**
	 * Readable pieces worth sending, as long as they fit: a document that does
	 * not fit is left out whole, never cut.
	 */
	private List<Document> select(Dossier dossier) {
		List<Document> candidates = sorted(dossier.getDocuments()
			.stream()
			.filter(document -> document.getState() == DocumentState.READ
					|| document.getState() == DocumentState.SCANNED)
			.filter(document -> ANALYSED_PIECES.contains(document.getPieceType()))
			.toList());
		List<Document> selected = new ArrayList<>();
		int length = 0;
		for (Document document : candidates) {
			int documentLength = load(document).length();
			if (documentLength > 0 && length + documentLength <= properties.maxCharacters()) {
				selected.add(document);
				length += documentLength;
			}
		}
		return selected;
	}

	private static List<Document> sorted(List<Document> documents) {
		return documents.stream()
			.sorted(Comparator.comparingInt((Document document) -> ANALYSED_PIECES.indexOf(document.getPieceType()))
				.thenComparing(Document::getFileName))
			.toList();
	}

	private SourceDocument load(Document document) {
		Map<Integer, String> texts = new LinkedHashMap<>();
		for (DocumentPage page : pages.findByDocumentIdOrderByPageNumber(document.getId())) {
			if (!page.getText().isBlank()) {
				texts.put(page.getPageNumber(), page.getText());
			}
		}
		return new SourceDocument(document.getId(), document.getFileName(), document.getPieceType(), texts);
	}

	/**
	 * Same files, same piece types, same model and instructions: same
	 * analysis.
	 */
	private String fingerprint(List<Document> documents) {
		String input = documents.stream()
			.map(document -> document.getSha256() + ":" + document.getPieceType())
			.sorted()
			.collect(Collectors.joining("\n", ExtractionPrompt.VERSION + "\n" + client.model() + "\n", ""));
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private record Requested(Long id, boolean started) {
	}

	private record Prepared(List<SourceDocument> sources, Instant noticeDeadline) {
	}

}
