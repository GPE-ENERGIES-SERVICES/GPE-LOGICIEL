package eu.gpefrance.tenders.document;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.function.LongPredicate;

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
			List<StoredFile> stored = store(nonEmpty.stream()
				// The file received is moved where it is kept (transferTo with a File renames it), not copied.
				.map(file -> new Source(file.getOriginalFilename(), file::getInputStream,
						target -> file.transferTo(target.toFile())))
				.toList(), dossierDirectory, new ArrayList<>());
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

	/**
	 * @param archives receives the archives kept beside their extracted files
	 */
	private List<StoredFile> store(List<Source> files, Path dossierDirectory, List<Path> archives) {
		Path filesDirectory = dossierDirectory.resolve("files");
		Path archivesDirectory = dossierDirectory.resolve("archives");
		List<StoredFile> stored = new ArrayList<>();
		for (Source file : files) {
			String name = FileStorage.safeName(file.name());
			try {
				if (ArchiveExtractor.isZip(name)) {
					Path archive = keep(file, archivesDirectory, name, null);
					archives.add(archive);
					Path target = FileStorage.uniquePath(filesDirectory, name.substring(0, name.length() - 4));
					for (ExtractedFile extracted : archiveExtractor.extract(archive, name, target)) {
						stored.add(new StoredFile(extracted.displayName(), extracted.path(), extracted.size(),
								extracted.sha256()));
					}
				}
				else {
					MessageDigest sha256 = ArchiveExtractor.sha256();
					Path path = keep(file, filesDirectory, name, sha256);
					stored.add(new StoredFile(name, path, Files.size(path), HexFormat.of().formatHex(sha256.digest())));
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

	/**
	 * Keeps a file in the directory: moved when it can be (a file the server
	 * has just received), copied otherwise, its fingerprint taken on the way.
	 * @param sha256 receives its content, or null
	 */
	private Path keep(Source file, Path directory, String name, MessageDigest sha256) throws IOException {
		if (file.mover() != null) {
			Files.createDirectories(directory);
			Path target = FileStorage.uniquePath(directory, name);
			file.mover().moveTo(target);
			if (sha256 != null) {
				try (InputStream in = new DigestInputStream(Files.newInputStream(target), sha256)) {
					in.transferTo(OutputStream.nullOutputStream());
				}
			}
			return target;
		}
		try (InputStream in = file.content().open()) {
			return storage.store(sha256 == null ? in : new DigestInputStream(in, sha256), directory, name);
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

	/**
	 * Keeps the files retrieved from a link, already validated, as a new
	 * version of the dossier of the tender, read like an upload (archives
	 * extracted within the same limits). No new version when the documents are
	 * exactly those of the last version (same SHA-256): a rectification that
	 * changed them makes one. The bytes written must fit in the quota of the
	 * retrieved files; otherwise nothing is kept.
	 * @param fitsQuota whether the bytes written fit in the quota
	 * @throws StorageLimitException when they do not
	 */
	public synchronized RetrievedDossier storeRetrieved(Long tenderId, Long dceLinkId, List<ReceivedFile> files,
			LongPredicate fitsQuota) {
		if (!tenders.existsById(tenderId)) {
			throw new TenderNotFoundException(tenderId);
		}
		int version = dossiers.findLastVersion(tenderId) + 1;
		Path dossierDirectory = storage.resolve("tenders/" + tenderId + "/v" + version);
		try {
			List<Path> archives = new ArrayList<>();
			List<StoredFile> stored = store(files.stream()
				.map(file -> new Source(file.fileName(), () -> Files.newInputStream(file.path()), null))
				.toList(), dossierDirectory, archives);
			Previous previous = transaction.execute(status -> dossiers.findByTenderIdOrderByVersionDesc(tenderId)
				.stream()
				.findFirst()
				.map(last -> new Previous(last.getVersion(),
						last.getDocuments().stream().map(Document::getSha256).sorted().toList()))
				.orElse(null));
			if (previous != null && previous.sha256().equals(stored.stream().map(StoredFile::sha256).sorted().toList())) {
				deleteQuietly(dossierDirectory);
				return new RetrievedDossier(null, previous.version(), stored.size(), 0);
			}
			// Only what this retrieval wrote: the extracted or received files and the archives.
			long bytes = stored.stream().mapToLong(StoredFile::size).sum() + sizeOf(archives);
			if (!fitsQuota.test(bytes)) {
				deleteQuietly(dossierDirectory);
				throw new StorageLimitException(bytes);
			}
			Long dossierId = transaction.execute(status -> {
				Dossier dossier = Dossier.retrieved(tenderId, version, dceLinkId, bytes, clock.instant());
				for (StoredFile file : stored) {
					dossier.addDocument(file.displayName(), storage.relativize(file.path()), file.size(), file.sha256(),
							PieceClassifier.classify(file.displayName()));
				}
				return dossiers.save(dossier).getId();
			});
			return new RetrievedDossier(dossierId, version, stored.size(), bytes);
		}
		catch (ArchiveRejectedException ex) {
			deleteQuietly(dossierDirectory);
			throw new FileTypeValidator.FileRejectedException(ex.getMessage());
		}
		catch (RuntimeException ex) {
			deleteQuietly(dossierDirectory);
			throw ex;
		}
	}

	private static long sizeOf(List<Path> files) {
		return files.stream().mapToLong(path -> path.toFile().length()).sum();
	}

	/** The last version, with the SHA-256 of its documents: the same documents make no new version. */
	private record Previous(int version, List<String> sha256) {
	}

	/**
	 * A file to keep: its name and how to read it.
	 * @param mover moves it where it is kept, faster than a copy; null when it
	 * must be copied
	 */
	private record Source(String name, Opener content, Mover mover) {
	}

	@FunctionalInterface
	private interface Mover {

		void moveTo(Path target) throws IOException;

	}

	@FunctionalInterface
	private interface Opener {

		InputStream open() throws IOException;

	}

	/** A retrieved file, validated, waiting in a temporary folder. */
	public record ReceivedFile(String fileName, Path path) {
	}

	/**
	 * @param dossierId the new version, null when the documents are those of
	 * the last version
	 * @param version the new version, or the identical last one
	 */
	public record RetrievedDossier(Long dossierId, int version, int fileCount, long storedBytes) {
	}

	public static class StorageLimitException extends RuntimeException {

		StorageLimitException(long bytes) {
			super("Quota des DCE récupérés atteint (" + bytes + " octets à écrire)");
		}

	}

	private record StoredFile(String displayName, Path path, long size, String sha256) {
	}

}
