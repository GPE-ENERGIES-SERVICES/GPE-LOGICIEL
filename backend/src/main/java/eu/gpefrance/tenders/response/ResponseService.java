package eu.gpefrance.tenders.response;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import eu.gpefrance.tenders.analysis.Analysis;
import eu.gpefrance.tenders.analysis.AnalysisKind;
import eu.gpefrance.tenders.analysis.AnalysisRepository;
import eu.gpefrance.tenders.analysis.AnalysisStatus;
import eu.gpefrance.tenders.analysis.ExtractedField;
import eu.gpefrance.tenders.analysis.FieldDecision;
import eu.gpefrance.tenders.company.CompanyDirectory;
import eu.gpefrance.tenders.company.CompanyDirectory.DocumentFile;
import eu.gpefrance.tenders.company.CompanyDocument;
import eu.gpefrance.tenders.company.Validity;
import eu.gpefrance.tenders.document.FileStorage;
import eu.gpefrance.tenders.equipment.EquipmentService;
import eu.gpefrance.tenders.document.FileTypeValidator;
import eu.gpefrance.tenders.estimate.DpgfFillService;
import eu.gpefrance.tenders.estimate.EstimateResponse;
import eu.gpefrance.tenders.estimate.EstimateService;
import eu.gpefrance.tenders.estimate.EstimateStatus;
import eu.gpefrance.tenders.memoir.MemoirHtml;
import eu.gpefrance.tenders.memoir.MemoirService;
import eu.gpefrance.tenders.memoir.MemoirWord;
import eu.gpefrance.tenders.response.ResponsePiece.Category;
import eu.gpefrance.tenders.tender.Tender;
import eu.gpefrance.tenders.tender.TenderNotFoundException;
import eu.gpefrance.tenders.tender.TenderRepository;

/**
 * The answer to a tender, the last step of its way: the pieces the buyer asks,
 * what is joined to each, what remains to do. The analysis and the company
 * propose, a user decides: nothing a user chose is changed by a new reading.
 * <p>
 * The export of an estimate holds the costs and coefficients of GPE: it is
 * never joined by itself to what goes to a buyer.
 */
@Service
public class ResponseService {

	private static final String APPLICATION_FIELD = "application_documents";

	private static final String OFFER_FIELD = "offer_documents";

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final ResponsePieceRepository pieces;

	private final TenderRepository tenders;

	private final AnalysisRepository analyses;

	private final CompanyDirectory company;

	private final EstimateService estimates;

	private final FileStorage storage;

	private final JdbcTemplate jdbc;

	private final Clock clock;

	private final EquipmentService equipment;

	private final MemoirService memoirs;

	private final DpgfFillService schedules;

	private final PieceWriter writer;

	public ResponseService(ResponsePieceRepository pieces, TenderRepository tenders, AnalysisRepository analyses,
			CompanyDirectory company, EstimateService estimates, FileStorage storage, JdbcTemplate jdbc, Clock clock,
			EquipmentService equipment, MemoirService memoirs, DpgfFillService schedules, PieceWriter writer) {
		this.memoirs = memoirs;
		this.schedules = schedules;
		this.writer = writer;
		this.equipment = equipment;
		this.pieces = pieces;
		this.tenders = tenders;
		this.analyses = analyses;
		this.company = company;
		this.estimates = estimates;
		this.storage = storage;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	public enum PieceState {

		/** Nothing usable is joined yet. */
		MISSING,
		/** The official document joined has expired. */
		EXPIRED,
		/** Joined, a user has not checked it. */
		TO_CHECK,
		READY,
		NOT_NEEDED

	}

	/**
	 * @param detail what to do next, or what is joined
	 * @param attachedName the file that goes in the archive, or null
	 */
	public record PieceResponse(Long id, Category category, String label, boolean fromAnalysis, PieceState state,
			String detail, Long companyDocumentId, String fileName, Long sizeBytes, String attachedName,
			String checkedBy, Instant checkedAt, boolean fillable, String section, Fill fill, String content,
			String contentOrigin, List<String> missing) {
	}

	/** A step before the answer, as it stands. */
	public record Step(String title, boolean ready, String detail, String anchor) {
	}

	public record DocumentChoice(Long id, String category, String name, java.time.LocalDate expiresOn, Validity validity,
			boolean hasFile) {
	}

	public record ResponseView(List<Step> steps, boolean analysisDone, List<PieceResponse> pieces,
			List<DocumentChoice> documents) {
	}

	/** A dossier in progress, for the list of the answers. */
	public record ResponseSummary(Long tenderId, String title, String buyer, Instant deadline, int total, int ready) {
	}

	@Transactional(readOnly = true)
	public ResponseView view(Long tenderId) {
		checkTender(tenderId);
		Analysis analysis = latestAnalysis(tenderId);
		List<EstimateResponse.Summary> tenderEstimates = estimates.ofTender(tenderId);
		List<String> memoirs = memoirStates(tenderId);
		List<DocumentFile> files = company.documentFiles();
		Map<Long, DocumentFile> byId = files.stream().collect(Collectors.toMap(DocumentFile::id, Function.identity()));
		int sheets = equipment.filesOfTender(tenderId).size();
		List<PieceResponse> responses = pieces.findByTenderIdOrderByCategoryAscIdAsc(tenderId)
			.stream()
			.map(piece -> response(piece, byId, tenderEstimates, memoirs, sheets))
			.toList();
		List<DocumentChoice> documents = files.stream()
			.map(file -> new DocumentChoice(file.id(), file.category().name(), file.name(), file.expiresOn(),
					file.validity(), file.path() != null))
			.toList();
		return new ResponseView(steps(analysis, tenderEstimates, memoirs), analysis != null, responses, documents);
	}

	@Transactional(readOnly = true)
	public ResponseView viewOfPiece(Long pieceId) {
		return view(find(pieceId).getTenderId());
	}

	/**
	 * Adds the pieces read by the latest analysis that are not listed yet.
	 * A value rejected by a user is left out, a corrected one is taken as
	 * corrected. What is already listed stays exactly as it is.
	 */
	@Transactional
	public ResponseView prepare(Long tenderId, String user) {
		checkTender(tenderId);
		Analysis analysis = latestAnalysis(tenderId);
		if (analysis == null) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Aucune fiche IA terminée : lancez-la, ou ajoutez les pièces à la main.");
		}
		List<DocumentFile> files = company.documentFiles();
		Instant now = clock.instant();
		Set<String> added = new HashSet<>();
		for (ExtractedField field : analysis.getFields()) {
			Category category = categoryOf(field.getFieldKey());
			String label = category == null ? null : labelOf(field);
			if (label == null || !added.add(ResponsePiece.keyOf(label))
					|| pieces.existsByTenderIdAndLabelKey(tenderId, ResponsePiece.keyOf(label))) {
				continue;
			}
			pieces.save(new ResponsePiece(tenderId, category, label, true, suggested(label, files), user, now));
		}
		pieces.flush();
		return view(tenderId);
	}

	@Transactional
	public ResponseView add(Long tenderId, Category category, String label, String user) {
		checkTender(tenderId);
		String clean = label.strip();
		if (pieces.existsByTenderIdAndLabelKey(tenderId, ResponsePiece.keyOf(clean))) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette pièce est déjà dans la liste.");
		}
		pieces.saveAndFlush(new ResponsePiece(tenderId, category, clean, false, suggested(clean, company.documentFiles()),
				user, clock.instant()));
		return view(tenderId);
	}

	/**
	 * @param companyDocumentId the official document joined, null for none
	 */
	@Transactional
	public ResponseView change(Long pieceId, String label, boolean notNeeded, Long companyDocumentId, String user) {
		ResponsePiece piece = find(pieceId);
		if (companyDocumentId != null && company.documentFiles().stream().noneMatch(file -> file.id().equals(companyDocumentId))) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document de l'entreprise introuvable.");
		}
		piece.change(label.strip(), notNeeded, companyDocumentId, user, clock.instant());
		return view(piece.getTenderId());
	}

	/** Says a user has looked at what is joined; there must be something to look at. */
	@Transactional
	public ResponseView check(Long pieceId, boolean checked, String user) {
		ResponsePiece piece = find(pieceId);
		if (checked && attached(piece, documentsById()) == null) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Joignez d'abord un fichier à cette pièce.");
		}
		piece.check(checked, user, clock.instant());
		return view(piece.getTenderId());
	}

	/** A piece asked by the buyer stays listed: it is marked not needed instead. */
	@Transactional
	public ResponseView delete(Long pieceId) {
		ResponsePiece piece = find(pieceId);
		if (piece.isFromAnalysis()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Cette pièce vient de l'analyse du DCE : marquez-la « non concernée » plutôt que de la supprimer.");
		}
		Long tenderId = piece.getTenderId();
		String path = piece.getStoragePath();
		pieces.delete(piece);
		pieces.flush();
		deleteFile(path);
		return view(tenderId);
	}

	/** The file of the user prevails over the official document proposed. */
	@Transactional
	public ResponseView upload(Long pieceId, MultipartFile file, String user) {
		ResponsePiece piece = find(pieceId);
		if (file == null || file.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fichier vide.");
		}
		Path temporary = null;
		try {
			temporary = Files.createTempFile("response-", ".upload");
			try (InputStream content = file.getInputStream()) {
				Files.copy(content, temporary, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			}
			FileTypeValidator.Validated validated = FileTypeValidator.validate(temporary, file.getOriginalFilename(), 200,
					200_000_000L);
			Path stored;
			try (InputStream copy = Files.newInputStream(temporary)) {
				stored = storage.store(copy, storage.resolve("responses/" + piece.getTenderId() + "/" + pieceId),
						validated.fileName());
			}
			String previous = piece.getStoragePath();
			piece.attach(validated.fileName(), storage.relativize(stored), validated.size(), user, clock.instant());
			deleteFile(previous);
		}
		catch (FileTypeValidator.FileRejectedException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fichier refusé : " + ex.getMessage() + ".");
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		finally {
			if (temporary != null) {
				temporary.toFile().delete();
			}
		}
		return view(piece.getTenderId());
	}

	/**
	 * How a piece is filled, besides an uploaded file: a form of the file
	 * (Administratif), the file of another step, the sheets of the equipment
	 * chosen, a text written here by hand or by the AI; null for an official
	 * document of the company, only joined.
	 */
	public enum Fill {
		FORM, MEMOIR, PRICES, EQUIPMENT, WRITE
	}

	/** The official documents of the company: joined, never written. */
	private static final Set<CompanyDocument.Category> OFFICIAL = Set.of(CompanyDocument.Category.KBIS,
			CompanyDocument.Category.VIGILANCE, CompanyDocument.Category.TAX, CompanyDocument.Category.SOCIAL,
			CompanyDocument.Category.INSURANCE, CompanyDocument.Category.CERTIFICATE);

	static Fill fillOf(String label) {
		if (FormService.fillable(label)) {
			return Fill.FORM;
		}
		return switch (PieceMatcher.kind(label)) {
			case MEMOIR -> Fill.MEMOIR;
			case PRICES -> Fill.PRICES;
			case FORM, ENGAGEMENT -> null;
			case OTHER -> {
				if (PieceMatcher.technical(label)) {
					yield Fill.EQUIPMENT;
				}
				CompanyDocument.Category category = PieceMatcher.category(label);
				yield PieceMatcher.drawing(label) || category != null && OFFICIAL.contains(category) ? null : Fill.WRITE;
			}
		};
	}

	/** A text written by hand: saved, and joined as a Word file in the style of GPE. */
	@Transactional
	public ResponseView write(Long pieceId, String html, String user) {
		ResponsePiece piece = find(pieceId);
		String cleaned = MemoirHtml.clean(html);
		if (cleaned == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Texte vide : écrivez la pièce, ou retirez-la.");
		}
		piece.write(cleaned, "USER", piece.getContentMissing(), user, clock.instant());
		joinWord(piece, cleaned, user);
		return view(piece.getTenderId());
	}

	/** A draft by the AI from what the application knows, joined as a Word file; a user reads and checks it. */
	@Transactional
	public ResponseView draft(Long pieceId, String user) {
		ResponsePiece piece = find(pieceId);
		if (fillOf(piece.getLabel()) != Fill.WRITE) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Cette pièce ne se rédige pas : c'est un formulaire, un document officiel ou le fichier d'une autre étape.");
		}
		Tender tender = tenders.findById(piece.getTenderId()).orElseThrow(() -> new TenderNotFoundException(piece.getTenderId()));
		PieceWriter.Written written = writer.write(piece, tender, user);
		String cleaned = MemoirHtml.clean(written.html());
		if (cleaned == null) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "L'IA n'a rien rédigé d'utilisable : réessayez ou rédigez-la à la main.");
		}
		piece.write(cleaned, "AI", written.missing().isEmpty() ? null : String.join("\n", written.missing()), user,
				clock.instant());
		joinWord(piece, cleaned, user);
		return view(piece.getTenderId());
	}

	/** Joins the file of another step as it is now: the memoir in Word, the price schedule filled. */
	@Transactional
	public ResponseView fromStep(Long pieceId, String user) {
		ResponsePiece piece = find(pieceId);
		Fill fill = fillOf(piece.getLabel());
		if (fill == Fill.MEMOIR) {
			Long memoirId = jdbc.queryForList("SELECT id FROM memoir WHERE tender_id = ? ORDER BY updated_at DESC LIMIT 1",
					Long.class, piece.getTenderId()).stream().findFirst()
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
						"Aucun mémoire pour cet appel d'offres : créez-le dans l'onglet « Mémoire »."));
			MemoirService.Export export = memoirs.exportWord(memoirId);
			join(piece, export.fileName(), export.content(), user);
		}
		else if (fill == Fill.PRICES) {
			DpgfFillService.Filled filled = filledSchedule(piece);
			join(piece, filled.fileName(), filled.content(), user);
		}
		else {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cette pièce ne vient pas d'une autre étape.");
		}
		return view(piece.getTenderId());
	}

	/** The schedule of the buyer filled by the estimate: the validated one first, the latest otherwise. */
	private DpgfFillService.Filled filledSchedule(ResponsePiece piece) {
		EstimateResponse.Summary estimate = estimates.ofTender(piece.getTenderId())
			.stream()
			.min(Comparator.comparing((EstimateResponse.Summary summary) -> summary.status() != EstimateStatus.VALIDATED)
				.thenComparing(EstimateResponse.Summary::updatedAt, Comparator.reverseOrder()))
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
					"Aucun chiffrage pour cet appel d'offres : faites-le dans l'onglet « Chiffrage »."));
		List<Map<String, Object>> sources = jdbc.queryForList("""
				SELECT DISTINCT d.id, d.file_name FROM estimate_item i JOIN document d ON d.id = i.source_document_id
				WHERE i.estimate_id = ? ORDER BY d.id""", estimate.id());
		if (sources.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Le chiffrage ne vient d'aucun bordereau du DCE : importez le DPGF (ou le BPU) dans le chiffrage.");
		}
		// The schedule named like the piece (DPGF, BPU, DQE), the first otherwise.
		String label = PieceMatcher.plain(piece.getLabel());
		Map<String, Object> source = sources.stream()
			.filter(row -> java.util.stream.Stream.of("dpgf", "bpu", "dqe")
				.anyMatch(word -> label.contains(word)
						&& PieceMatcher.plain(String.valueOf(row.get("file_name"))).contains(word)))
			.findFirst()
			.orElse(sources.getFirst());
		return schedules.fill(estimate.id(), ((Number) source.get("id")).longValue());
	}

	private void joinWord(ResponsePiece piece, String html, String user) {
		Tender tender = tenders.findById(piece.getTenderId()).orElseThrow(() -> new TenderNotFoundException(piece.getTenderId()));
		byte[] word = MemoirWord.piece(piece.getLabel(), tender.getTitle(), tender.getBuyer(), tender.getLocation(), html);
		join(piece, piece.getLabel().replaceAll("[\\\\/:*?\"<>|\\x00-\\x1F]", "_").strip() + ".docx", word, user);
	}

	/** A file made by the application, joined to the piece in place of the former one. */
	private void join(ResponsePiece piece, String fileName, byte[] content, String user) {
		String name = fileName.length() <= 200 ? fileName : fileName.substring(0, 190) + fileName.substring(fileName.lastIndexOf('.'));
		Path stored = storage.store(new java.io.ByteArrayInputStream(content),
				storage.resolve("responses/" + piece.getTenderId() + "/" + piece.getId()), name);
		String previous = piece.getStoragePath();
		String relative = storage.relativize(stored);
		piece.attach(name, relative, content.length, user, clock.instant());
		if (previous != null && !previous.equals(relative)) {
			deleteFile(previous);
		}
	}

	@Transactional
	public ResponseView removeFile(Long pieceId, String user) {
		ResponsePiece piece = find(pieceId);
		String path = piece.getStoragePath();
		piece.detach(user, clock.instant());
		deleteFile(path);
		return view(piece.getTenderId());
	}

	public record PieceFile(String name, Path path) {
	}

	/** The file uploaded for a piece. */
	@Transactional(readOnly = true)
	public PieceFile file(Long pieceId) {
		ResponsePiece piece = find(pieceId);
		if (piece.getStoragePath() == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Aucun fichier déposé pour cette pièce.");
		}
		return new PieceFile(piece.getFileName(), storage.resolve(piece.getStoragePath()));
	}

	/**
	 * What goes in the archive, each file under the part of the answer it
	 * belongs to; refused rather than incomplete when a file is gone from the
	 * disk.
	 */
	/**
	 * @param single one envelope, as some consultations ask: the folders of GPE
	 * without the candidature and the offer around them
	 */
	@Transactional(readOnly = true)
	public List<PieceFile> archiveEntries(Long tenderId, boolean single) {
		checkTender(tenderId);
		Map<Long, DocumentFile> documents = documentsById();
		List<PieceFile> entries = new ArrayList<>();
		Set<String> names = new HashSet<>();
		for (ResponsePiece piece : pieces.findByTenderIdOrderByCategoryAscIdAsc(tenderId)) {
			PieceFile attached = piece.isNotNeeded() ? null : attached(piece, documents);
			if (attached == null) {
				continue;
			}
			if (!Files.isRegularFile(attached.path())) {
				throw new ResponseStatusException(HttpStatus.CONFLICT,
						"Le fichier de « " + piece.getLabel() + " » est introuvable : déposez-le à nouveau.");
			}
			String envelope = single ? "" : piece.getCategory() == Category.APPLICATION ? "1-Candidature/" : "2-Offre/";
			String folder = envelope + PieceMatcher.section(piece.getCategory(), piece.getLabel()) + "/";
			entries.add(new PieceFile(unique(folder + attached.name().replaceAll("[\\\\/:*?\"<>|]", "_"), names),
					attached.path()));
		}
		// The sheets of the equipment chosen for the tender go with the offer, as GPE files them.
		for (EquipmentService.SheetFile sheet : equipment.filesOfTender(tenderId)) {
			if (Files.isRegularFile(sheet.path())) {
				entries.add(new PieceFile(unique((single ? "" : "2-Offre/") + "06-Fiches techniques/"
						+ sheet.fileName().replaceAll("[\\\\/:*?\"<>|]", "_"), names), sheet.path()));
			}
		}
		if (entries.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Aucun fichier à exporter pour l'instant.");
		}
		return entries;
	}

	public static void writeArchive(List<PieceFile> entries, OutputStream out) throws IOException {
		try (ZipOutputStream zip = new ZipOutputStream(out)) {
			for (PieceFile entry : entries) {
				zip.putNextEntry(new ZipEntry(entry.name()));
				Files.copy(entry.path(), zip);
				zip.closeEntry();
			}
		}
	}

	/** The tenders whose answer has begun, the nearest deadline first. */
	public List<ResponseSummary> list() {
		return jdbc.query("""
				SELECT t.id, t.title, t.buyer, t.deadline, count(*) FILTER (WHERE NOT p.not_needed) AS total,
				       count(*) FILTER (WHERE NOT p.not_needed AND p.checked_at IS NOT NULL) AS ready
				FROM response_piece p JOIN tender t ON t.id = p.tender_id
				GROUP BY t.id, t.title, t.buyer, t.deadline
				ORDER BY t.deadline NULLS LAST, t.id DESC
				""", (row, n) -> new ResponseSummary(row.getLong("id"), row.getString("title"), row.getString("buyer"),
				row.getTimestamp("deadline") == null ? null : row.getTimestamp("deadline").toInstant(),
				row.getInt("total"), row.getInt("ready")));
	}

	private static String unique(String name, Set<String> names) {
		if (names.add(name)) {
			return name;
		}
		int dot = name.lastIndexOf('.');
		String base = dot > 0 ? name.substring(0, dot) : name;
		String extension = dot > 0 ? name.substring(dot) : "";
		for (int number = 2;; number++) {
			String candidate = base + " (" + number + ")" + extension;
			if (names.add(candidate)) {
				return candidate;
			}
		}
	}

	/**
	 * @param sheets how many sheets of the equipment chosen go in the archive by themselves
	 */
	private PieceResponse response(ResponsePiece piece, Map<Long, DocumentFile> documents,
			List<EstimateResponse.Summary> tenderEstimates, List<String> memoirs, int sheets) {
		PieceFile attached = attached(piece, documents);
		Fill fill = fillOf(piece.getLabel());
		DocumentFile document = piece.getStoragePath() == null ? documents.get(piece.getCompanyDocumentId()) : null;
		PieceState state;
		String detail;
		if (piece.isNotNeeded()) {
			state = PieceState.NOT_NEEDED;
			detail = "Non concernée : ne part pas dans le dossier.";
		}
		else if (attached == null && fill == Fill.EQUIPMENT && sheets > 0) {
			state = piece.getCheckedAt() != null ? PieceState.READY : PieceState.TO_CHECK;
			detail = sheets + " fiche(s) du matériel retenu (choix techniques) partent d'elles-mêmes dans 06-Fiches techniques.";
		}
		else if (attached == null) {
			state = PieceState.MISSING;
			detail = document != null ? "« " + document.name() + " » n'a aucun fichier dans Entreprise."
					: whatToDo(piece.getLabel(), tenderEstimates, memoirs);
		}
		else if (document != null && document.validity() == Validity.EXPIRED) {
			state = PieceState.EXPIRED;
			detail = "« " + document.name() + " » a expiré le " + DATE.format(document.expiresOn()) + " : à renouveler.";
		}
		else {
			state = piece.getCheckedAt() != null ? PieceState.READY : PieceState.TO_CHECK;
			detail = document != null ? "Proposé depuis Entreprise : « " + document.name() + " »"
					+ (document.expiresOn() == null ? "." : ", valable jusqu'au " + DATE.format(document.expiresOn()) + ".")
					: piece.getContent() != null ? ("AI".equals(piece.getContentOrigin()) ? "Rédigée par l'IA, à relire" : "Rédigée dans l'application")
							+ " : « " + piece.getFileName() + " »." : "Fichier déposé.";
		}
		return new PieceResponse(piece.getId(), piece.getCategory(), piece.getLabel(), piece.isFromAnalysis(), state, detail,
				piece.getCompanyDocumentId(), piece.getFileName(), piece.getSizeBytes(),
				attached == null ? null : attached.name(), piece.getCheckedBy(), piece.getCheckedAt(),
				fill == Fill.FORM, PieceMatcher.section(piece.getCategory(), piece.getLabel()), fill, piece.getContent(),
				piece.getContentOrigin(),
				piece.getContentMissing() == null ? List.of() : List.of(piece.getContentMissing().split("\n")));
	}

	private static String whatToDo(String label, List<EstimateResponse.Summary> tenderEstimates, List<String> memoirs) {
		return switch (PieceMatcher.kind(label)) {
			case FORM -> "Onglet « Administratif » : remplissez le formulaire du DCE, à la main ou avec l'IA ; vous pouvez aussi déposer le vôtre.";
			case ENGAGEMENT -> "Onglet « Administratif » : remplissez le formulaire du DCE, à la main ou avec l'IA ; vous pouvez aussi déposer le vôtre.";
			case PRICES -> pricesToDo(tenderEstimates);
			case MEMOIR -> memoirs.isEmpty() ? "Aucun mémoire : créez-le dans l'onglet « Mémoire », puis « Prendre le mémoire »."
					: "Mémoire : " + String.join(", ", memoirs) + ". « Prendre le mémoire » joint son Word au format GPE ; ou déposez votre version finale.";
			case OTHER -> fillOf(label) == Fill.WRITE ? "« Rédiger » : à la main ou avec l'IA ; ou déposez un fichier, ou choisissez un document de l'entreprise."
					: "À déposer ici, ou à choisir dans les documents de l'entreprise.";
		};
	}

	private static String pricesToDo(List<EstimateResponse.Summary> tenderEstimates) {
		if (tenderEstimates.isEmpty()) {
			return "Aucun chiffrage : faites-le dans l'onglet « Chiffrage » à partir du bordereau du DCE, puis « Prendre le "
					+ "bordereau rempli ».";
		}
		boolean validated = tenderEstimates.stream().anyMatch(estimate -> estimate.status() == EstimateStatus.VALIDATED);
		return (validated ? "Chiffrage validé" : "Chiffrage encore en brouillon")
				+ " : « Prendre le bordereau rempli » joint le bordereau de l'acheteur rempli de ses prix. L'export du chiffrage, avec vos "
				+ "coûts, ne part jamais dans le dossier.";
	}

	private List<Step> steps(Analysis analysis, List<EstimateResponse.Summary> tenderEstimates, List<String> memoirs) {
		boolean validated = tenderEstimates.stream().anyMatch(estimate -> estimate.status() == EstimateStatus.VALIDATED);
		boolean memoirDone = memoirs.contains("terminé");
		return List.of(
				new Step("Fiche IA", analysis != null,
						analysis == null ? "Pas encore de fiche terminée : la liste des pièces en dépend." : "Fiche terminée.",
						"fiche"),
				new Step("Chiffrage", validated, validated ? "Chiffrage validé."
						: tenderEstimates.isEmpty() ? "Pas encore de chiffrage." : "Chiffrage encore en brouillon.", "chiffrage"),
				new Step("Mémoire", memoirDone, memoirDone ? "Mémoire terminé."
						: memoirs.isEmpty() ? "Pas encore de mémoire." : "Mémoire en cours.", "memoire"));
	}

	private List<String> memoirStates(Long tenderId) {
		return jdbc.queryForList("SELECT status FROM memoir WHERE tender_id = ? ORDER BY id", String.class, tenderId)
			.stream()
			.map(status -> status.equals("DONE") ? "terminé" : "en cours")
			.toList();
	}

	/** What goes in the archive for a piece: the file of the user first, otherwise the official document. */
	private PieceFile attached(ResponsePiece piece, Map<Long, DocumentFile> documents) {
		if (piece.getStoragePath() != null) {
			return new PieceFile(piece.getFileName(), storage.resolve(piece.getStoragePath()));
		}
		DocumentFile document = documents.get(piece.getCompanyDocumentId());
		return document == null || document.path() == null ? null : new PieceFile(document.fileName(), document.path());
	}

	/** The official document of the category with a file, the one that ends last. */
	private static Long suggested(String label, List<DocumentFile> files) {
		var category = PieceMatcher.category(label);
		if (category == null) {
			return null;
		}
		return files.stream()
			.filter(file -> file.category() == category && file.path() != null && file.validity() != Validity.EXPIRED)
			.max(Comparator.comparing(DocumentFile::expiresOn, Comparator.nullsFirst(Comparator.naturalOrder())))
			.map(DocumentFile::id)
			.orElse(null);
	}

	private static Category categoryOf(String fieldKey) {
		return switch (fieldKey) {
			case APPLICATION_FIELD -> Category.APPLICATION;
			case OFFER_FIELD -> Category.OFFER;
			default -> null;
		};
	}

	private static String labelOf(ExtractedField field) {
		if (field.getDecision() == FieldDecision.REJECTED) {
			return null;
		}
		String value = field.getDecision() == FieldDecision.CORRECTED ? field.getCorrectedValue() : field.getValue();
		if (value == null || value.isBlank()) {
			return null;
		}
		String label = value.strip();
		return label.length() <= 500 ? label : label.substring(0, 500);
	}

	private Analysis latestAnalysis(Long tenderId) {
		return analyses.findByTenderIdAndKindOrderByIdDesc(tenderId, AnalysisKind.DCE_SUMMARY)
			.stream()
			.filter(analysis -> analysis.getStatus() == AnalysisStatus.DONE)
			.findFirst()
			.orElse(null);
	}

	private Map<Long, DocumentFile> documentsById() {
		return company.documentFiles().stream().collect(Collectors.toMap(DocumentFile::id, Function.identity()));
	}

	private void deleteFile(String relativePath) {
		if (relativePath != null) {
			// shortcut: removed before the commit; a failed transaction loses the former file, the piece asks for it again.
			storage.resolve(relativePath).toFile().delete();
		}
	}

	private void checkTender(Long tenderId) {
		if (!tenders.existsById(tenderId)) {
			throw new TenderNotFoundException(tenderId);
		}
	}

	private ResponsePiece find(Long pieceId) {
		return pieces.findById(pieceId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pièce introuvable."));
	}

}
