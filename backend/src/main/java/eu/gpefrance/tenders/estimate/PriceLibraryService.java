package eu.gpefrance.tenders.estimate;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.multipart.MultipartFile;

import eu.gpefrance.tenders.analysis.PriceScheduleChecker;
import eu.gpefrance.tenders.analysis.PriceScheduleChecker.ScheduleLine;
import eu.gpefrance.tenders.document.DocumentProcessor;
import eu.gpefrance.tenders.document.FileStorage;
import eu.gpefrance.tenders.document.Spreadsheet;
import eu.gpefrance.tenders.document.SpreadsheetExtractor;

/**
 * The prices GPE already gave: the unit prices of the price schedules it
 * filled and sent, read by plain code (same reading as the import into an
 * estimate: repeated headers, titles and totals left out). A line without a
 * written unit price (included, without object...) is not kept.
 */
@Service
public class PriceLibraryService {

	private static final Logger log = LoggerFactory.getLogger(PriceLibraryService.class);

	private static final int BATCH = 500;

	private static final int MAX_RESULTS = 50;

	private final JdbcTemplate jdbc;

	private final SpreadsheetExtractor extractor;

	private final Clock clock;

	private final FileStorage storage;

	public PriceLibraryService(JdbcTemplate jdbc, SpreadsheetExtractor extractor, Clock clock, FileStorage storage) {
		this.jdbc = jdbc;
		this.extractor = extractor;
		this.clock = clock;
		this.storage = storage;
	}

	/** @param hasFile its price schedule can be opened; not for the imports made before it was kept */
	public record Import(Long id, String label, String fileName, Integer priceYear, String domain, int itemCount,
			String importedBy, Instant importedAt, boolean hasFile) {
	}

	/** The price schedule of an import, to open. */
	public record ImportFile(String fileName, Path path) {
	}

	public record Item(Long id, String designation, String unit, BigDecimal unitPrice, BigDecimal quantity,
			String chapter, String reference, Long importId, String label, Integer priceYear) {
	}

	/**
	 * @param label the job the file was sent for (« Chambre funéraire Istres »)
	 * @param priceYear the year of its prices, to update them later
	 * @param domain ELECTRICITY, PHOTOVOLTAIC or null
	 */
	@Transactional
	public Import importFile(MultipartFile file, String label, Integer priceYear, String domain, String user) {
		String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
		if (!SpreadsheetExtractor.handles(name)) {
			throw problem("Cadre de prix attendu au format XLSX, XLS, ODS ou CSV.");
		}
		List<Line> lines = new ArrayList<>();
		for (Spreadsheet.Sheet sheet : read(file, DocumentProcessor.extension(name)).sheets()) {
			for (ScheduleLine line : PriceScheduleChecker.lines(sheet)) {
				if (line.unitPrice() != null && line.unitPrice().signum() > 0 && line.designation() != null) {
					lines.add(new Line(sheet.name(), line));
				}
			}
		}
		if (lines.isEmpty()) {
			throw problem("Aucune ligne avec un prix unitaire n'a été trouvée : le fichier doit avoir un en-tête "
					+ "(désignation, quantité, prix unitaire) et des prix remplis.");
		}
		Instant now = clock.instant();
		Path kept;
		try (InputStream content = file.getInputStream()) {
			kept = storage.store(content, storage.resolve("price-library"), name);
		}
		catch (IOException ex) {
			throw problem("Cadre de prix non enregistré : " + ex.getMessage());
		}
		// Not imported after all: the file kept goes too.
		org.springframework.transaction.support.TransactionSynchronizationManager
			.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
				@Override
				public void afterCompletion(int status) {
					if (status != STATUS_COMMITTED) {
						deleteQuietly(kept);
					}
				}
			});
		Long id = jdbc.queryForObject("""
				INSERT INTO price_library_import (label, file_name, price_year, domain, item_count, imported_by, imported_at,
				    storage_path)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id""", Long.class, label.strip(), name, priceYear, domain,
				lines.size(), user, Timestamp.from(now), storage.relativize(kept));
		for (int start = 0; start < lines.size(); start += BATCH) {
			jdbc.batchUpdate("""
					INSERT INTO price_library_item (import_id, sheet, source_row, chapter, reference, designation, unit,
					    quantity, unit_price)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""", lines.subList(start, Math.min(start + BATCH, lines.size())),
					BATCH, (statement, line) -> {
						statement.setLong(1, id);
						statement.setString(2, cut(line.sheet(), 100));
						statement.setInt(3, line.line().row());
						statement.setString(4, line.line().chapter());
						statement.setString(5, cut(line.line().reference(), 100));
						statement.setString(6, line.line().designation().strip());
						statement.setString(7, cut(line.line().unit(), 30));
						statement.setBigDecimal(8, line.line().quantity());
						statement.setBigDecimal(9, line.line().unitPrice());
					});
		}
		log.info("{} prices of « {} » added to the price library by {}", lines.size(), label, user);
		return new Import(id, label.strip(), name, priceYear, domain, lines.size(), user, now, true);
	}

	private record Line(String sheet, ScheduleLine line) {
	}

	public List<Import> imports() {
		return jdbc.query("""
				SELECT id, label, file_name, price_year, domain, item_count, imported_by, imported_at, storage_path
				FROM price_library_import ORDER BY imported_at DESC""",
				(row, n) -> new Import(row.getLong("id"), row.getString("label"), row.getString("file_name"),
						(Integer) row.getObject("price_year"), row.getString("domain"), row.getInt("item_count"),
						row.getString("imported_by"), row.getTimestamp("imported_at").toInstant(),
						row.getString("storage_path") != null));
	}

	/** Deletes the import, its lines and its file. */
	@Transactional
	public void deleteImport(Long id) {
		// Optional of a list: an import made before has a null path, which findFirst refuses.
		List<java.util.Optional<String>> found = jdbc.query("SELECT storage_path FROM price_library_import WHERE id = ?",
				(row, n) -> java.util.Optional.ofNullable(row.getString("storage_path")), id);
		if (found.isEmpty()) {
			throw new ErrorResponseException(HttpStatus.NOT_FOUND,
					ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Import introuvable."), null);
		}
		jdbc.update("DELETE FROM price_library_import WHERE id = ?", id);
		found.getFirst().ifPresent((path) -> deleteQuietly(storage.resolve(path)));
	}

	public ImportFile file(Long id) {
		return jdbc.query("SELECT file_name, storage_path FROM price_library_import WHERE id = ? AND storage_path IS NOT NULL",
				(row, n) -> new ImportFile(row.getString("file_name"), storage.resolve(row.getString("storage_path"))), id)
			.stream()
			.findFirst()
			.filter((found) -> Files.exists(found.path()))
			.orElseThrow(PriceLibraryService::notFound);
	}

	private static ErrorResponseException notFound() {
		return new ErrorResponseException(HttpStatus.NOT_FOUND,
				ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Fichier introuvable."), null);
	}

	private static void deleteQuietly(Path path) {
		try {
			Files.deleteIfExists(path);
		}
		catch (IOException ex) {
			log.warn("Price library file not deleted: {}", ex.getClass().getSimpleName());
		}
	}

	/**
	 * The lines whose designation holds every word searched, regardless of case
	 * and accents, the most recent prices first.
	 * @param domain ELECTRICITY or PHOTOVOLTAIC, or null for every domain
	 */
	public List<Item> search(String query, String unit, String domain) {
		List<String> words = query == null ? List.of()
				: Arrays.stream(query.strip().split("\\s+")).filter(word -> word.length() >= 2).limit(8).toList();
		StringBuilder sql = new StringBuilder("""
				SELECT i.id, i.designation, i.unit, i.unit_price, i.quantity, i.chapter, i.reference, i.import_id,
				       m.label, m.price_year
				FROM price_library_item i JOIN price_library_import m ON m.id = i.import_id WHERE TRUE""");
		List<Object> parameters = new ArrayList<>();
		for (String word : words) {
			sql.append(" AND ").append(COMPACT).append(" LIKE '%' || unaccent(lower(?)) || '%'");
			parameters.add(word.replace("%", "").replace("_", ""));
		}
		if (unit != null && !unit.isBlank()) {
			sql.append(" AND lower(i.unit) = lower(?)");
			parameters.add(unit.strip());
		}
		if (domain != null && !domain.isBlank()) {
			sql.append(" AND m.domain = ?");
			parameters.add(domain);
		}
		sql.append(" ORDER BY m.price_year DESC NULLS LAST, m.imported_at DESC, i.id LIMIT ").append(MAX_RESULTS);
		return jdbc.query(sql.toString(),
				(row, n) -> new Item(row.getLong("id"), row.getString("designation"), row.getString("unit"),
						row.getBigDecimal("unit_price"), row.getBigDecimal("quantity"), row.getString("chapter"),
						row.getString("reference"), row.getLong("import_id"), row.getString("label"),
						(Integer) row.getObject("price_year")),
				parameters.toArray());
	}

	/** The designation without case, accents nor spaces: « 3x1,5 » finds « 3 x 1,5 mm² ». */
	private static final String COMPACT = "regexp_replace(unaccent(lower(i.designation)), '\\s+', '', 'g')";

	private static final java.util.Set<String> STOP_WORDS = java.util.Set.of("pour", "avec", "dans", "sans",
			"compris", "fourniture", "pose", "mise", "oeuvre", "suivant", "selon", "type", "toutes", "tous", "ensemble",
			"travaux", "les", "des", "une", "aux", "sur", "par", "raccordement", "y");

	/** Every line, the most recent prices first: for the matching by plain code. */
	public List<Item> all() {
		return jdbc.query("""
				SELECT i.id, i.designation, i.unit, i.unit_price, i.quantity, i.chapter, i.reference, i.import_id,
				       m.label, m.price_year
				FROM price_library_item i JOIN price_library_import m ON m.id = i.import_id
				ORDER BY m.price_year DESC NULLS LAST, m.imported_at DESC, i.id""",
				(row, n) -> new Item(row.getLong("id"), row.getString("designation"), row.getString("unit"),
						row.getBigDecimal("unit_price"), row.getBigDecimal("quantity"), row.getString("chapter"),
						row.getString("reference"), row.getLong("import_id"), row.getString("label"),
						(Integer) row.getObject("price_year")));
	}

	public java.util.Optional<Item> find(Long id) {
		return jdbc.query("""
				SELECT i.id, i.designation, i.unit, i.unit_price, i.quantity, i.chapter, i.reference, i.import_id,
				       m.label, m.price_year
				FROM price_library_item i JOIN price_library_import m ON m.id = i.import_id WHERE i.id = ?""",
				(row, n) -> new Item(row.getLong("id"), row.getString("designation"), row.getString("unit"),
						row.getBigDecimal("unit_price"), row.getBigDecimal("quantity"), row.getString("chapter"),
						row.getString("reference"), row.getLong("import_id"), row.getString("label"),
						(Integer) row.getObject("price_year")),
				id)
			.stream()
			.findFirst();
	}

	/**
	 * The lines of the library sharing the most words with a designation, then
	 * the most recent, for the AI to choose from.
	 */
	public List<Item> candidates(String designation, int limit) {
		List<String> words = Arrays
			.stream(java.text.Normalizer.normalize(designation == null ? "" : designation, java.text.Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "")
				.toLowerCase(java.util.Locale.ROOT)
				.split("[^a-z0-9,./]+"))
			.map(word -> word.replaceAll("^[,./]+|[,./]+$", ""))
			.filter(word -> word.length() >= 3 && !STOP_WORDS.contains(word))
			.distinct()
			.limit(10)
			.toList();
		if (words.isEmpty()) {
			return List.of();
		}
		StringBuilder score = new StringBuilder("(0");
		List<Object> parameters = new ArrayList<>();
		for (String word : words) {
			score.append(" + CASE WHEN ").append(COMPACT).append(" LIKE ? THEN 1 ELSE 0 END");
			parameters.add("%" + word + "%");
		}
		score.append(")");
		parameters.add(limit);
		return jdbc.query("""
				SELECT * FROM (
				  SELECT i.id, i.designation, i.unit, i.unit_price, i.quantity, i.chapter, i.reference, i.import_id,
				         m.label, m.price_year, m.imported_at, %s AS score
				  FROM price_library_item i JOIN price_library_import m ON m.id = i.import_id) found
				WHERE score > 0 ORDER BY score DESC, price_year DESC NULLS LAST, imported_at DESC LIMIT ?"""
			.formatted(score),
				(row, n) -> new Item(row.getLong("id"), row.getString("designation"), row.getString("unit"),
						row.getBigDecimal("unit_price"), row.getBigDecimal("quantity"), row.getString("chapter"),
						row.getString("reference"), row.getLong("import_id"), row.getString("label"),
						(Integer) row.getObject("price_year")),
				parameters.toArray());
	}

	/**
	 * A price of the library brought to this year: price × (1 + rate) ^ years
	 * since its year, to the cent; as it is when its year is unknown or later.
	 */
	static BigDecimal indexed(BigDecimal price, Integer priceYear, BigDecimal annualRate, int currentYear) {
		if (priceYear == null || priceYear >= currentYear || annualRate == null) {
			return price.setScale(2, java.math.RoundingMode.HALF_UP);
		}
		return price.multiply(BigDecimal.ONE.add(annualRate).pow(currentYear - priceYear))
			.setScale(2, java.math.RoundingMode.HALF_UP);
	}

	/** Read from a temporary copy, deleted at once. */
	private Spreadsheet read(MultipartFile file, String extension) {
		Path copy = null;
		try (InputStream content = file.getInputStream()) {
			copy = Files.createTempFile("price-library-", "." + extension);
			Files.copy(content, copy, StandardCopyOption.REPLACE_EXISTING);
			return extractor.read(copy, extension);
		}
		catch (IOException ex) {
			throw problem("Cadre de prix illisible : " + ex.getMessage());
		}
		finally {
			if (copy != null) {
				try {
					Files.deleteIfExists(copy);
				}
				catch (IOException ex) {
					log.warn("Temporary price library file not deleted: {}", ex.getClass().getSimpleName());
				}
			}
		}
	}

	private static String cut(String value, int max) {
		return value == null || value.length() <= max ? value : value.substring(0, max);
	}

	private static ErrorResponseException problem(String detail) {
		return new ErrorResponseException(HttpStatus.BAD_REQUEST,
				ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail), null);
	}

}
