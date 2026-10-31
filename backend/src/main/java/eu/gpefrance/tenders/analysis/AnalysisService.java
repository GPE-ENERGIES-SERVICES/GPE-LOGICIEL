package eu.gpefrance.tenders.analysis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import eu.gpefrance.tenders.analysis.AnalysisGate.Status;
import eu.gpefrance.tenders.analysis.ExtractionPrompt.SourceDocument;
import eu.gpefrance.tenders.document.Document;
import eu.gpefrance.tenders.document.DocumentNotFoundException;
import eu.gpefrance.tenders.document.DocumentPage;
import eu.gpefrance.tenders.document.DocumentPageRepository;
import eu.gpefrance.tenders.document.DocumentState;
import eu.gpefrance.tenders.document.Dossier;
import eu.gpefrance.tenders.document.DossierReadEvent;
import eu.gpefrance.tenders.document.DossierRepository;
import eu.gpefrance.tenders.document.DocumentTriage;
import eu.gpefrance.tenders.document.DocumentTriage.Decision;
import eu.gpefrance.tenders.document.DossierStatus;
import eu.gpefrance.tenders.document.PageMethod;
import eu.gpefrance.tenders.document.PieceType;
import eu.gpefrance.tenders.tender.Tender;
import eu.gpefrance.tenders.tender.TenderRepository;

/**
 * Asks for the AI analysis of a dossier: chooses the documents, checks that
 * the analysis is allowed and puts it in the queue. Nothing is sent here:
 * {@link AnalysisWorker} makes the calls, in the background. Every source of
 * a dossier (upload, retrieval) goes the same way: only the documents and
 * their extracted text count.
 */
@Service
public class AnalysisService {

	/**
	 * Pieces sent to the model for the summary, in this order, as long as they
	 * fit. Price schedules (DPGF, BPU, DQE) go to the analysis of the prices,
	 * plans and spreadsheets are not sent.
	 */
	static final List<PieceType> ANALYSED_PIECES = List.of(PieceType.RC, PieceType.NOTICE, PieceType.AE,
			PieceType.CCAP, PieceType.CCTP, PieceType.OTHER);

	/** Pieces sent for the analysis of the prices, in this order. */
	static final List<PieceType> FINANCIAL_PIECES = List.of(PieceType.DPGF, PieceType.BPU, PieceType.DQE);

	static final List<AnalysisStatus> UNFINISHED = List.of(AnalysisStatus.PENDING, AnalysisStatus.PROCESSING,
			AnalysisStatus.RETRY_WAITING);

	private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

	/** Put before the text of a page read by OCR, for the model. */
	static final String OCR_PAGE = "[Page lue par reconnaissance de caractères (OCR) : le texte peut contenir des "
			+ "erreurs, surtout sur les chiffres, virgules et montants ; toute valeur de cette page est à vérifier]";

	private final AnalysisRepository analyses;

	private final DossierRepository dossiers;

	private final DocumentPageRepository pages;

	private final TenderRepository tenders;

	private final AnalysisGate gate;

	private final AnalysisWorker worker;

	private final AnalysisProperties properties;

	/** Wait before an analysis started by itself is sent: a user may still cancel it. */
	private final Duration autoStartDelay;

	private final TransactionTemplate transaction;

	private final Clock clock;

	public AnalysisService(AnalysisRepository analyses, DossierRepository dossiers, DocumentPageRepository pages,
			TenderRepository tenders, AnalysisGate gate, AnalysisWorker worker, AnalysisProperties properties,
			TransactionTemplate transaction, Clock clock,
			@Value("${analysis.auto-start-delay:2m}") Duration autoStartDelay) {
		this.autoStartDelay = autoStartDelay;
		this.analyses = analyses;
		this.dossiers = dossiers;
		this.pages = pages;
		this.tenders = tenders;
		this.gate = gate;
		this.worker = worker;
		this.properties = properties;
		this.transaction = transaction;
		this.clock = clock;
	}

	/**
	 * Queues the analysis of a dossier. The same content already analysed
	 * with the same model and instructions is not analysed again unless
	 * forced (rule RM9), and a tender has only one analysis unfinished at a
	 * time.
	 * @param requestedBy the user, null for an automatic request
	 * @return the id of the analysis to follow: the new one or the existing one
	 */
	public Long request(Long dossierId, boolean force, String requestedBy) {
		return request(AnalysisKind.DCE_SUMMARY, dossierId, force, requestedBy);
	}

	/**
	 * Queues the analysis of the price schedules (DPGF, BPU, DQE) of a
	 * dossier: only on the request of a user, never by itself.
	 * @see #request(Long, boolean, String)
	 */
	public Long requestFinancial(Long dossierId, boolean force, String requestedBy) {
		return request(AnalysisKind.FINANCIAL, dossierId, force, requestedBy);
	}

	private Long request(AnalysisKind kind, Long dossierId, boolean force, String requestedBy) {
		Status status = gate.status();
		if (!status.ready()) {
			throw new AnalysisRejectedException(status.message());
		}
		ExtractionClient client = status.client();
		Requested requested;
		try {
			requested = transaction.execute(tx -> queue(kind, dossierId, force, requestedBy, client));
		}
		catch (DataIntegrityViolationException ex) {
			// Another request of the same tender was queued at the same time.
			requested = transaction.execute(tx -> dossiers.findById(dossierId)
				.flatMap(dossier -> analyses.findFirstByTenderIdAndKindAndStatusInOrderByIdDesc(dossier.getTenderId(),
						kind, UNFINISHED))
				.map(running -> new Requested(running.getId(), false))
				.orElseThrow(() -> ex));
		}
		if (requested.queued()) {
			worker.wakeUp();
		}
		return requested.id();
	}

	private Requested queue(AnalysisKind kind, Long dossierId, boolean force, String requestedBy,
			ExtractionClient client) {
		Dossier dossier = dossiers.findById(dossierId).orElseThrow(() -> new DocumentNotFoundException(dossierId));
		if (dossier.getStatus() != DossierStatus.DONE) {
			throw new AnalysisRejectedException(
					"La lecture du dossier n'est pas terminée : l'analyse sera possible ensuite.");
		}
		Optional<Analysis> running = analyses.findFirstByTenderIdAndKindAndStatusInOrderByIdDesc(dossier.getTenderId(),
				kind, UNFINISHED);
		if (running.isPresent()) {
			return new Requested(running.get().getId(), false);
		}
		boolean financial = kind == AnalysisKind.FINANCIAL;
		Selection selection = financial ? selectFinancial(dossier) : select(dossier);
		if (selection.documents().isEmpty()) {
			throw new AnalysisRejectedException(financial
					? "Aucune pièce financière lisible : il faut un DPGF, un BPU ou un DQE (tableur ou PDF) dont le "
							+ "contenu a été lu. Vérifiez le type des pièces."
					: "Aucun document analysable : il faut au moins un règlement de consultation, un avis, un acte "
							+ "d'engagement, un CCAP, un CCTP ou une autre pièce dont le texte a été lu. Vérifiez le "
							+ "type des pièces.");
		}
		String version = financial ? FinancialPrompt.VERSION : ExtractionPrompt.VERSION;
		String fingerprint = fingerprint(selection.documents(), client.provider() + "/" + client.model(), version);
		if (!force) {
			Optional<Analysis> done = analyses.findFirstByTenderIdAndKindAndInputFingerprintAndStatusOrderByIdDesc(
					dossier.getTenderId(), kind, fingerprint, AnalysisStatus.DONE);
			if (done.isPresent()) {
				return new Requested(done.get().getId(), false);
			}
		}
		if (!gate.fitsBudget(selection.characters())) {
			throw new AnalysisRejectedException("Le plafond mensuel de dépense ne permet pas cette analyse "
					+ "(estimation locale) : elle n'est pas lancée.");
		}
		Analysis created = new Analysis(kind, dossier.getTenderId(), dossierId, client.provider(), client.model(),
				version, selection.documents().stream().map(Document::getId).toList(), fingerprint, requestedBy,
				clock.instant());
		if (requestedBy == null) {
			created.delayUntil(clock.instant().plus(autoStartDelay));
		}
		Analysis analysis = analyses.saveAndFlush(created);
		log.info("Analysis {} ({}) of dossier {} queued ({} documents, {} characters)", analysis.getId(), kind,
				dossierId, selection.documents().size(), selection.characters());
		return new Requested(analysis.getId(), true);
	}

	/**
	 * The analysis starts by itself once the documents are read, only for the
	 * tenders kept by the filter, to control the cost (rule RM8), and only
	 * when the analysis is allowed: reading a dossier never calls an AI by
	 * itself otherwise.
	 */
	@EventListener
	public void onDossierRead(DossierReadEvent event) {
		if (!gate.status().ready()) {
			return;
		}
		Boolean retained = transaction.execute(status -> dossiers.findById(event.dossierId())
			// A user may have stopped it while the dossier was read.
			.filter(Dossier::isAutoAnalysis)
			.flatMap(dossier -> tenders.findById(dossier.getTenderId()))
			.map(Tender::getRetained)
			.orElse(null));
		if (!Boolean.TRUE.equals(retained)) {
			return;
		}
		try {
			request(event.dossierId(), false, null);
		}
		catch (AnalysisRejectedException ex) {
			log.info("Dossier {} not analysed: {}", event.dossierId(), ex.getBody().getDetail());
		}
	}

	/**
	 * Sends at once an analysis waiting to be sent (started by itself, or
	 * waiting for a new attempt), instead of waiting for its time.
	 * @return the tender of the analysis
	 */
	public Long sendNow(Long analysisId) {
		Long tenderId = transaction.execute(status -> {
			Analysis analysis = analyses.findById(analysisId)
				.orElseThrow(() -> new AnalysisNotFoundException(analysisId));
			analysis.sendNow(clock.instant());
			return analysis.getTenderId();
		});
		worker.wakeUp();
		return tenderId;
	}

	/**
	 * Cancels an analysis not sent yet.
	 * @return the tender of the analysis
	 */
	public Long cancel(Long analysisId, String user) {
		return transaction.execute(status -> {
			Analysis analysis = analyses.findById(analysisId)
				.orElseThrow(() -> new AnalysisNotFoundException(analysisId));
			analysis.cancel(user, clock.instant());
			log.info("Analysis {} cancelled by a user", analysisId);
			return analysis.getTenderId();
		});
	}

	/**
	 * Readable pieces worth sending, as long as they fit: a document that does
	 * not fit is left out whole, never cut.
	 */
	private Selection select(Dossier dossier) {
		// The rules of DocumentTriage: duplicates once, choices of the users, no plan nor
		// drawing, most useful first.
		Map<Long, Decision> decisions = DocumentTriage.decide(dossier.getDocuments(), pages);
		List<Document> candidates = dossier.getDocuments()
			.stream()
			.filter(document -> decisions.get(document.getId()).sentForSummary())
			.sorted(Comparator.comparingInt((Document document) -> decisions.get(document.getId()).rank())
				.thenComparing(Document::getFileName))
			.toList();
		return fit(candidates);
	}

	/**
	 * Readable price schedules, spreadsheets or PDF, as long as they fit:
	 * they never compete with the pieces of the summary.
	 */
	private Selection selectFinancial(Dossier dossier) {
		Map<Long, Decision> decisions = DocumentTriage.decide(dossier.getDocuments(), pages);
		return fit(financialPieces(dossier.getDocuments()
			.stream()
			.filter(document -> decisions.get(document.getId()).use() == DocumentTriage.Use.FINANCIAL)
			.toList()));
	}

	static List<Document> financialPieces(List<Document> documents) {
		return documents.stream()
			.filter(document -> document.getState() == DocumentState.READ
					|| document.getState() == DocumentState.SCANNED)
			.filter(document -> FINANCIAL_PIECES.contains(document.getPieceType()))
			.sorted(Comparator.comparingInt((Document document) -> FINANCIAL_PIECES.indexOf(document.getPieceType()))
				.thenComparing(Document::getFileName))
			.toList();
	}

	private Selection fit(List<Document> candidates) {
		List<Document> selected = new ArrayList<>();
		int length = 0;
		for (Document document : candidates) {
			if (selected.size() >= properties.maxDocumentsPerTender()) {
				break;
			}
			int documentLength = load(document, pages).length();
			if (documentLength > 0 && length + documentLength <= properties.maxCharacters()) {
				selected.add(document);
				length += documentLength;
			}
		}
		return new Selection(selected, length);
	}

	static List<Document> sorted(List<Document> documents) {
		return documents.stream()
			.sorted(Comparator.comparingInt((Document document) -> ANALYSED_PIECES.indexOf(document.getPieceType()))
				.thenComparing(Document::getFileName))
			.toList();
	}

	static SourceDocument load(Document document, DocumentPageRepository pages) {
		Map<Integer, String> texts = new LinkedHashMap<>();
		for (DocumentPage page : pages.findByDocumentIdOrderByPageNumber(document.getId())) {
			if (!page.getText().isBlank()) {
				// The model is told which text was read by OCR, so that it never gives its values as sure.
				texts.put(page.getPageNumber(),
						page.getMethod() == PageMethod.OCR ? OCR_PAGE + "\n" + page.getText() : page.getText());
			}
		}
		return new SourceDocument(document.getId(), document.getFileName(), document.getPieceType(), texts);
	}

	/**
	 * Same files, same piece types, same model and same grid and
	 * instructions: same analysis. A new version of the grid or of the
	 * instructions, or another model, gives another fingerprint.
	 */
	static String fingerprint(List<Document> documents, String model) {
		return fingerprint(documents, model, ExtractionPrompt.VERSION);
	}

	static String fingerprint(List<Document> documents, String model, String version) {
		String input = documents.stream()
			.map(document -> document.getSha256() + ":" + document.getPieceType())
			.sorted()
			.collect(Collectors.joining("\n", version + "\n" + model + "\n", ""));
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private record Requested(Long id, boolean queued) {
	}

	private record Selection(List<Document> documents, int characters) {
	}

}
