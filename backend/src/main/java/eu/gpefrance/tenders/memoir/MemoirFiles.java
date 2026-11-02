package eu.gpefrance.tenders.memoir;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.extractor.ExtractorFactory;
import org.apache.poi.extractor.POITextExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import eu.gpefrance.tenders.document.FileStorage;
import eu.gpefrance.tenders.document.FileTypeValidator;
import eu.gpefrance.tenders.document.FileTypeValidator.Kind;

/**
 * The files of the memoirs, under memoirs/ in the storage directory: images
 * put in a memoir (PNG, JPEG), and former memoirs (PDF, Word), whose text is
 * read at once for the AI. A file is checked by its content, as the files of
 * the consultations.
 */
@Component
class MemoirFiles {

	private static final Logger log = LoggerFactory.getLogger(MemoirFiles.class);

	/** Text kept of a former memoir: enough for its style and its structure. */
	private static final int MAX_TEXT = 300_000;

	private static final Set<String> WORD = Set.of("docx", "doc", "odt");

	private final FileStorage storage;

	private final MemoirProperties properties;

	MemoirFiles(FileStorage storage, MemoirProperties properties) {
		this.storage = storage;
		this.properties = properties;
	}

	/**
	 * @param relativePath where it is stored, relative to the storage directory
	 */
	record Stored(String fileName, String relativePath, long size, Path path) {
	}

	Stored storeImage(Long memoirId, MultipartFile file) {
		return store(file, "memoirs/" + memoirId, kind -> kind == Kind.PNG || kind == Kind.JPEG,
				"Image attendue : PNG ou JPEG.");
	}

	Stored storeFormerMemoir(MultipartFile file) {
		return store(file, "memoirs/library", kind -> kind == Kind.PDF || kind == Kind.ZIP || kind == Kind.OLE,
				"Mémoire attendu au format PDF ou Word.");
	}

	/** The text of a former memoir, null when none can be read (scanned PDF...). */
	String text(Stored stored) {
		String extension = stored.fileName().substring(stored.fileName().lastIndexOf('.') + 1).toLowerCase();
		try {
			String text;
			if (extension.equals("pdf")) {
				try (PDDocument document = Loader.loadPDF(stored.path().toFile())) {
					text = new PDFTextStripper().getText(document);
				}
			}
			else if (WORD.contains(extension) && !extension.equals("odt")) {
				try (POITextExtractor extractor = ExtractorFactory.createExtractor(stored.path().toFile())) {
					text = extractor.getText();
				}
			}
			else {
				return null;
			}
			text = text.replaceAll("[ \\t\\x0B\\f\\r]+", " ").replaceAll("\\n{3,}", "\n\n").strip();
			return text.isEmpty() ? null : text.length() <= MAX_TEXT ? text : text.substring(0, MAX_TEXT);
		}
		catch (IOException | RuntimeException ex) {
			log.warn("Text of a former memoir not read: {}", ex.getClass().getSimpleName());
			return null;
		}
	}

	Path resolve(String relativePath) {
		return storage.resolve(relativePath);
	}

	/** Removes files from the disk once the change is committed. */
	void deleteAfterCommit(List<String> relativePaths) {
		List<Path> paths = relativePaths.stream().map(storage::resolve).toList();
		Runnable delete = () -> paths.forEach(MemoirFiles::delete);
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					delete.run();
				}
			});
		}
		else {
			delete.run();
		}
	}

	private Stored store(MultipartFile file, String folder, java.util.function.Predicate<Kind> accepted,
			String refused) {
		if (file.isEmpty()) {
			throw MemoirRuleException.invalid("Fichier vide.");
		}
		if (file.getSize() > properties.maxFileSize()) {
			throw MemoirRuleException.invalid("Fichier trop volumineux (" + properties.maxFileSize() / 1_000_000
					+ " Mo au plus).");
		}
		Path temporary = null;
		try (InputStream content = file.getInputStream()) {
			temporary = Files.createTempFile("memoir-", ".upload");
			Files.copy(content, temporary, StandardCopyOption.REPLACE_EXISTING);
			FileTypeValidator.Validated validated = FileTypeValidator.validate(temporary, file.getOriginalFilename(), 200,
					200_000_000L);
			String extension = validated.fileName().substring(validated.fileName().lastIndexOf('.') + 1).toLowerCase();
			boolean office = validated.kind() != Kind.ZIP && validated.kind() != Kind.OLE || WORD.contains(extension);
			if (!accepted.test(validated.kind()) || !office) {
				throw MemoirRuleException.invalid(refused);
			}
			Path stored;
			try (InputStream copy = Files.newInputStream(temporary)) {
				stored = storage.store(copy, storage.resolve(folder), validated.fileName());
			}
			deleteOnRollback(stored);
			return new Stored(validated.fileName(), storage.relativize(stored), validated.size(), stored);
		}
		catch (FileTypeValidator.FileRejectedException ex) {
			throw MemoirRuleException.invalid("Fichier refusé : " + ex.getMessage() + ".");
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		finally {
			delete(temporary);
		}
	}

	private static void deleteOnRollback(Path stored) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCompletion(int status) {
					if (status != STATUS_COMMITTED) {
						delete(stored);
					}
				}
			});
		}
	}

	private static void delete(Path path) {
		if (path == null) {
			return;
		}
		try {
			Files.deleteIfExists(path);
		}
		catch (IOException ex) {
			log.warn("Memoir file not deleted: {}", ex.getClass().getSimpleName());
		}
	}

}
