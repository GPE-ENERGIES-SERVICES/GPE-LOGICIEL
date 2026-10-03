package eu.gpefrance.tenders.document;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import eu.gpefrance.tenders.document.OfficeConverter.ConversionException;

/**
 * Reads the documents of a dossier in the background: text of each PDF page,
 * Word documents converted to PDF first. Other formats are kept but marked
 * as not analysed. A document that fails never stops the others.
 */
@Service
public class DocumentProcessor {

	private static final Logger log = LoggerFactory.getLogger(DocumentProcessor.class);

	private static final Set<String> WORD = Set.of("doc", "docx", "odt", "rtf");

	private static final Set<String> SPREADSHEET = Set.of("xls", "xlsx", "xlsm", "ods", "csv");

	private static final Set<String> DRAWING = Set.of("dwg", "dxf", "jpg", "jpeg", "png", "tif", "tiff", "gif",
			"bmp", "svg");

	private static final Set<String> ARCHIVE = Set.of("7z", "rar", "tar", "gz", "tgz");

	private final DossierRepository dossiers;

	private final DocumentRepository documents;

	private final DocumentPageRepository pages;

	private final FileStorage storage;

	private final PdfTextExtractor pdfExtractor;

	private final OfficeConverter officeConverter;

	private final TransactionTemplate transaction;

	private final TaskExecutor taskExecutor;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	public DocumentProcessor(DossierRepository dossiers, DocumentRepository documents, DocumentPageRepository pages,
			FileStorage storage, PdfTextExtractor pdfExtractor, OfficeConverter officeConverter,
			TransactionTemplate transaction, @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor,
			ApplicationEventPublisher events, Clock clock) {
		this.dossiers = dossiers;
		this.documents = documents;
		this.pages = pages;
		this.storage = storage;
		this.pdfExtractor = pdfExtractor;
		this.officeConverter = officeConverter;
		this.transaction = transaction;
		this.taskExecutor = taskExecutor;
		this.events = events;
		this.clock = clock;
	}

	public void startInBackground(Long dossierId) {
		taskExecutor.execute(() -> process(dossierId));
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
		List<Long> pending = transaction.execute(status -> {
			Dossier dossier = dossiers.findById(dossierId).orElseThrow();
			dossier.startProcessing();
			return dossier.getDocuments()
				.stream()
				.filter(document -> document.getState() == DocumentState.PENDING)
				.map(Document::getId)
				.toList();
		});
		try {
			for (Long documentId : pending == null ? List.<Long>of() : pending) {
				processDocument(documentId);
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
		events.publishEvent(new DossierReadEvent(dossierId));
	}

	private void processDocument(Long documentId) {
		Document document = documents.findById(documentId).orElseThrow();
		String extension = extension(document.getFileName());
		Path file = storage.resolve(document.getStoragePath());
		try {
			if (extension.equals("pdf")) {
				saveText(documentId, file);
			}
			else if (WORD.contains(extension)) {
				Path outputDirectory = file.resolveSibling(".pdf").resolve(String.valueOf(documentId));
				saveText(documentId, officeConverter.toPdf(file, outputDirectory));
			}
			else {
				update(documentId, doc -> doc.markNotAnalyzed(notAnalyzedReason(extension)));
			}
		}
		catch (InvalidPasswordException ex) {
			update(documentId, doc -> doc.markFailed("PDF protégé par un mot de passe : non analysé"));
		}
		catch (IOException ex) {
			log.warn("Document {} could not be read", documentId, ex);
			update(documentId, doc -> doc.markFailed("Fichier illisible : non analysé"));
		}
		catch (ConversionException ex) {
			log.warn("Document {} could not be converted", documentId, ex);
			update(documentId, doc -> doc.markFailed(ex.getMessage() + " : non analysé"));
		}
	}

	private void saveText(Long documentId, Path pdf) throws IOException {
		List<String> texts = pdfExtractor.extractPages(pdf);
		String pdfPath = storage.relativize(pdf);
		transaction.executeWithoutResult(status -> {
			pages.deleteByDocumentId(documentId);
			int pagesWithoutText = 0;
			for (int i = 0; i < texts.size(); i++) {
				if (texts.get(i).isBlank()) {
					pagesWithoutText++;
				}
				pages.save(new DocumentPage(documentId, i + 1, texts.get(i), PageMethod.NATIVE));
			}
			documents.findById(documentId).orElseThrow().markRead(texts.size(), pagesWithoutText, pdfPath);
		});
	}

	private void update(Long documentId, Consumer<Document> change) {
		transaction.executeWithoutResult(status -> change.accept(documents.findById(documentId).orElseThrow()));
	}

	static String extension(String fileName) {
		int dot = fileName.lastIndexOf('.');
		return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

	private static String notAnalyzedReason(String extension) {
		if (SPREADSHEET.contains(extension)) {
			return "Tableur conservé, non analysé dans cette version";
		}
		if (DRAWING.contains(extension)) {
			return "Plan ou image conservé, non analysé";
		}
		if (ARCHIVE.contains(extension)) {
			return "Archive non prise en charge : décompressez-la avant le dépôt";
		}
		return "Format non analysé";
	}

}
