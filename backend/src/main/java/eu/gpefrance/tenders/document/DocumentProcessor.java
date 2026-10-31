package eu.gpefrance.tenders.document;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import eu.gpefrance.tenders.document.ImageConverter.ConvertedImage;
import eu.gpefrance.tenders.document.OcrEngine.OcrPage;
import eu.gpefrance.tenders.document.OcrEngine.OcrUnavailableException;
import eu.gpefrance.tenders.document.OfficeConverter.ConversionException;
import jakarta.annotation.PreDestroy;

/**
 * Reads the documents of a dossier in the background: text of each PDF page,
 * pages without text read by OCR (except plans), Word documents converted to
 * PDF first, images that may be pieces of the consultation turned into a PDF
 * then read by OCR, plain text and HTML files by sections, spreadsheets as
 * rows and cells with their coordinates. Other
 * formats (plans, photos, other archives) are kept but marked as not
 * analysed, with the reason: never read as an empty text. A document that
 * fails never stops the others. The AI only uses the text stored here; the
 * checks of the price schedules read their spreadsheets again with the same
 * {@link SpreadsheetExtractor}, to compute on the cells themselves.
 */
@Service
public class DocumentProcessor {

	private static final Logger log = LoggerFactory.getLogger(DocumentProcessor.class);

	private static final Set<String> WORD = Set.of("doc", "docx", "odt", "rtf");

	private static final Set<String> TEXT = Set.of("txt");

	private static final Set<String> HTML = Set.of("html", "htm");

	private static final Set<String> DRAWING = Set.of("dwg", "dxf", "svg");

	/** Why an image is kept aside: nothing tells it is a piece of the consultation. */
	static final String IMAGE_ASIDE = "Image conservée, non lue : plan, photo ou dessin d'après son nom ou son "
			+ "dossier. « Forcer l'envoi » la fait lire";

	/** Why a plan or a drawing is not read. */
	static final String DRAWING_ASIDE = "Plan ou dessin (d'après son nom) : texte non lu, jamais envoyé à l'IA. "
			+ "« Forcer l'envoi » le fait lire";

	/** Images whose name says nothing looked at for letters in one reading of a dossier, at most. */
	static final int MAX_PROBED_IMAGES = 100;

	private static final Set<String> ARCHIVE = Set.of("7z", "rar", "tar", "gz", "tgz");

	private final DossierRepository dossiers;

	private final DocumentRepository documents;

	private final DocumentPageRepository pages;

	private final FileStorage storage;

	private final PdfTextExtractor pdfExtractor;

	private final OfficeConverter officeConverter;

	private final TextFileExtractor textExtractor;

	private final SpreadsheetExtractor spreadsheetExtractor;

	private final OcrEngine ocr;

	private final ImageConverter images;

	/** Tesseract missing is said once, not for every page. */
	private final AtomicBoolean ocrMissingLogged = new AtomicBoolean();

	private final TransactionTemplate transaction;

	private final TaskExecutor taskExecutor;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	/** Documents of a dossier read at the same time. */
	private final ExecutorService readers;

	/** Scanned pages of a document read by OCR at the same time. */
	private final ExecutorService ocrPages;

	public DocumentProcessor(DossierRepository dossiers, DocumentRepository documents, DocumentPageRepository pages,
			FileStorage storage, PdfTextExtractor pdfExtractor, OfficeConverter officeConverter,
			TextFileExtractor textExtractor, SpreadsheetExtractor spreadsheetExtractor, OcrEngine ocr,
			ImageConverter images, TransactionTemplate transaction,
			@Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor, ApplicationEventPublisher events, Clock clock,
			@Value("${documents.read-threads:0}") int readThreads) {
		this.dossiers = dossiers;
		this.documents = documents;
		this.pages = pages;
		this.storage = storage;
		this.pdfExtractor = pdfExtractor;
		this.officeConverter = officeConverter;
		this.textExtractor = textExtractor;
		this.spreadsheetExtractor = spreadsheetExtractor;
		this.ocr = ocr;
		this.images = images;
		this.transaction = transaction;
		this.taskExecutor = taskExecutor;
		this.events = events;
		this.clock = clock;
		// As many as the processors, up to 4: LibreOffice and Tesseract each take one, and the memory stays low.
		int threads = readThreads > 0 ? readThreads : Math.clamp(Runtime.getRuntime().availableProcessors(), 1, 4);
		this.readers = Executors.newFixedThreadPool(threads,
				Thread.ofPlatform().name("document-reader-", 1).daemon().factory());
		this.ocrPages = Executors.newFixedThreadPool(threads, Thread.ofPlatform().name("ocr-page-", 1).daemon().factory());
	}

	@PreDestroy
	void stop() {
		readers.shutdownNow();
		ocrPages.shutdownNow();
	}

	public void startInBackground(Long dossierId) {
		taskExecutor.execute(() -> process(dossierId));
	}

	/**
	 * Reads again documents of a dossier already read (one added to the AI by
	 * a user, its scanned pages now worth OCR), without announcing it: no
	 * analysis, which is paid, starts by itself for that.
	 */
	public void rereadInBackground(Long dossierId) {
		taskExecutor.execute(() -> process(dossierId, false));
	}

	/**
	 * Dossiers left unfinished by a restart are read again.
	 */
	@EventListener(ApplicationReadyEvent.class)
	public void resumeUnfinished() {
		dossiers.findByStatusIn(List.of(DossierStatus.PENDING, DossierStatus.PROCESSING))
			.forEach(dossier -> startInBackground(dossier.getId()));
	}

	/**
	 * Reads every document still pending in the dossier, then announces it so
	 * that the analysis can start.
	 */
	public void process(Long dossierId) {
		process(dossierId, true);
	}

	/**
	 * @param announce whether the analysis may start once read
	 */
	private void process(Long dossierId, boolean announce) {
		// The first of identical files are read first; the others then take their reading.
		List<List<Long>> waves = transaction.execute(status -> {
			Dossier dossier = dossiers.findById(dossierId).orElseThrow();
			dossier.startProcessing();
			List<Long> first = new ArrayList<>();
			List<Long> again = new ArrayList<>();
			Set<String> seen = new HashSet<>();
			dossier.getDocuments()
				.stream()
				.filter(document -> document.getState() == DocumentState.PENDING)
				.sorted(Comparator.comparing(Document::getId))
				.forEach(document -> (seen.add(document.getSha256()) ? first : again).add(document.getId()));
			return List.of(first, again);
		});
		AtomicInteger probed = new AtomicInteger();
		try {
			for (List<Long> wave : waves == null ? List.<List<Long>>of() : waves) {
				read(wave, probed);
			}
			transaction.executeWithoutResult(
					status -> dossiers.findById(dossierId).orElseThrow().finish(clock.instant()));
		}
		catch (RuntimeException ex) {
			log.error("Dossier {} could not be processed", dossierId, ex);
			transaction.executeWithoutResult(status -> dossiers.findById(dossierId)
				.orElseThrow()
				.fail("Erreur pendant la lecture du dossier : " + ex.getMessage(), clock.instant()));
			return;
		}
		if (announce) {
			events.publishEvent(new DossierReadEvent(dossierId));
		}
	}

	/**
	 * Reads documents at the same time, several at once; a failure is raised
	 * once all are done.
	 */
	private void read(List<Long> documentIds, AtomicInteger probed) {
		List<Future<?>> reading = new ArrayList<>();
		for (Long documentId : documentIds) {
			reading.add(readers.submit(() -> processDocument(documentId, probed)));
		}
		RuntimeException failure = null;
		for (Future<?> document : reading) {
			try {
				document.get();
			}
			catch (ExecutionException ex) {
				failure = failure != null ? failure
						: ex.getCause() instanceof RuntimeException cause ? cause : new IllegalStateException(ex);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				reading.forEach(future -> future.cancel(true));
				throw new IllegalStateException("Lecture interrompue", ex);
			}
		}
		if (failure != null) {
			throw failure;
		}
	}

	/**
	 * @param probed images whose name says nothing looked at so far
	 */
	private void processDocument(Long documentId, AtomicInteger probed) {
		long start = System.nanoTime();
		Document document = documents.findById(documentId).orElseThrow();
		String extension = extension(document.getFileName());
		Path file = storage.resolve(document.getStoragePath());
		try {
			if ((extension.equals("pdf") || WORD.contains(extension)) && DocumentTriage.drawingByName(document)) {
				// Its text never goes to the AI: not worth the reading, often the longest of the file.
				String pdfPath = extension.equals("pdf") ? storage.relativize(file) : null;
				update(documentId, doc -> doc.setAside(DRAWING_ASIDE, pdfPath));
				return;
			}
			if (reuseReading(document, extension, file)) {
				return;
			}
			if (extension.equals("pdf")) {
				saveText(documentId, file, null);
			}
			else if (WORD.contains(extension)) {
				Path outputDirectory = file.resolveSibling(".pdf").resolve(String.valueOf(documentId));
				saveText(documentId, officeConverter.toPdf(file, outputDirectory), null);
			}
			else if (TEXT.contains(extension)) {
				saveSections(documentId, textExtractor.extractText(file), PageMethod.TEXT);
			}
			else if (HTML.contains(extension)) {
				saveSections(documentId, textExtractor.extractHtml(file), PageMethod.HTML);
			}
			else if (SpreadsheetExtractor.FORMATS.contains(extension)) {
				saveSheets(documentId, spreadsheetExtractor.read(file, extension));
			}
			else if (ImageConverter.FORMATS.contains(extension)) {
				readImage(documentId, file, probed);
			}
			else {
				update(documentId, doc -> doc.markNotAnalyzed(notAnalyzedReason(extension)));
			}
		}
		catch (InvalidPasswordException ex) {
			update(documentId, doc -> doc.markFailed("PDF protégé par un mot de passe : non analysé"));
		}
		catch (TextFileExtractor.TooLargeException ex) {
			update(documentId, doc -> doc.markNotAnalyzed("Fichier texte trop volumineux (plus de 20 Mo) : non analysé"));
		}
		catch (SpreadsheetExtractor.TooLargeException ex) {
			update(documentId, doc -> doc.markNotAnalyzed("Tableur trop volumineux : non analysé"));
		}
		catch (SpreadsheetExtractor.UnreadableSpreadsheetException ex) {
			update(documentId, doc -> doc.markFailed(ex.getMessage() + " : non analysé"));
		}
		catch (ImageConverter.TooLargeException | ImageConverter.UnsupportedImageException
				| ImageConverter.NoTextException ex) {
			update(documentId, doc -> doc.markNotAnalyzed(ex.getMessage()));
		}
		catch (ImageConverter.UnreadableImageException ex) {
			update(documentId, doc -> doc.markFailed(ex.getMessage()));
		}
		catch (IOException ex) {
			log.warn("Document {} could not be read", documentId, ex);
			update(documentId, doc -> doc.markFailed("Fichier illisible : non analysé"));
		}
		catch (ConversionException ex) {
			log.warn("Document {} could not be converted", documentId, ex);
			update(documentId, doc -> doc.markFailed(ex.getMessage() + " : non analysé"));
		}
		finally {
			log.debug("Document {} (.{}) read in {} ms", documentId, extension, (System.nanoTime() - start) / 1_000_000);
		}
	}

	/**
	 * A file already read in full (deposited again, or twice in the same
	 * archive) takes the reading done: same content, same text, its pages
	 * copied rather than read again.
	 * @return whether the reading was taken
	 */
	private boolean reuseReading(Document document, String extension, Path file) {
		Document read = documents
			.findFirstBySha256AndStateAndIdNotOrderByIdDesc(document.getSha256(), DocumentState.READ, document.getId())
			.filter(other -> extension(other.getFileName()).equals(extension))
			.orElse(null);
		if (read == null) {
			return false;
		}
		String pdfPath;
		if (read.getPdfPath() == null) {
			pdfPath = null;
		}
		else if (extension.equals("pdf")) {
			pdfPath = storage.relativize(file);
		}
		else {
			// A Word document or an image: its PDF is copied beside the file, the older version may go.
			Path source = storage.resolve(read.getPdfPath());
			Path target = file.resolveSibling(".pdf")
				.resolve(String.valueOf(document.getId()))
				.resolve(source.getFileName().toString());
			try {
				Files.createDirectories(target.getParent());
				Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
			}
			catch (IOException ex) {
				return false;
			}
			pdfPath = storage.relativize(target);
		}
		transaction.executeWithoutResult(status -> {
			pages.deleteByDocumentId(document.getId());
			for (DocumentPage page : pages.findByDocumentIdOrderByPageNumber(read.getId())) {
				pages.save(page.copyFor(document.getId()));
			}
			documents.findById(document.getId()).orElseThrow().markReadLike(read, pdfPath);
		});
		return true;
	}

	/**
	 * Images: the pieces of the consultation are read, turned into a PDF then
	 * read by OCR like a scanned page; an image whose name says nothing is read
	 * if Tesseract finds letters in it, so that no page photographed is missed;
	 * photos, plans and drawings by their name stay aside, unless a user asks
	 * for them.
	 * @param probed images whose name says nothing looked at so far
	 */
	private void readImage(Long documentId, Path file, AtomicInteger probed) throws IOException {
		Document document = documents.findById(documentId).orElseThrow();
		boolean probe = !DocumentTriage.worthReading(document);
		if (probe && !DocumentTriage.worthProbing(document)) {
			update(documentId, doc -> doc.markNotAnalyzed(IMAGE_ASIDE));
			return;
		}
		if (probe && probed.incrementAndGet() > MAX_PROBED_IMAGES) {
			update(documentId, doc -> doc.markNotAnalyzed("Image conservée, non lue : plus de " + MAX_PROBED_IMAGES
					+ " images sans nom parlant dans ce dépôt. « Forcer l'envoi » la fait lire"));
			return;
		}
		Path outputDirectory = file.resolveSibling(".pdf").resolve(String.valueOf(documentId));
		ConvertedImage converted = images.toPdf(file, outputDirectory, probe);
		saveText(documentId, converted.pdf(), converted.notes());
	}

	/**
	 * @param imageNotes what was done to an image turned into this PDF, null
	 * for a PDF or a Word document
	 */
	private void saveText(Long documentId, Path pdf, List<String> imageNotes) throws IOException {
		List<String> texts = pdfExtractor.extractPages(pdf);
		String pdfPath = storage.relativize(pdf);
		Recognition recognition = recognise(documentId, pdf, texts, imageNotes != null);
		Map<Integer, OcrPage> recognised = recognition.pages();
		transaction.executeWithoutResult(status -> {
			pages.deleteByDocumentId(documentId);
			int pagesWithoutText = 0;
			for (int i = 0; i < texts.size(); i++) {
				OcrPage ocr = recognised.get(i + 1);
				if (ocr != null) {
					pages.save(new DocumentPage(documentId, i + 1, ocr.text(), OcrEngine.toJson(ocr.words())));
					continue;
				}
				if (texts.get(i).isBlank() && !recognition.blank().contains(i + 1)) {
					pagesWithoutText++;
				}
				pages.save(new DocumentPage(documentId, i + 1, texts.get(i), PageMethod.NATIVE));
			}
			Document document = documents.findById(documentId).orElseThrow();
			document.markRead(texts.size(), pagesWithoutText, pdfPath);
			if (!recognised.isEmpty()) {
				document.noteRecognised(recognised.size(),
						recognised.values().stream().mapToDouble(OcrPage::confidence).average().orElse(0));
			}
			if (imageNotes != null) {
				if (recognised.isEmpty()) {
					document.markNotAnalyzed(recognition.unavailable()
							? "Image : reconnaissance de caractères (OCR) indisponible sur le serveur, non lue"
							: "Aucun texte reconnu dans l'image (photo, schéma ou image trop floue) : non analysée");
				}
				if (!imageNotes.isEmpty()) {
					document.noteDetail(String.join(", ", imageNotes));
				}
			}
		});
	}

	/**
	 * @param pages the pages read, by number, each with some text
	 * @param unavailable whether Tesseract could not be run
	 * @param blank the blank pages, nothing to read on them
	 */
	private record Recognition(Map<Integer, OcrPage> pages, boolean unavailable, Set<Integer> blank) {
	}

	/**
	 * Reads the pages without text with OCR, up to the limit, only for the
	 * pieces that may be sent to the AI ({@link DocumentTriage#worthReading}):
	 * never plans nor drawings, whose text is graphics, not sentences. When Tesseract is not
	 * there, the pages stay unread, as before.
	 * @param image whether the PDF is an image turned into one: already chosen
	 * to be read
	 */
	private Recognition recognise(Long documentId, Path pdf, List<String> texts, boolean image) {
		Map<Integer, OcrPage> recognised = new LinkedHashMap<>();
		if (texts.stream().noneMatch(String::isBlank)
				|| !image && !documents.findById(documentId).map(DocumentTriage::worthReading).orElse(false)) {
			return new Recognition(recognised, false, Set.of());
		}
		// The pages are read at the same time, each by its own Tesseract.
		Map<Integer, Future<OcrPage>> reading = new LinkedHashMap<>();
		for (int i = 0; i < texts.size() && reading.size() < ocr.maxPages(); i++) {
			if (texts.get(i).isBlank()) {
				int number = i + 1;
				reading.put(number, ocrPages.submit(() -> ocr.recognise(pdf, number)));
			}
		}
		boolean unavailable = false;
		Set<Integer> blank = new HashSet<>();
		for (Map.Entry<Integer, Future<OcrPage>> page : reading.entrySet()) {
			try {
				OcrPage read = page.getValue().get();
				if (read == OcrEngine.BLANK) {
					blank.add(page.getKey());
				}
				else if (read != null && !read.text().isBlank()) {
					recognised.put(page.getKey(), read);
				}
			}
			catch (ExecutionException ex) {
				if (ex.getCause() instanceof OcrUnavailableException missing) {
					unavailable = true;
					if (ocrMissingLogged.compareAndSet(false, true)) {
						log.warn("OCR unavailable ({}): scanned pages stay unread", missing.getMessage());
					}
				}
				else {
					// Only which page and why: never its text.
					log.warn("OCR of page {} of document {} failed: {}", page.getKey(), documentId,
							ex.getCause().getClass().getSimpleName());
				}
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				reading.values().forEach(future -> future.cancel(true));
				break;
			}
		}
		return new Recognition(unavailable ? new LinkedHashMap<>() : recognised, unavailable, blank);
	}

	/**
	 * Text and HTML files: each section is stored as a page, so that answers
	 * can cite it. There is no PDF to open at a page.
	 */
	private void saveSections(Long documentId, List<String> sections, PageMethod method) {
		transaction.executeWithoutResult(status -> {
			pages.deleteByDocumentId(documentId);
			for (int i = 0; i < sections.size(); i++) {
				pages.save(new DocumentPage(documentId, i + 1, sections.get(i), method));
			}
			Document document = documents.findById(documentId).orElseThrow();
			if (sections.isEmpty()) {
				document.markNotAnalyzed("Fichier sans texte : non analysé");
			}
			else {
				document.markRead(sections.size(), 0, null);
			}
		});
	}

	/**
	 * Spreadsheets: each part of a sheet is stored as a page of rows and cells,
	 * so that answers can cite the sheet, the row and the cell.
	 */
	private void saveSheets(Long documentId, Spreadsheet spreadsheet) {
		List<String> sheetPages = SpreadsheetText.pages(spreadsheet);
		transaction.executeWithoutResult(status -> {
			pages.deleteByDocumentId(documentId);
			for (int i = 0; i < sheetPages.size(); i++) {
				pages.save(new DocumentPage(documentId, i + 1, sheetPages.get(i), PageMethod.SHEET));
			}
			Document document = documents.findById(documentId).orElseThrow();
			if (sheetPages.isEmpty()) {
				document.markNotAnalyzed("Tableur sans aucune valeur : non analysé");
			}
			else {
				document.markSheetsRead(sheetPages.size(),
						spreadsheet.warnings().isEmpty() ? null : String.join(" ; ", spreadsheet.warnings()));
			}
		});
	}

	private void update(Long documentId, Consumer<Document> change) {
		transaction.executeWithoutResult(status -> change.accept(documents.findById(documentId).orElseThrow()));
	}

	/**
	 * Whether a file is of a format read here, when it is not kept aside.
	 */
	public static boolean readable(String fileName) {
		String extension = extension(fileName);
		return extension.equals("pdf") || WORD.contains(extension) || TEXT.contains(extension)
				|| HTML.contains(extension) || SpreadsheetExtractor.FORMATS.contains(extension)
				|| ImageConverter.FORMATS.contains(extension);
	}

	public static String extension(String fileName) {
		int dot = fileName.lastIndexOf('.');
		return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

	private static String notAnalyzedReason(String extension) {
		if (DRAWING.contains(extension)) {
			return "Plan ou image conservé, non analysé";
		}
		if (ARCHIVE.contains(extension)) {
			return "Archive non prise en charge : décompressez-la avant le dépôt";
		}
		return "Format non analysé";
	}

}
