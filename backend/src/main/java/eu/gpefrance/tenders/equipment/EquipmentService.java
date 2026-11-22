package eu.gpefrance.tenders.equipment;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import eu.gpefrance.tenders.document.FileStorage;
import eu.gpefrance.tenders.document.FileTypeValidator;

/**
 * The technical sheets of the equipment GPE installs, and the equipment chosen
 * for a tender. What a sheet is (category, brand, reference) is guessed from
 * its file name by plain code, and stays to be corrected by a user.
 */
@Service
public class EquipmentService {

	private static final Logger log = LoggerFactory.getLogger(EquipmentService.class);

	/** The kinds of equipment, in the order of the equipment table of GPE. */
	public static final Set<String> CATEGORIES = Set.of("MODULE", "MOUNTING", "INVERTER", "MONITORING",
			"ELECTRICAL_BOX", "CABLE_DC", "CABLE_AC", "CONNECTOR", "CABLE_TRAY", "SAFETY", "OTHER");

	/** The label of each category in the equipment table, in its order. */
	public static final Map<String, String> TABLE_LABELS = labels();

	private final JdbcTemplate jdbc;

	private final FileStorage storage;

	private final Clock clock;

	public EquipmentService(JdbcTemplate jdbc, FileStorage storage, Clock clock) {
		this.jdbc = jdbc;
		this.storage = storage;
		this.clock = clock;
	}

	public record Sheet(Long id, String category, String brand, String reference, String fileName, long sizeBytes,
			String uploadedBy, Instant uploadedAt) {
	}

	/** What a file name says of its equipment, before a user corrects it. */
	public record Guess(String category, String brand, String reference) {
	}

	public List<Sheet> list() {
		return jdbc.query(SELECT + " ORDER BY category, brand NULLS LAST, reference", EquipmentService::sheet);
	}

	/**
	 * Adds a sheet; what is not given is guessed from the file name. The same
	 * file sent again is the sheet already kept.
	 */
	@Transactional
	public Sheet add(MultipartFile file, String category, String brand, String reference, String user) {
		if (file.isEmpty()) {
			throw invalid("Fichier vide.");
		}
		Path temporary = null;
		try (InputStream content = file.getInputStream()) {
			temporary = Files.createTempFile("equipment-", ".upload");
			Files.copy(content, temporary, StandardCopyOption.REPLACE_EXISTING);
			FileTypeValidator.Validated validated = FileTypeValidator.validate(temporary, file.getOriginalFilename(), 1,
					100_000_000L);
			if (validated.kind() != FileTypeValidator.Kind.PDF) {
				throw invalid("Fiche technique attendue au format PDF.");
			}
			String hash = sha256(temporary);
			Optional<Sheet> known = jdbc.query(SELECT + " WHERE sha256 = ?", EquipmentService::sheet, hash).stream().findFirst();
			if (known.isPresent()) {
				return known.get();
			}
			Guess guess = guess(validated.fileName());
			String chosen = clean(category) != null ? check(category) : guess.category();
			String madeBy = clean(brand) != null ? clean(brand) : guess.brand();
			String ref = clean(reference) != null ? clean(reference) : guess.reference();
			Path stored;
			try (InputStream copy = Files.newInputStream(temporary)) {
				stored = storage.store(copy, storage.resolve("equipment"), validated.fileName());
			}
			Long id = jdbc.queryForObject("""
					INSERT INTO equipment_sheet (category, brand, reference, file_name, storage_path, size_bytes, sha256,
					    uploaded_by, uploaded_at)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id""", Long.class, chosen, cut(madeBy, 100), cut(ref, 200),
					validated.fileName(), storage.relativize(stored), validated.size(), hash, user,
					Timestamp.from(clock.instant()));
			log.info("Technical sheet {} added by {}", id, user);
			return find(id);
		}
		catch (FileTypeValidator.FileRejectedException ex) {
			throw invalid("Fichier refusé : " + ex.getMessage() + ".");
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		finally {
			if (temporary != null) {
				temporary.toFile().delete();
			}
		}
	}

	@Transactional
	public Sheet update(Long id, String category, String brand, String reference) {
		String ref = clean(reference);
		if (ref == null) {
			throw invalid("Donnez la référence du matériel.");
		}
		if (jdbc.update("UPDATE equipment_sheet SET category = ?, brand = ?, reference = ? WHERE id = ?", check(category),
				cut(clean(brand), 100), cut(ref, 200), id) == 0) {
			throw notFound();
		}
		return find(id);
	}

	/** Deletes the sheet, its file, and takes it out of the tenders. */
	@Transactional
	public void delete(Long id) {
		Path file = path(id);
		jdbc.update("DELETE FROM equipment_sheet WHERE id = ?", id);
		file.toFile().delete();
	}

	public record SheetFile(String fileName, Path path) {
	}

	public SheetFile file(Long id) {
		Sheet sheet = find(id);
		return new SheetFile(sheet.fileName(), path(id));
	}

	/** The equipment chosen for a tender, in the order of the equipment table. */
	public List<Sheet> ofTender(Long tenderId) {
		return jdbc.query(SELECT + " JOIN tender_equipment t ON t.sheet_id = equipment_sheet.id WHERE t.tender_id = ?",
				EquipmentService::sheet, tenderId)
			.stream()
			.sorted(java.util.Comparator.comparingInt((Sheet sheet) -> order(sheet.category()))
				.thenComparing(Sheet::reference))
			.toList();
	}

	/** The equipment chosen for a tender, replacing the former choice. */
	@Transactional
	public List<Sheet> choose(Long tenderId, List<Long> sheetIds, String user) {
		if (jdbc.queryForObject("SELECT count(*) FROM tender WHERE id = ?", Long.class, tenderId) == 0) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Appel d'offres introuvable.");
		}
		jdbc.update("DELETE FROM tender_equipment WHERE tender_id = ?", tenderId);
		Timestamp now = Timestamp.from(clock.instant());
		for (Long sheetId : sheetIds.stream().distinct().toList()) {
			find(sheetId);
			jdbc.update("INSERT INTO tender_equipment (tender_id, sheet_id, chosen_by, chosen_at) VALUES (?, ?, ?, ?)",
					tenderId, sheetId, user, now);
		}
		return ofTender(tenderId);
	}

	/** The file of each sheet chosen for a tender, for its answer. */
	public List<SheetFile> filesOfTender(Long tenderId) {
		return ofTender(tenderId).stream().map((sheet) -> new SheetFile(sheet.fileName(), path(sheet.id()))).toList();
	}

	// Reading the file name

	/**
	 * The category, brand and reference a file name tells: « DMEGC DM460G12RT-B48HBT – 460 Wc.pdf » is a module of
	 * DMEGC, « HWE-SUN2000-50KTL-M3.pdf » an inverter of Huawei. Unknown: OTHER, the name as reference.
	 */
	static Guess guess(String fileName) {
		String base = fileName.substring(fileName.lastIndexOf('/') + 1).replaceFirst("\\.[^.]+$", "");
		String text = plain(base);
		String category = category(text);
		String brand = brand(text);
		String reference = base.replaceFirst("(?i)^(annexe\\s*\\d+\\s*[–-]\\s*)?(ft[-\\s]+|fiche technique\\s*)?", "")
			.replace('_', ' ')
			.replaceAll("\\s+", " ")
			.strip();
		return new Guess(category, brand, reference.isEmpty() ? base : reference);
	}

	private static String category(String text) {
		// « HWE3152972 »: an inverter in the references of Huawei at the suppliers.
		if (text.matches(".*(sun2000|onduleur|inverter|\\bktl\\b|\\bhwe ?\\d).*")) {
			return "INVERTER";
		}
		if (text.matches(".*(fusionsolar|smartlogger|webdyn|monitoring|supervision|gateway).*")) {
			return "MONITORING";
		}
		if (text.matches(".*(h1z2z2|cables? dc|cable pv|solaire dc).*")) {
			return "CABLE_DC";
		}
		if (text.matches(".*(u1000|r2v|cables? ac|fr-n1|x6g).*")) {
			return "CABLE_AC";
		}
		if (text.matches(".*(mc4|connecteur|staubli).*")) {
			return "CONNECTOR";
		}
		if (text.matches(".*(arret d.urgence|\\bau\\b|bris de glace|coupure pompier|pompiers).*")) {
			return "SAFETY";
		}
		if (text.matches(".*(tgbt|coffret|\\bagcp\\b|\\bnsx\\b|disjoncteur|parafoudre|tableau).*")) {
			return "ELECTRICAL_BOX";
		}
		if (text.matches(".*(chemin de cable|niedax|goulotte).*")) {
			return "CABLE_TRAY";
		}
		if (text.matches(".*(soprasolar|fix evo|dome|helios|kogysun|novotegra|k2|jorisolar|opti.roof|s360|integration|fixation|bac acier|top fix).*")) {
			return "MOUNTING";
		}
		if (text.matches(".*(dmegc|\\bdm\\d{3}|jinko|jkm|longi|trina|ja ?solar|jam\\d|module|panneau|\\b\\d{3} ?wc\\b).*")) {
			return "MODULE";
		}
		return "OTHER";
	}

	private static final Map<String, String> BRANDS = brands();

	private static String brand(String text) {
		return BRANDS.entrySet()
			.stream()
			.filter((entry) -> text.matches(".*(" + entry.getKey() + ").*"))
			.map(Map.Entry::getValue)
			.findFirst()
			.orElse(null);
	}

	private static Map<String, String> brands() {
		Map<String, String> brands = new LinkedHashMap<>();
		brands.put("sun2000|huawei|\\bhwe\\b|\\bhwe ?\\d|fusionsolar|smartlogger", "Huawei");
		brands.put("dmegc|\\bdm\\d{3}", "DMEGC");
		brands.put("jinko|jkm", "JinkoSolar");
		brands.put("\\bja ?solar|jam\\d", "JA Solar");
		brands.put("longi", "LONGi");
		brands.put("soprasolar|fix evo", "Soprasolar");
		brands.put("dome|helios|kogysun|s360", "Dome Solar");
		brands.put("novotegra", "Novotegra");
		brands.put("\\bk2\\b", "K2 Systems");
		brands.put("jorisolar|opti.roof", "JoriSolar");
		brands.put("webdyn", "Webdyn");
		brands.put("staubli|mc4", "Stäubli");
		brands.put("h1z2z2|u1000|r2v|nexans", "Nexans");
		brands.put("niedax", "Niedax");
		brands.put("\\bnsx\\b|schneider", "Schneider Electric");
		return brands;
	}

	private static Map<String, String> labels() {
		Map<String, String> labels = new LinkedHashMap<>();
		labels.put("MODULE", "Panneaux photovoltaïques");
		labels.put("MOUNTING", "Système de fixation");
		labels.put("INVERTER", "Onduleur");
		labels.put("MONITORING", "Système de supervision");
		labels.put("ELECTRICAL_BOX", "Coffret électrique + protections électriques + parafoudre");
		labels.put("CABLE_DC", "Câbles PV (DC)");
		labels.put("CABLE_AC", "Câbles AC");
		labels.put("CONNECTOR", "Connecteurs");
		labels.put("CABLE_TRAY", "Chemin de câbles");
		labels.put("SAFETY", "Dispositif de coupure d'urgence");
		labels.put("OTHER", "Autre matériel");
		return labels;
	}

	static int order(String category) {
		return List.copyOf(TABLE_LABELS.keySet()).indexOf(category);
	}

	// Rows

	private static final String SELECT = """
			SELECT equipment_sheet.id, category, brand, reference, file_name, size_bytes, uploaded_by, uploaded_at
			FROM equipment_sheet""";

	private static Sheet sheet(java.sql.ResultSet row, int n) throws java.sql.SQLException {
		return new Sheet(row.getLong("id"), row.getString("category"), row.getString("brand"), row.getString("reference"),
				row.getString("file_name"), row.getLong("size_bytes"), row.getString("uploaded_by"),
				row.getTimestamp("uploaded_at").toInstant());
	}

	private Sheet find(Long id) {
		return jdbc.query(SELECT + " WHERE equipment_sheet.id = ?", EquipmentService::sheet, id)
			.stream()
			.findFirst()
			.orElseThrow(EquipmentService::notFound);
	}

	private Path path(Long id) {
		return jdbc.queryForList("SELECT storage_path FROM equipment_sheet WHERE id = ?", String.class, id)
			.stream()
			.findFirst()
			.map(storage::resolve)
			.orElseThrow(EquipmentService::notFound);
	}

	private static String check(String category) {
		String value = clean(category);
		if (value == null || !CATEGORIES.contains(value)) {
			throw invalid("Catégorie de matériel inconnue : " + category);
		}
		return value;
	}

	private static String sha256(Path file) throws IOException {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String plain(String text) {
		return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
			.replace('_', ' ');
	}

	private static String clean(String text) {
		return text == null || text.isBlank() ? null : text.strip();
	}

	private static String cut(String text, int max) {
		return text == null || text.length() <= max ? text : text.substring(0, max);
	}

	private static ResponseStatusException invalid(String message) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
	}

	private static ResponseStatusException notFound() {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "Fiche technique introuvable.");
	}

}
