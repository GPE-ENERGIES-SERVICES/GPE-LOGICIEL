package eu.gpefrance.tenders.document;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import eu.gpefrance.tenders.document.ArchiveExtractor.ArchiveRejectedException;
import eu.gpefrance.tenders.document.ArchiveExtractor.ExtractedFile;
import eu.gpefrance.tenders.tender.TenderNotFoundException;
import eu.gpefrance.tenders.tender.TenderRepository;

/**
 * Receives the consultation file of a tender: each upload is a new version
 * of the dossier. Archives are extracted; files are kept as uploaded.
 */
@Service
public class DossierService {

	private final DossierRepository dossiers;

	private final TenderRepository tenders;

	private final FileStorage storage;

	private final ArchiveExtractor archiveExtractor;

	private final TransactionTemplate transaction;

	private final Clock clock;

	public DossierService(DossierRepository dossiers, TenderRepository tenders, FileStorage storage,
			ArchiveExtractor archiveExtractor, TransactionTemplate transaction, Clock clock) {
		this.dossiers = dossiers;
		this.tenders = tenders;
		this.storage = storage;
		this.archiveExtractor = archiveExtractor;
		this.transaction = transaction;
		this.clock = clock;
	}

	/**
	 * Stores the files and creates the new dossier version. Reading the
	 * documents is started separately, in the background.
	 * @return the id of the new dossier
	 */
	public synchronized Long upload(Long tenderId, List<MultipartFile> files, String uploadedBy) {
		if (!tenders.existsById(tenderId)) {
			throw new TenderNotFoundException(tenderId);
		}
		List<MultipartFile> nonEmpty = files == null ? List.of()
				: files.stream().filter(file -> !file.isEmpty()).toList();
		if (nonEmpty.isEmpty()) {
			throw new UploadRejectedException("Aucun fichier reçu");
		}

		int version = dossiers.findLastVersion(tenderId) + 1;
		String directory = "tenders/" + tenderId + "/v" + version;
		Path dossierDirectory = storage.resolve(directory);
		try {
			List<StoredFile> stored = store(nonEmpty, dossierDirectory);
			return transaction.execute(status -> {
				Dossier dossier = new Dossier(tenderId, version, uploadedBy, clock.instant());
				for (StoredFile file : stored) {
					dossier.addDocument(file.displayName(), storage.relativize(file.path()), file.size(), file.sha256(),
							PieceClassifier.classify(file.displayName()));
				}
				return dossiers.save(dossier).getId();
			});
		}
		catch (ArchiveRejectedException ex) {
			deleteQuietly(dossierDirectory);
			throw new UploadRejectedException(ex.getMessage());
		}
		catch (RuntimeException ex) {
			deleteQuietly(dossierDirectory);
			throw ex;
		}
	}

	private List<StoredFile> store(List<MultipartFile> files, Path dossierDirectory) {
		Path filesDirectory = dossierDirectory.resolve("files");
		Path archivesDirectory = dossierDirectory.resolve("archives");
		List<StoredFile> stored = new ArrayList<>();
		for (MultipartFile file : files) {
			String name = FileStorage.safeName(file.getOriginalFilename());
			try (InputStream in = file.getInputStream()) {
				if (ArchiveExtractor.isZip(name)) {
					Path archive = storage.store(in, archivesDirectory, name);
					Path target = FileStorage.uniquePath(filesDirectory, name.substring(0, name.length() - 4));
					for (ExtractedFile extracted : archiveExtractor.extract(archive, name, target)) {
						stored.add(describe(extracted.displayName(), extracted.path()));
					}
				}
				else {
					stored.add(describe(name, storage.store(in, filesDirectory, name)));
				}
			}
			catch (IOException ex) {
				throw new UncheckedIOException(ex);
			}
		}
		if (stored.isEmpty()) {
			throw new UploadRejectedException("Les archives déposées ne contiennent aucun fichier");
		}
		return stored;
	}

	private static StoredFile describe(String displayName, Path path) throws IOException {
		return new StoredFile(displayName, path, Files.size(path), sha256(path));
	}

	/**
	 * Fingerprint of the content: the same file uploaded twice is recognised.
	 */
	static String sha256(Path path) throws IOException {
		try (DigestInputStream in = new DigestInputStream(Files.newInputStream(path),
				MessageDigest.getInstance("SHA-256"))) {
			in.transferTo(OutputStream.nullOutputStream());
			return HexFormat.of().formatHex(in.getMessageDigest().digest());
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static void deleteQuietly(Path directory) {
		if (!Files.exists(directory)) {
			return;
		}
		try (var paths = Files.walk(directory)) {
			paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
		}
		catch (IOException ex) {
			// leftover files of a refused upload are harmless
		}
	}

	private record StoredFile(String displayName, Path path, long size, String sha256) {
	}

}
