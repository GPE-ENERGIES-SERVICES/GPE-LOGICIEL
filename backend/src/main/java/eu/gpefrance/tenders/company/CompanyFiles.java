package eu.gpefrance.tenders.company;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import eu.gpefrance.tenders.document.FileStorage;
import eu.gpefrance.tenders.document.FileTypeValidator;
import eu.gpefrance.tenders.document.FileTypeValidator.Kind;

/**
 * The files of the company, in the storage directory under company/. A file
 * is checked by its content, as the files of the consultations: PDF, images
 * and office documents only, archives refused; a logo is an image. A file
 * removed from the database is removed from the disk once the change is
 * committed.
 */
@Component
public class CompanyFiles {

	private static final Logger log = LoggerFactory.getLogger(CompanyFiles.class);

	private static final Set<Kind> ACCEPTED = Set.of(Kind.PDF, Kind.PNG, Kind.JPEG, Kind.TIFF, Kind.ZIP, Kind.OLE);

	private static final Set<Kind> IMAGES = Set.of(Kind.PNG, Kind.JPEG);

	/** Office documents are zip files inside: these extensions only, never a bare archive. */
	private static final Set<String> OFFICE = Set.of("docx", "xlsx", "pptx", "odt", "ods", "odp", "doc", "xls", "ppt");

	private final JdbcTemplate jdbc;

	private final FileStorage storage;

	private final CompanyProperties properties;

	private final Clock clock;

	public CompanyFiles(JdbcTemplate jdbc, FileStorage storage, CompanyProperties properties, Clock clock) {
		this.jdbc = jdbc;
		this.storage = storage;
		this.properties = properties;
		this.clock = clock;
	}

	public record FileResponse(Long id, String fileName, long sizeBytes, String uploadedBy, Instant uploadedAt) {
	}

	record Stored(FileResponse file, Path path) {
	}

	/** The files of each record of a kind, the last uploaded first. */
	Map<Long, List<FileResponse>> byOwner(FileOwner owner) {
		Map<Long, List<FileResponse>> files = new LinkedHashMap<>();
		jdbc.query("SELECT * FROM company_file WHERE owner_type = ? ORDER BY id DESC", row -> {
			files.computeIfAbsent(row.getLong("owner_id"), id -> new ArrayList<>()).add(response(row));
		}, owner.name());
		return files;
	}

	List<FileResponse> of(FileOwner owner, Long ownerId) {
		return byOwner(owner).getOrDefault(ownerId, List.of());
	}

	FileResponse store(FileOwner owner, Long ownerId, MultipartFile file, String user) {
		if (file.isEmpty()) {
			throw CompanyRuleException.invalid("Fichier vide.");
		}
		if (file.getSize() > properties.maxFileSize()) {
			throw CompanyRuleException.invalid("Fichier trop volumineux (" + properties.maxFileSize() / 1_000_000
					+ " Mo au plus).");
		}
		Path temporary = null;
		try (InputStream content = file.getInputStream()) {
			temporary = Files.createTempFile("company-", ".upload");
			Files.copy(content, temporary, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			FileTypeValidator.Validated validated = FileTypeValidator.validate(temporary, file.getOriginalFilename(),
					200, 200_000_000L);
			check(owner, validated);
			Path stored;
			try (InputStream copy = Files.newInputStream(temporary)) {
				stored = storage.store(copy, storage.resolve(owner.folder() + "/" + ownerId), validated.fileName());
			}
			Instant now = clock.instant();
			Long id = jdbc.queryForObject("""
					INSERT INTO company_file (owner_type, owner_id, file_name, storage_path, size_bytes, uploaded_by,
					    uploaded_at)
					VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id""", Long.class, owner.name(), ownerId, validated.fileName(),
					storage.relativize(stored), validated.size(), user, Timestamp.from(now));
			deleteOnRollback(stored);
			log.info("File {} of {} {} stored by {}", id, owner, ownerId, user);
			return new FileResponse(id, validated.fileName(), validated.size(), user, now);
		}
		catch (FileTypeValidator.FileRejectedException ex) {
			throw CompanyRuleException.invalid("Fichier refusé : " + ex.getMessage() + ".");
		}
		catch (IOException ex) {
			throw new java.io.UncheckedIOException(ex);
		}
		finally {
			delete(temporary);
		}
	}

	Optional<Stored> find(Long fileId) {
		return jdbc.query("SELECT * FROM company_file WHERE id = ?",
				(row, n) -> new Stored(response(row), storage.resolve(row.getString("storage_path"))), fileId)
			.stream()
			.findFirst();
	}

	/** Removes a file, from the disk once the change is committed. */
	void delete(Long fileId) {
		List<String> paths = jdbc.queryForList("DELETE FROM company_file WHERE id = ? RETURNING storage_path",
				String.class, fileId);
		if (paths.isEmpty()) {
			throw CompanyRuleException.notFound();
		}
		deleteAfterCommit(paths);
	}

	/** Every file of a record, removed with it. */
	void deleteAll(FileOwner owner, Collection<Long> ownerIds) {
		for (Long ownerId : ownerIds) {
			deleteAfterCommit(jdbc.queryForList(
					"DELETE FROM company_file WHERE owner_type = ? AND owner_id = ? RETURNING storage_path", String.class,
					owner.name(), ownerId));
		}
	}

	private static void check(FileOwner owner, FileTypeValidator.Validated validated) {
		String extension = validated.fileName().substring(validated.fileName().lastIndexOf('.') + 1).toLowerCase();
		if (owner == FileOwner.LOGO && !IMAGES.contains(validated.kind())) {
			throw CompanyRuleException.invalid("Le logo doit être une image PNG ou JPEG.");
		}
		if (!ACCEPTED.contains(validated.kind())
				|| (validated.kind() == Kind.ZIP || validated.kind() == Kind.OLE) && !OFFICE.contains(extension)) {
			throw CompanyRuleException.invalid("Format refusé : PDF, image (PNG, JPEG, TIFF) ou document bureautique "
					+ "seulement, sans archive.");
		}
	}

	private void deleteAfterCommit(List<String> paths) {
		List<Path> files = paths.stream().map(storage::resolve).toList();
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					files.forEach(CompanyFiles::delete);
				}
			});
		}
		else {
			files.forEach(CompanyFiles::delete);
		}
	}

	/** A file written for a change that is cancelled is not left on the disk. */
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
			log.warn("Company file not deleted: {}", ex.getClass().getSimpleName());
		}
	}

	private static FileResponse response(java.sql.ResultSet row) throws java.sql.SQLException {
		return new FileResponse(row.getLong("id"), row.getString("file_name"), row.getLong("size_bytes"),
				row.getString("uploaded_by"), row.getTimestamp("uploaded_at").toInstant());
	}

}
