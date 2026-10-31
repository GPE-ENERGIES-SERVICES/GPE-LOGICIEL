package eu.gpefrance.tenders.document;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Decides, for each document of a consultation file, whether it goes to the
 * AI and why, with plain rules valid for any file: a duplicate goes once, a
 * choice of the user prevails, price schedules go to the analysis of the
 * prices, plans and drawings never go, the essential pieces go first, then
 * the useful ones (diagnostics, reports...), and generic documentation or
 * pages only read by OCR go if there is room left. The same rules decide
 * which scanned pages are worth reading by OCR.
 */
public final class DocumentTriage {

	/** What a document is used for. */
	public enum Use {

		/** Sent for the summary sheet. */
		SUMMARY,
		/** Sent for the summary sheet if there is room left. */
		IF_ROOM,
		/** Sent for the analysis of the prices. */
		FINANCIAL,
		/** Never sent. */
		EXCLUDED

	}

	/**
	 * @param rank order of sending, the lowest first
	 * @param reason why, for the user
	 */
	public record Decision(Use use, int rank, String reason) {

		public boolean sentForSummary() {
			return use == Use.SUMMARY || use == Use.IF_ROOM;
		}

	}

	/**
	 * What was read from a document.
	 * @param characters length of its text
	 * @param pagesWithText pages holding some text
	 * @param ocrOnly whether all of its text comes from OCR
	 */
	public record TextStats(long characters, int pagesWithText, boolean ocrOnly) {

		public static final TextStats NONE = new TextStats(0, 0, false);

	}

	private static final List<PieceType> ESSENTIAL = List.of(PieceType.RC, PieceType.NOTICE, PieceType.AE,
			PieceType.CCAP, PieceType.CCTP);

	private static final List<PieceType> FINANCIAL = List.of(PieceType.DPGF, PieceType.BPU, PieceType.DQE);

	/** Names of documents useful to decide, checked before the names of drawings. */
	private static final Pattern USEFUL = Pattern.compile("\\b(diagnostics?|diag|rapports?|etudes?|amiante|plomb|"
			+ "hap|sondages?|geotechniques?|portance|expertises?|notes? (de|d)|programme|memoire|cadre de reponse|"
			+ "questionnaire|planning|calendrier|pgc|ppsps|plan general de coordination|plan de prevention|"
			+ "plan d assurance|reglement|conditions|charte|attestation de visite|bordereau des pieces)\\b");

	/** Names of plans and drawings. */
	private static final Pattern DRAWING = Pattern.compile("\\b(plans?|det|details?|coupes?|facades?|elevations?|"
			+ "carnets?|calepinages?|calpinages?|masse|situation|niveaux?|rdc|rez de chaussee|sous sol|etages?|"
			+ "photos?|reportage|minute graphique|perspectives?|vues?|releves?|esquisses?|axonometries?|"
			+ "implantation|nomenclature|cartouche|dwg|dxf)\\b");

	/** Names of generic documentation, not written for this consultation. */
	private static final Pattern GENERIC = Pattern.compile("\\b(manuels?|guides?|catalogues?|brochures?|"
			+ "fiches? techniques?|documentation|plaquettes?|notice de pose|avis techniques?|dtu)\\b");

	/** Below this many characters per page with text, a document is a drawing, not a text. */
	static final int MIN_CHARACTERS_PER_PAGE = 300;

	private DocumentTriage() {
	}

	/**
	 * The decision for each document of a dossier, in the order of the
	 * documents: the first of identical files is the one kept.
	 * @param stats what was read from each document, by id
	 */
	public static Map<Long, Decision> decide(List<Document> documents, Map<Long, TextStats> stats) {
		Map<Long, Decision> decisions = new LinkedHashMap<>();
		Map<String, Document> first = new HashMap<>();
		for (Document document : documents.stream()
			.sorted(Comparator.comparing(Document::getId))
			.toList()) {
			Document original = first.putIfAbsent(document.getSha256(), document);
			decisions.put(document.getId(), original != null
					? new Decision(Use.EXCLUDED, 99, "Doublon de « " + shortName(original.getFileName()) + " »")
					: decide(document, stats.getOrDefault(document.getId(), TextStats.NONE)));
		}
		return decisions;
	}

	/**
	 * The decisions for the documents of a dossier, from what was read of
	 * them, without loading their text.
	 */
	public static Map<Long, Decision> decide(List<Document> documents, DocumentPageRepository pages) {
		Map<Long, TextStats> stats = new HashMap<>();
		if (!documents.isEmpty()) {
			for (DocumentPageRepository.TextCount count : pages
				.countText(documents.stream().map(Document::getId).toList())) {
				long withText = count.getPagesWithText() == null ? 0 : count.getPagesWithText();
				long byOcr = count.getPagesByOcr() == null ? 0 : count.getPagesByOcr();
				stats.put(count.getDocumentId(), new TextStats(count.getCharacters() == null ? 0 : count.getCharacters(),
						(int) withText, withText > 0 && byOcr == withText));
			}
		}
		return decide(documents, stats);
	}

	static Decision decide(Document document, TextStats stats) {
		PieceType type = document.getPieceType();
		String name = normalize(shortName(document.getFileName()));
		boolean readable = (document.getState() == DocumentState.READ || document.getState() == DocumentState.SCANNED)
				&& stats.characters() > 0;
		if (document.getAiChoice() == AiChoice.EXCLUDE) {
			return new Decision(Use.EXCLUDED, 99, "Exclu à la main");
		}
		if (FINANCIAL.contains(type)) {
			return readable ? new Decision(Use.FINANCIAL, 0, "Pièce de prix : analyse financière")
					: new Decision(Use.EXCLUDED, 99, unreadable(document));
		}
		if (!readable) {
			return new Decision(Use.EXCLUDED, 99, unreadable(document));
		}
		if (document.getAiChoice() == AiChoice.INCLUDE) {
			return new Decision(Use.SUMMARY, 0, "Ajouté à la main");
		}
		if (type == PieceType.PLAN) {
			return new Decision(Use.EXCLUDED, 99, "Plan : non envoyé");
		}
		if (SpreadsheetExtractor.handles(document.getFileName())) {
			return new Decision(Use.EXCLUDED, 99,
					"Tableur hors pièces de prix : classez-le DPGF, BPU ou DQE pour l'analyse financière");
		}
		if (ESSENTIAL.contains(type)) {
			return new Decision(Use.SUMMARY, 1 + ESSENTIAL.indexOf(type), "Pièce essentielle");
		}
		if (USEFUL.matcher(name).find()) {
			return new Decision(Use.SUMMARY, 10, "Document utile à la décision (d'après son nom)");
		}
		if (DRAWING.matcher(name).find()) {
			return new Decision(Use.EXCLUDED, 99, "Plan ou dessin (d'après son nom) : non envoyé");
		}
		if (stats.pagesWithText() > 0 && stats.characters() / stats.pagesWithText() < MIN_CHARACTERS_PER_PAGE) {
			return new Decision(Use.EXCLUDED, 99, "Très peu de texte par page : dessin ou schéma probable");
		}
		if (GENERIC.matcher(name).find()) {
			return new Decision(Use.IF_ROOM, 30, "Documentation générale : envoyée s'il reste de la place");
		}
		if (stats.ocrOnly()) {
			return new Decision(Use.IF_ROOM, 40, "Lu seulement par OCR : envoyé s'il reste de la place");
		}
		return new Decision(Use.IF_ROOM, 20, "Autre pièce : envoyée s'il reste de la place");
	}

	/**
	 * Whether the scanned pages of a document are worth reading by OCR: only
	 * for the pieces that may be sent, never for plans and drawings.
	 */
	public static boolean worthReading(Document document) {
		if (document.getAiChoice() == AiChoice.EXCLUDE || document.getPieceType() == PieceType.PLAN) {
			return false;
		}
		if (document.getAiChoice() == AiChoice.INCLUDE || ESSENTIAL.contains(document.getPieceType())
				|| FINANCIAL.contains(document.getPieceType())) {
			return true;
		}
		String name = normalize(shortName(document.getFileName()));
		return USEFUL.matcher(name).find() && !DRAWING.matcher(name).find();
	}

	/**
	 * Whether a document is a plan or a drawing by its type or its name, the
	 * rules of {@link #decide} keeping it from the AI whatever its text: its
	 * text is not worth reading, unless a user asks for it.
	 */
	public static boolean drawingByName(Document document) {
		if (document.getAiChoice() == AiChoice.INCLUDE) {
			return false;
		}
		if (document.getPieceType() == PieceType.PLAN) {
			return true;
		}
		if (ESSENTIAL.contains(document.getPieceType()) || FINANCIAL.contains(document.getPieceType())) {
			return false;
		}
		String name = normalize(shortName(document.getFileName()));
		return !USEFUL.matcher(name).find() && DRAWING.matcher(name).find();
	}

	/**
	 * Whether an image not {@link #worthReading worth reading} by its name may
	 * still be a page worth it: its name says nothing (IMG_2034, scan001), it
	 * is neither a plan, a photo nor a drawing, by its name nor by the folders
	 * of the archive holding it (« Dossier photo », « Plans »). Its letters
	 * decide then.
	 */
	public static boolean worthProbing(Document document) {
		if (document.getAiChoice() == AiChoice.EXCLUDE || document.getPieceType() == PieceType.PLAN) {
			return false;
		}
		String path = document.getFileName().replace('\\', '/');
		int dot = path.lastIndexOf('.');
		return !DRAWING.matcher(normalize(dot > path.lastIndexOf('/') ? path.substring(0, dot) : path)).find();
	}

	private static String unreadable(Document document) {
		return document.getStateDetail() != null ? document.getStateDetail() : "Aucun texte lu : non envoyé";
	}

	private static String shortName(String fileName) {
		String name = fileName.replace('\\', '/');
		name = name.substring(name.lastIndexOf('/') + 1);
		int dot = name.lastIndexOf('.');
		return dot > 0 ? name.substring(0, dot) : name;
	}

	static String normalize(String text) {
		return Normalizer.normalize(text, Normalizer.Form.NFD)
			.replaceAll("\\p{M}", "")
			.toLowerCase(Locale.ROOT)
			.replaceAll("[^a-z0-9]+", " ")
			.strip();
	}

}
