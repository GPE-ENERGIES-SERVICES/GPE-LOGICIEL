package eu.gpefrance.tenders.supplier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.multipart.MultipartFile;

import eu.gpefrance.tenders.document.DocumentProcessor;
import eu.gpefrance.tenders.document.Spreadsheet;
import eu.gpefrance.tenders.document.SpreadsheetExtractor;

/**
 * The suppliers and the import of their price files. An import replaces the
 * price of each reference it holds and leaves the others as they are; the
 * estimates keep the prices they copied.
 */
@Service
public class SupplierService {

	private static final Logger log = LoggerFactory.getLogger(SupplierService.class);

	private static final int BATCH = 500;

	private final JdbcTemplate jdbc;

	private final SpreadsheetExtractor extractor;

	private final Clock clock;

	public SupplierService(JdbcTemplate jdbc, SpreadsheetExtractor extractor, Clock clock) {
		this.jdbc = jdbc;
		this.extractor = extractor;
		this.clock = clock;
	}

	public record SupplierResponse(Long id, String code, String name, long offers, Instant lastImport) {
	}

	/**
	 * @param columns header of the file found for each kind of value
	 * @param rejected lines left out: no reference, designation or price
	 */
	public record ImportResult(int imported, int rejected, Map<String, String> columns, List<String> warnings) {
	}

	public List<SupplierResponse> list() {
		return jdbc.query("""
				SELECT s.id, s.code, s.name, count(o.id) AS offers, max(o.imported_at) AS last_import
				FROM supplier s LEFT JOIN supplier_offer o ON o.supplier_id = s.id
				GROUP BY s.id ORDER BY s.name""", (row, n) -> {
			Timestamp last = row.getTimestamp("last_import");
			return new SupplierResponse(row.getLong("id"), row.getString("code"), row.getString("name"),
					row.getLong("offers"), last == null ? null : last.toInstant());
		});
	}

	public SupplierResponse create(String code, String name, String user) {
		try {
			Long id = jdbc.queryForObject("INSERT INTO supplier (code, name, created_at) VALUES (?, ?, ?) RETURNING id",
					Long.class, code.strip().toUpperCase(), name.strip(), Timestamp.from(clock.instant()));
			log.info("Supplier {} created by {}", code, user);
			return new SupplierResponse(id, code.strip().toUpperCase(), name.strip(), 0, null);
		}
		catch (DuplicateKeyException ex) {
			throw problem(HttpStatus.CONFLICT, "Ce code de fournisseur existe déjà.");
		}
	}

	/**
	 * @param validFrom the date of the prices, kept with each offer, or null
	 */
	@Transactional
	public ImportResult importOffers(Long supplierId, MultipartFile file, LocalDate validFrom, String user) {
		if (jdbc.queryForObject("SELECT count(*) FROM supplier WHERE id = ?", Long.class, supplierId) == 0) {
			throw problem(HttpStatus.NOT_FOUND, "Fournisseur introuvable.");
		}
		String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
		if (!SpreadsheetExtractor.handles(name)) {
			throw problem(HttpStatus.BAD_REQUEST, "Fichier de prix attendu au format CSV, XLSX, XLS ou ODS.");
		}
		OfferFileReader.Result result = OfferFileReader.read(read(file, DocumentProcessor.extension(name)));
		if (result.columns().isEmpty()) {
			throw problem(HttpStatus.BAD_REQUEST, "Colonnes non reconnues : le fichier doit avoir un en-tête avec une "
					+ "référence, une désignation et un prix (net ou tarif).");
		}
		Timestamp now = Timestamp.from(clock.instant());
		List<OfferFileReader.Line> lines = result.lines();
		for (int start = 0; start < lines.size(); start += BATCH) {
			jdbc.batchUpdate("""
					INSERT INTO supplier_offer (supplier_id, supplier_ref, manufacturer_code, manufacturer_ref, ean,
					    designation, purchase_unit, pack_quantity, list_price, discount_rate, net_price, valid_from,
					    imported_at, imported_by)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					ON CONFLICT (supplier_id, supplier_ref) DO UPDATE SET
					    manufacturer_code = COALESCE(excluded.manufacturer_code, supplier_offer.manufacturer_code),
					    manufacturer_ref = COALESCE(excluded.manufacturer_ref, supplier_offer.manufacturer_ref),
					    ean = excluded.ean, designation = excluded.designation, purchase_unit = excluded.purchase_unit,
					    pack_quantity = excluded.pack_quantity, list_price = excluded.list_price,
					    discount_rate = excluded.discount_rate, net_price = excluded.net_price,
					    valid_from = excluded.valid_from, valid_until = NULL, imported_at = excluded.imported_at,
					    imported_by = excluded.imported_by, eco_tax = NULL, price_source = 'IMPORT', priced_quantity = NULL""",
					lines.subList(start, Math.min(start + BATCH, lines.size())), BATCH, (statement, line) -> {
						statement.setLong(1, supplierId);
						statement.setString(2, line.supplierRef());
						statement.setString(3, line.manufacturerCode());
						statement.setString(4, line.manufacturerRef());
						statement.setString(5, line.ean());
						statement.setString(6, line.designation());
						statement.setString(7, line.unit());
						statement.setBigDecimal(8, line.packQuantity());
						statement.setBigDecimal(9, line.listPrice());
						statement.setBigDecimal(10, line.discountRate());
						statement.setBigDecimal(11, line.netPrice());
						statement.setObject(12, validFrom);
						statement.setTimestamp(13, now);
						statement.setString(14, user);
					});
		}
		// One line of history for each price this import changed.
		jdbc.update("""
				INSERT INTO supplier_offer_price (offer_id, list_price, discount_rate, net_price, pack_quantity, source,
				    observed_at, observed_by)
				SELECT o.id, o.list_price, o.discount_rate, o.net_price, o.pack_quantity, 'IMPORT', o.imported_at, o.imported_by
				FROM supplier_offer o
				LEFT JOIN LATERAL (SELECT * FROM supplier_offer_price p WHERE p.offer_id = o.id
				    ORDER BY p.observed_at DESC, p.id DESC LIMIT 1) last ON TRUE
				WHERE o.supplier_id = ? AND o.imported_at = ? AND (last.id IS NULL
				    OR last.net_price IS DISTINCT FROM o.net_price OR last.list_price IS DISTINCT FROM o.list_price
				    OR last.discount_rate IS DISTINCT FROM o.discount_rate OR last.pack_quantity IS DISTINCT FROM o.pack_quantity)""",
				supplierId, now);
		log.info("{} prices imported for supplier {} by {} ({} lines left out)", lines.size(), supplierId, user,
				result.rejected());
		return new ImportResult(lines.size(), result.rejected(),
				result.columns()
					.entrySet()
					.stream()
					.collect(java.util.stream.Collectors.toMap(entry -> entry.getKey().name(), Map.Entry::getValue)),
				result.warnings());
	}

	/** Read from a temporary copy, deleted at once: the file of the supplier is not kept. */
	private Spreadsheet read(MultipartFile file, String extension) {
		Path copy = null;
		try (InputStream content = file.getInputStream()) {
			copy = Files.createTempFile("prices-", "." + extension);
			Files.copy(content, copy, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			return extractor.read(copy, extension);
		}
		catch (IOException ex) {
			throw problem(HttpStatus.BAD_REQUEST, "Fichier de prix illisible : " + ex.getMessage());
		}
		finally {
			if (copy != null) {
				try {
					Files.deleteIfExists(copy);
				}
				catch (IOException ex) {
					log.warn("Temporary price file not deleted: {}", ex.getClass().getSimpleName());
				}
			}
		}
	}

	private static ErrorResponseException problem(HttpStatus status, String detail) {
		return new ErrorResponseException(status, ProblemDetail.forStatusAndDetail(status, detail), null);
	}

}
