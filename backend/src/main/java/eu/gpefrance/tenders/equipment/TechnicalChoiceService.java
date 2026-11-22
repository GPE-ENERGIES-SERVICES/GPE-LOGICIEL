package eu.gpefrance.tenders.equipment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import eu.gpefrance.tenders.analysis.DceAnalysisDone;
import eu.gpefrance.tenders.analysis.ExtractionGrid;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The technical choices of GPE for a tender, between its analysis and its
 * estimate, for every kind of work: what the consultation file requires and
 * how GPE answers it, the questions to the buyer, the key quantities, the site;
 * for PV, the use of the energy, the roof and its planes, the production. The
 * analysis of the file proposes what it read (only where nothing is written,
 * with its source); a user always has the last word, and any change takes the
 * validation back. The sizing is computed by plain code.
 */
@Service
public class TechnicalChoiceService {

	private static final Logger log = LoggerFactory.getLogger(TechnicalChoiceService.class);

	private static final JsonMapper JSON = JsonMapper.builder().build();

	/** How the energy produced is used, as the choices store it and as the analysis reads it. */
	public static final Map<String, String> VALORISATIONS = valorisations();

	private static final Set<String> KINDS = Set.of("REQUIREMENT", "QUESTION", "QUANTITY", "PLANE");

	private static final Map<String, Set<String>> STATUSES = Map.of("REQUIREMENT",
			Set.of("TO_CHECK", "COMPLIANT", "NOT_COMPLIANT", "VARIANT"), "QUESTION", Set.of("TO_ASK", "ASKED", "ANSWERED"));

	/** Beyond this gap, in %, a power or a production is to look at. */
	private static final BigDecimal GAP_WARNING = BigDecimal.valueOf(5);

	private static final Pattern KWC = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*kWc", Pattern.CASE_INSENSITIVE);

	private static final Pattern NUMBER = Pattern.compile("(\\d+(?:[ \\u00a0]\\d{3})*(?:[.,]\\d+)?)");

	private static final Pattern LABELLED = Pattern.compile("^(.*?)\\s*:\\s*(\\d+(?:[ \\u00a0]\\d{3})*(?:[.,]\\d+)?)\\s*(.*)$");

	private final JdbcTemplate jdbc;

	private final EquipmentService equipment;

	private final Clock clock;

	public TechnicalChoiceService(JdbcTemplate jdbc, EquipmentService equipment, Clock clock) {
		this.jdbc = jdbc;
		this.equipment = equipment;
		this.clock = clock;
	}

	/** What a user chose; every value may be empty. */
	public record Choices(String valorisation, String roof, BigDecimal requestedKwc, BigDecimal yieldKwhKwc,
			BigDecimal productionMwh, String constraints, String siteAccess, String safety, String subcontracting,
			String planning, String notes) {
	}

	/** Where the AI read a value: the document, its page and the sentence. */
	public record Source(Long documentId, String fileName, Integer page, String quote) {
	}

	/**
	 * A requirement, a question, a key quantity or a roof plane.
	 * @param answer GPE's answer to a requirement, the buyer's to a question
	 * @param origin AI while a user did not change it, USER otherwise
	 */
	public record Line(Long id, String kind, String text, String answer, String status, BigDecimal value, String unit,
			Integer moduleCount, String orientation, BigDecimal tilt, String origin, Source source) {
	}

	/**
	 * @param modules the modules chosen, all lines together
	 * @param installedKwc modules × their power, null while a power or a quantity is missing
	 * @param gapPercent the installed power against the power asked, + above
	 * @param dcAcRatio the power of the modules against the one of the inverters
	 * @param expectedMwh the installed power × the yield
	 */
	public record Sizing(BigDecimal modules, BigDecimal installedKwc, BigDecimal gapPercent, BigDecimal inverterKva,
			BigDecimal dcAcRatio, Integer planeModules, BigDecimal expectedMwh, List<String> warnings) {
	}

	/** A power the consultation file names, and the sentence it comes from. */
	public record PowerHint(BigDecimal kwc, String source) {
	}

	/**
	 * @param saved false while nothing was entered nor proposed
	 * @param aiSources for each value proposed by the AI and not changed since, where it was read
	 * @param domains of the tender: PHOTOVOLTAIC shows what is proper to PV
	 */
	public record View(boolean saved, Choices choices, Map<String, Source> aiSources, List<EquipmentService.Chosen> equipment,
			List<Line> requirements, List<Line> questions, List<Line> quantities, List<Line> planes, Sizing sizing,
			String updatedBy, Instant updatedAt, String validatedBy, Instant validatedAt, Instant prefilledAt,
			List<PowerHint> powers, List<String> domains, boolean hasFile, boolean analysisDone) {
	}

	public View view(Long tenderId) {
		checkTender(tenderId);
		List<String> domains = jdbc.queryForObject("SELECT activities FROM tender WHERE id = ?",
				(row, n) -> List.of((String[]) row.getArray("activities").getArray()), tenderId);
		boolean hasFile = jdbc.queryForObject("SELECT count(*) FROM dossier WHERE tender_id = ?", Long.class, tenderId) > 0;
		boolean analysisDone = latestAnalysis(tenderId) != null;
		List<EquipmentService.Chosen> chosen = equipment.chosen(tenderId);
		List<Line> lines = lines(tenderId);
		List<Line> planes = lines.stream().filter((line) -> line.kind().equals("PLANE")).toList();
		Optional<Object[]> row = jdbc.query("SELECT * FROM technical_choice WHERE tender_id = ?", (result, n) -> new Object[] {
				choices(result), sources(result.getString("ai_sources")), result.getString("updated_by"),
				instant(result, "updated_at"), result.getString("validated_by"), instant(result, "validated_at"),
				instant(result, "prefilled_at") }, tenderId).stream().findFirst();
		Choices choices = row.map((values) -> (Choices) values[0]).orElse(EMPTY);
		@SuppressWarnings("unchecked")
		Map<String, Source> aiSources = row.map((values) -> (Map<String, Source>) values[1]).orElse(Map.of());
		return new View(row.isPresent(), choices, aiSources, chosen, of(lines, "REQUIREMENT"), of(lines, "QUESTION"),
				of(lines, "QUANTITY"), planes, sizing(choices, chosen, planes), (String) row.map((v) -> v[2]).orElse(null),
				(Instant) row.map((v) -> v[3]).orElse(null), (String) row.map((v) -> v[4]).orElse(null),
				(Instant) row.map((v) -> v[5]).orElse(null), (Instant) row.map((v) -> v[6]).orElse(null),
				powers(tenderId), domains, hasFile, analysisDone);
	}

	private static final Choices EMPTY = new Choices(null, null, null, null, null, null, null, null, null, null, null);

	/** Keeps the choices; a value changed loses its AI source; any change takes the validation back. */
	@Transactional
	public View save(Long tenderId, Choices choices, String user) {
		checkTender(tenderId);
		String valorisation = clean(choices.valorisation());
		if (valorisation != null && !VALORISATIONS.containsKey(valorisation)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mode de valorisation inconnu : " + valorisation);
		}
		Choices cleaned = new Choices(valorisation, clean(choices.roof()), choices.requestedKwc(), choices.yieldKwhKwc(),
				choices.productionMwh(), clean(choices.constraints()), clean(choices.siteAccess()), clean(choices.safety()),
				clean(choices.subcontracting()), clean(choices.planning()), clean(choices.notes()));
		View before = view(tenderId);
		ObjectNode kept = JSON.createObjectNode();
		Map<String, Object> was = values(before.choices());
		Map<String, Object> now = values(cleaned);
		before.aiSources().forEach((field, source) -> {
			if (same(was.get(field), now.get(field))) {
				kept.set(field, JSON.valueToTree(source));
			}
		});
		ensureRow(tenderId, user);
		jdbc.update("""
				UPDATE technical_choice SET valorisation = ?, roof = ?, requested_kwc = ?, yield_kwh_kwc = ?,
				    production_mwh = ?, constraints = ?, site_access = ?, safety = ?, subcontracting = ?, planning = ?,
				    notes = ?, ai_sources = CAST(? AS jsonb), updated_by = ?, updated_at = ?, validated_by = NULL,
				    validated_at = NULL
				WHERE tender_id = ?""", cleaned.valorisation(), cleaned.roof(), cleaned.requestedKwc(),
				cleaned.yieldKwhKwc(), cleaned.productionMwh(), cleaned.constraints(), cleaned.siteAccess(),
				cleaned.safety(), cleaned.subcontracting(), cleaned.planning(), cleaned.notes(), kept.toString(), user,
				Timestamp.from(clock.instant()), tenderId);
		return view(tenderId);
	}

	@Transactional
	public View validate(Long tenderId, String user) {
		if (jdbc.update("UPDATE technical_choice SET validated_by = ?, validated_at = ? WHERE tender_id = ?", user,
				Timestamp.from(clock.instant()), tenderId) == 0) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Renseignez d'abord les choix techniques.");
		}
		return view(tenderId);
	}

	// Lines

	/** What a user writes on a line; only what its kind uses is kept. */
	public record LineRequest(String text, String answer, String status, BigDecimal value, String unit, Integer moduleCount,
			String orientation, BigDecimal tilt) {
	}

	@Transactional
	public View addLine(Long tenderId, String kind, LineRequest request, String user) {
		checkTender(tenderId);
		checkKind(kind);
		String text = required(request.text());
		Integer position = jdbc.queryForObject(
				"SELECT coalesce(max(position), 0) + 1 FROM technical_line WHERE tender_id = ? AND kind = ?", Integer.class,
				tenderId, kind);
		jdbc.update("""
				INSERT INTO technical_line (tender_id, kind, position, text, answer, status, value, unit, module_count,
				    orientation, tilt, origin, updated_by, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'USER', ?, ?)""", tenderId, kind, position, text,
				clean(request.answer()), status(kind, request.status()), request.value(), cut(clean(request.unit()), 20),
				request.moduleCount(), cut(clean(request.orientation()), 60), request.tilt(), user,
				Timestamp.from(clock.instant()));
		changed(tenderId, user);
		return view(tenderId);
	}

	/** A line changed by a user is his: it is no more marked as proposed by the AI, its source stays. */
	@Transactional
	public View updateLine(Long tenderId, Long lineId, LineRequest request, String user) {
		String kind = kindOf(tenderId, lineId);
		jdbc.update("""
				UPDATE technical_line SET text = ?, answer = ?, status = ?, value = ?, unit = ?, module_count = ?,
				    orientation = ?, tilt = ?, origin = 'USER', updated_by = ?, updated_at = ?
				WHERE id = ?""", required(request.text()), clean(request.answer()), status(kind, request.status()),
				request.value(), cut(clean(request.unit()), 20), request.moduleCount(), cut(clean(request.orientation()), 60),
				request.tilt(), user, Timestamp.from(clock.instant()), lineId);
		changed(tenderId, user);
		return view(tenderId);
	}

	@Transactional
	public View deleteLine(Long tenderId, Long lineId, String user) {
		kindOf(tenderId, lineId);
		jdbc.update("DELETE FROM technical_line WHERE id = ?", lineId);
		changed(tenderId, user);
		return view(tenderId);
	}

	/** How many of an equipment chosen and how powerful; a change takes the validation back. */
	@Transactional
	public View detailEquipment(Long tenderId, Long sheetId, BigDecimal quantity, BigDecimal unitPower, String note,
			String user) {
		equipment.detail(tenderId, sheetId, quantity, unitPower, note);
		changed(tenderId, user);
		return view(tenderId);
	}

	// What the analysis of the file read

	/** Once an analysis of the file is over, the technical choices start from what it read. */
	@EventListener
	public void onAnalysisDone(DceAnalysisDone done) {
		try {
			prefill(done.tenderId(), "Analyse IA");
		}
		catch (RuntimeException ex) {
			// The analysis stays done: the choices can be filled again from the page.
			log.warn("Technical choices of tender {} not prefilled: {}", done.tenderId(), ex.getClass().getSimpleName());
		}
	}

	private record Read(String key, String value, Long documentId, String fileName, Integer page, String quote) {

		Source source() {
			return new Source(documentId, fileName, page, quote);
		}

	}

	/**
	 * Fills, from the latest analysis of the file, what is still empty: the
	 * values, the requirements, the questions, the quantities and the planes
	 * not already listed. Nothing written by a user is changed.
	 * @return how many values and lines were added
	 */
	@Transactional
	public int prefill(Long tenderId, String user) {
		checkTender(tenderId);
		Long analysisId = latestAnalysis(tenderId);
		if (analysisId == null) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Aucune analyse du DCE terminée : lancez-la, ou renseignez les choix à la main.");
		}
		List<Read> read = jdbc.query("""
				SELECT f.field_key, coalesce(f.corrected_value, f.value) AS value, f.document_id, d.file_name,
				       f.page_number, f.quote
				FROM extracted_field f LEFT JOIN document d ON d.id = f.document_id
				WHERE f.analysis_id = ? AND f.field_key LIKE 'tech\\_%' AND coalesce(f.corrected_value, f.value) IS NOT NULL
				  AND coalesce(f.decision, '') <> 'REJECTED'
				ORDER BY f.id""",
				(row, n) -> new Read(row.getString("field_key"), row.getString("value"), (Long) row.getObject("document_id"),
						row.getString("file_name"), (Integer) row.getObject("page_number"), row.getString("quote")),
				analysisId);
		ensureRow(tenderId, user);
		View before = view(tenderId);
		Choices was = before.choices();
		ObjectNode sources = JSON.valueToTree(before.aiSources());
		int added = 0;
		// The values of the choices, only where they are empty.
		Optional<Read> valorisation = first(read, ExtractionGrid.TECH_VALORISATION);
		String valorisationCode = valorisation.map((value) -> VALORISATION_BY_LABEL.get(value.value())).orElse(null);
		if (was.valorisation() == null && valorisationCode != null) {
			jdbc.update("UPDATE technical_choice SET valorisation = ? WHERE tender_id = ?", valorisationCode, tenderId);
			sources.set("valorisation", JSON.valueToTree(valorisation.get().source()));
			added++;
		}
		Optional<Read> roof = first(read, ExtractionGrid.TECH_ROOF);
		if (was.roof() == null && roof.isPresent()) {
			jdbc.update("UPDATE technical_choice SET roof = ? WHERE tender_id = ?", roof.get().value(), tenderId);
			sources.set("roof", JSON.valueToTree(roof.get().source()));
			added++;
		}
		Map<BigDecimal, Read> powers = new LinkedHashMap<>();
		for (Read power : all(read, ExtractionGrid.TECH_POWER)) {
			Matcher matcher = KWC.matcher(power.value());
			if (matcher.find()) {
				powers.putIfAbsent(decimal(matcher.group(1)).stripTrailingZeros(), power);
			}
		}
		if (was.requestedKwc() == null && powers.size() == 1) {
			Map.Entry<BigDecimal, Read> power = powers.entrySet().iterator().next();
			jdbc.update("UPDATE technical_choice SET requested_kwc = ? WHERE tender_id = ?", power.getKey(), tenderId);
			sources.set("requestedKwc", JSON.valueToTree(power.getValue().source()));
			added++;
		}
		added += number(was.yieldKwhKwc(), first(read, ExtractionGrid.TECH_YIELD), "yield_kwh_kwc", "yieldKwhKwc", tenderId,
				sources);
		added += number(was.productionMwh(), first(read, ExtractionGrid.TECH_PRODUCTION), "production_mwh", "productionMwh",
				tenderId, sources);
		List<Read> site = all(read, ExtractionGrid.TECH_SITE);
		if (was.constraints() == null && !site.isEmpty()) {
			jdbc.update("UPDATE technical_choice SET constraints = ? WHERE tender_id = ?", bullets(site), tenderId);
			sources.set("constraints", JSON.valueToTree(site.getFirst().source()));
			added++;
		}
		List<Read> designed = all(read, ExtractionGrid.TECH_DESIGNER_EQUIPMENT);
		if (was.notes() == null && !designed.isEmpty()) {
			jdbc.update("UPDATE technical_choice SET notes = ? WHERE tender_id = ?",
					"Matériel étudié par la maîtrise d'œuvre :\n" + bullets(designed), tenderId);
			sources.set("notes", JSON.valueToTree(designed.getFirst().source()));
			added++;
		}
		// The lines not already listed.
		List<Line> lines = lines(tenderId);
		for (Read requirement : all(read, ExtractionGrid.TECH_REQUIREMENTS)) {
			added += addRead(tenderId, "REQUIREMENT", requirement, requirement.value(), null, null, null, lines, user);
		}
		for (Read question : all(read, ExtractionGrid.TECH_INCONSISTENCIES)) {
			added += addRead(tenderId, "QUESTION", question, question.value(), null, null, null, lines, user);
		}
		if (powers.size() > 1 && all(read, ExtractionGrid.TECH_INCONSISTENCIES).isEmpty()) {
			String text = "Puissance de la centrale : " + String.join(" ou ",
					powers.keySet().stream().map((kwc) -> kwc.toPlainString().replace('.', ',') + " kWc").toList()) + " ?";
			added += addRead(tenderId, "QUESTION", powers.values().iterator().next(), text, null, null, null, lines, user);
		}
		for (Read quantity : all(read, ExtractionGrid.TECH_QUANTITIES)) {
			Matcher labelled = LABELLED.matcher(quantity.value().strip());
			added += labelled.matches()
					? addRead(tenderId, "QUANTITY", quantity, labelled.group(1), decimal(labelled.group(2)),
							cut(clean(labelled.group(3)), 20), null, lines, user)
					: addRead(tenderId, "QUANTITY", quantity, quantity.value(), null, null, null, lines, user);
		}
		for (Read plane : all(read, ExtractionGrid.TECH_PLANES)) {
			added += addRead(tenderId, "PLANE", plane, plane.value().split(":")[0].strip(), null, null, plane, lines, user);
		}
		jdbc.update("""
				UPDATE technical_choice SET ai_sources = CAST(? AS jsonb), prefilled_at = ? WHERE tender_id = ?""",
				sources.toString(), Timestamp.from(clock.instant()), tenderId);
		if (added > 0) {
			changed(tenderId, user);
		}
		log.info("Technical choices of tender {}: {} values and lines proposed from analysis {}", tenderId, added, analysisId);
		return added;
	}

	private int number(BigDecimal current, Optional<Read> read, String column, String field, Long tenderId,
			ObjectNode sources) {
		if (current != null || read.isEmpty()) {
			return 0;
		}
		Matcher matcher = NUMBER.matcher(read.get().value());
		if (!matcher.find()) {
			return 0;
		}
		jdbc.update("UPDATE technical_choice SET " + column + " = ? WHERE tender_id = ?", decimal(matcher.group(1)), tenderId);
		sources.set(field, JSON.valueToTree(read.get().source()));
		return 1;
	}

	private static final Pattern PLANE_MODULES = Pattern.compile("(\\d+)\\s*modules?", Pattern.CASE_INSENSITIVE);

	private static final Pattern PLANE_ORIENTATION = Pattern.compile("orient\\p{L}*\\s+(?:plein\\s+)?([\\p{L}-]+(?:[ -][\\p{L}]+)?)",
			Pattern.CASE_INSENSITIVE);

	private static final Pattern PLANE_TILT = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*°");

	/** A line read by the AI, unless one of the same text is already listed. */
	private int addRead(Long tenderId, String kind, Read read, String text, BigDecimal value, String unit, Read plane,
			List<Line> lines, String user) {
		String plain = plain(text);
		if (plain.isBlank() || lines.stream().anyMatch((line) -> line.kind().equals(kind) && plain(line.text()).equals(plain))) {
			return 0;
		}
		Integer modules = null;
		String orientation = null;
		BigDecimal tilt = null;
		if (plane != null) {
			Matcher found = PLANE_MODULES.matcher(plane.value());
			modules = found.find() ? Integer.valueOf(found.group(1)) : null;
			found = PLANE_ORIENTATION.matcher(plane.value());
			orientation = found.find() ? cut(found.group(1), 60) : null;
			found = PLANE_TILT.matcher(plane.value());
			tilt = found.find() ? decimal(found.group(1)) : null;
		}
		Integer position = jdbc.queryForObject(
				"SELECT coalesce(max(position), 0) + 1 FROM technical_line WHERE tender_id = ? AND kind = ?", Integer.class,
				tenderId, kind);
		jdbc.update("""
				INSERT INTO technical_line (tender_id, kind, position, text, status, value, unit, module_count, orientation,
				    tilt, origin, document_id, page, quote, updated_by, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'AI', ?, ?, ?, ?, ?)""", tenderId, kind, position, text.strip(),
				STATUSES.containsKey(kind) ? (kind.equals("QUESTION") ? "TO_ASK" : "TO_CHECK") : null, value, unit, modules,
				orientation, tilt, read.documentId(), read.page(), read.quote(), user, Timestamp.from(clock.instant()));
		lines.add(new Line(null, kind, text, null, null, null, null, null, null, null, "AI", null));
		return 1;
	}

	// For the memoir

	/** The choices said in words, for the AI writing the memoir; empty while nothing is entered. */
	public List<String> describe(Long tenderId) {
		if (tenderId == null) {
			return List.of();
		}
		View view = view(tenderId);
		if (!view.saved() && view.equipment().isEmpty()) {
			return List.of();
		}
		Choices choices = view.choices();
		List<String> lines = new ArrayList<>();
		if (choices.valorisation() != null) {
			lines.add("Valorisation de l'énergie : " + VALORISATIONS.get(choices.valorisation()));
		}
		add(lines, "Toiture et support", choices.roof());
		if (view.sizing().installedKwc() != null) {
			lines.add("Puissance installée : " + french(view.sizing().installedKwc()) + " kWc ("
					+ french(view.sizing().modules()) + " modules)");
		}
		for (EquipmentService.Chosen item : view.equipment()) {
			if (item.quantity() != null) {
				lines.add("Quantité : " + french(item.quantity()) + " × " + (item.sheet().brand() == null ? ""
						: item.sheet().brand() + " ") + item.sheet().reference());
			}
		}
		for (Line plane : view.planes()) {
			lines.add("Pan « " + plane.text() + " » : " + (plane.moduleCount() == null ? "" : plane.moduleCount() + " modules")
					+ (plane.orientation() == null ? "" : ", orientation " + plane.orientation())
					+ (plane.tilt() == null ? "" : ", inclinaison " + french(plane.tilt()) + "°"));
		}
		if (choices.yieldKwhKwc() != null) {
			lines.add("Productible : " + french(choices.yieldKwhKwc()) + " kWh/kWc/an");
		}
		if (choices.productionMwh() != null) {
			lines.add("Production annuelle : " + french(choices.productionMwh()) + " MWh/an");
		}
		for (Line requirement : view.requirements()) {
			if (requirement.answer() != null || "COMPLIANT".equals(requirement.status())) {
				lines.add("Exigence « " + requirement.text() + " » : " + (requirement.answer() != null ? requirement.answer()
						: "respectée"));
			}
		}
		for (Line quantity : view.quantities()) {
			lines.add("Quantité clé : " + quantity.text() + (quantity.value() == null ? ""
					: " : " + french(quantity.value()) + (quantity.unit() == null ? "" : " " + quantity.unit())));
		}
		add(lines, "Contraintes du projet", choices.constraints());
		add(lines, "Site et accès", choices.siteAccess());
		add(lines, "Sécurité et levage", choices.safety());
		add(lines, "Sous-traitance", choices.subcontracting());
		add(lines, "Hypothèses de planning", choices.planning());
		add(lines, "Choix et remarques de GPE", choices.notes());
		return lines;
	}

	private static void add(List<String> lines, String label, String value) {
		if (value != null) {
			lines.add(label + " : " + value);
		}
	}

	// Plain code

	static Sizing sizing(Choices choices, List<EquipmentService.Chosen> equipment, List<Line> planes) {
		List<String> warnings = new ArrayList<>();
		List<EquipmentService.Chosen> modules = equipment.stream().filter((item) -> item.sheet().category().equals("MODULE")).toList();
		List<EquipmentService.Chosen> inverters = equipment.stream()
			.filter((item) -> item.sheet().category().equals("INVERTER"))
			.toList();
		BigDecimal moduleCount = sum(modules, false);
		BigDecimal installed = modules.isEmpty() || modules.stream().anyMatch((item) -> item.quantity() == null || item.unitPower() == null)
				? null : sum(modules, true).divide(BigDecimal.valueOf(1000), 3, RoundingMode.HALF_UP);
		if (!modules.isEmpty() && installed == null) {
			warnings.add("Renseignez la quantité et la puissance (Wc) de chaque module retenu pour calculer la puissance installée.");
		}
		BigDecimal gap = null;
		if (installed != null && choices.requestedKwc() != null) {
			gap = percent(installed, choices.requestedKwc());
			if (gap.abs().compareTo(GAP_WARNING) > 0) {
				warnings.add("La puissance installée (" + french(installed) + " kWc) s'écarte de " + french(gap)
						+ " % de la puissance demandée (" + french(choices.requestedKwc()) + " kWc).");
			}
		}
		BigDecimal inverterKva = inverters.isEmpty()
				|| inverters.stream().anyMatch((item) -> item.quantity() == null || item.unitPower() == null) ? null
						: sum(inverters, true);
		if (!inverters.isEmpty() && inverterKva == null) {
			warnings.add("Renseignez la quantité et la puissance (kVA) de chaque onduleur retenu pour calculer le ratio DC/AC.");
		}
		BigDecimal ratio = installed == null || inverterKva == null || inverterKva.signum() == 0 ? null
				: installed.divide(inverterKva, 2, RoundingMode.HALF_UP);
		if (ratio != null && ratio.compareTo(BigDecimal.valueOf(1.3)) > 0) {
			warnings.add("Ratio DC/AC de " + french(ratio) + " : onduleurs sous-dimensionnés pour cette puissance de modules.");
		}
		if (ratio != null && ratio.compareTo(BigDecimal.valueOf(0.9)) < 0) {
			warnings.add("Ratio DC/AC de " + french(ratio) + " : onduleurs surdimensionnés.");
		}
		Integer planeModules = planes.isEmpty() || planes.stream().anyMatch((plane) -> plane.moduleCount() == null) ? null
				: planes.stream().mapToInt(Line::moduleCount).sum();
		if (planeModules != null && moduleCount.signum() > 0 && moduleCount.compareTo(BigDecimal.valueOf(planeModules)) != 0) {
			warnings.add("Les pans comptent " + planeModules + " modules, le matériel retenu " + french(moduleCount) + ".");
		}
		BigDecimal expected = installed == null || choices.yieldKwhKwc() == null ? null
				: installed.multiply(choices.yieldKwhKwc()).divide(BigDecimal.valueOf(1000), 1, RoundingMode.HALF_UP);
		if (expected != null && choices.productionMwh() != null
				&& percent(expected, choices.productionMwh()).abs().compareTo(GAP_WARNING) > 0) {
			warnings.add("Puissance × productible donne " + french(expected) + " MWh/an, l'étude annonce "
					+ french(choices.productionMwh()) + " MWh/an.");
		}
		return new Sizing(moduleCount, installed, gap, inverterKva, ratio, planeModules, expected, warnings);
	}

	private static BigDecimal sum(List<EquipmentService.Chosen> items, boolean power) {
		return items.stream()
			.filter((item) -> item.quantity() != null && (!power || item.unitPower() != null))
			.map((item) -> power ? item.quantity().multiply(item.unitPower()) : item.quantity())
			.reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private static BigDecimal percent(BigDecimal value, BigDecimal reference) {
		return value.subtract(reference).multiply(BigDecimal.valueOf(100)).divide(reference, 1, RoundingMode.HALF_UP);
	}

	/** The powers named by the latest analysis of the file, each once, with its sentence. */
	private List<PowerHint> powers(Long tenderId) {
		Long analysisId = latestAnalysis(tenderId);
		if (analysisId == null) {
			return List.of();
		}
		Map<BigDecimal, String> found = new LinkedHashMap<>();
		for (String value : jdbc.queryForList("""
				SELECT coalesce(corrected_value, value) FROM extracted_field
				WHERE analysis_id = ? AND coalesce(corrected_value, value) ILIKE '%kWc%' ORDER BY id""", String.class,
				analysisId)) {
			Matcher matcher = KWC.matcher(value);
			while (matcher.find()) {
				found.putIfAbsent(decimal(matcher.group(1)).stripTrailingZeros(),
						value.length() <= 200 ? value : value.substring(0, 200) + "…");
			}
		}
		return found.entrySet().stream().map((entry) -> new PowerHint(entry.getKey(), entry.getValue())).toList();
	}

	// Rows

	private Long latestAnalysis(Long tenderId) {
		return jdbc.queryForList("""
				SELECT max(a.id) FROM analysis a JOIN dossier d ON d.id = a.dossier_id
				WHERE d.tender_id = ? AND a.kind = 'DCE_SUMMARY' AND a.status = 'DONE'""", Long.class, tenderId)
			.stream()
			.filter(Objects::nonNull)
			.findFirst()
			.orElse(null);
	}

	private List<Line> lines(Long tenderId) {
		return new ArrayList<>(jdbc.query("""
				SELECT l.*, d.file_name FROM technical_line l LEFT JOIN document d ON d.id = l.document_id
				WHERE l.tender_id = ? ORDER BY l.kind, l.position, l.id""",
				(row, n) -> new Line(row.getLong("id"), row.getString("kind"), row.getString("text"), row.getString("answer"),
						row.getString("status"), row.getBigDecimal("value"), row.getString("unit"),
						(Integer) row.getObject("module_count"), row.getString("orientation"), row.getBigDecimal("tilt"),
						row.getString("origin"),
						row.getObject("document_id") == null && row.getString("quote") == null ? null
								: new Source((Long) row.getObject("document_id"), row.getString("file_name"),
										(Integer) row.getObject("page"), row.getString("quote"))),
				tenderId));
	}

	private static List<Line> of(List<Line> lines, String kind) {
		return lines.stream().filter((line) -> line.kind().equals(kind)).toList();
	}

	private static List<Read> all(List<Read> read, String key) {
		return read.stream().filter((value) -> value.key().equals(key)).toList();
	}

	private static Optional<Read> first(List<Read> read, String key) {
		return read.stream().filter((value) -> value.key().equals(key)).findFirst();
	}

	private static Choices choices(ResultSet row) throws SQLException {
		return new Choices(row.getString("valorisation"), row.getString("roof"), row.getBigDecimal("requested_kwc"),
				row.getBigDecimal("yield_kwh_kwc"), row.getBigDecimal("production_mwh"), row.getString("constraints"),
				row.getString("site_access"), row.getString("safety"), row.getString("subcontracting"),
				row.getString("planning"), row.getString("notes"));
	}

	private static Map<String, Source> sources(String json) {
		Map<String, Source> sources = new LinkedHashMap<>();
		JsonNode root = JSON.readTree(json == null ? "{}" : json);
		root.properties().forEach((entry) -> sources.put(entry.getKey(), JSON.treeToValue(entry.getValue(), Source.class)));
		return sources;
	}

	/** The values of the choices by the name the sources use. */
	private static Map<String, Object> values(Choices choices) {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("valorisation", choices.valorisation());
		values.put("roof", choices.roof());
		values.put("requestedKwc", choices.requestedKwc());
		values.put("yieldKwhKwc", choices.yieldKwhKwc());
		values.put("productionMwh", choices.productionMwh());
		values.put("constraints", choices.constraints());
		values.put("notes", choices.notes());
		return values;
	}

	private static boolean same(Object a, Object b) {
		if (a instanceof BigDecimal x && b instanceof BigDecimal y) {
			return x.compareTo(y) == 0;
		}
		return Objects.equals(a, b);
	}

	private void ensureRow(Long tenderId, String user) {
		jdbc.update("""
				INSERT INTO technical_choice (tender_id, updated_by, updated_at) VALUES (?, ?, ?)
				ON CONFLICT (tender_id) DO NOTHING""", tenderId, user, Timestamp.from(clock.instant()));
	}

	/** Something changed: who and when, and the validation is to give again. */
	private void changed(Long tenderId, String user) {
		ensureRow(tenderId, user);
		jdbc.update("""
				UPDATE technical_choice SET updated_by = ?, updated_at = ?, validated_by = NULL, validated_at = NULL
				WHERE tender_id = ?""", user, Timestamp.from(clock.instant()), tenderId);
	}

	private String kindOf(Long tenderId, Long lineId) {
		return jdbc.queryForList("SELECT kind FROM technical_line WHERE id = ? AND tender_id = ?", String.class, lineId, tenderId)
			.stream()
			.findFirst()
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ligne introuvable."));
	}

	private void checkTender(Long tenderId) {
		if (jdbc.queryForObject("SELECT count(*) FROM tender WHERE id = ?", Long.class, tenderId) == 0) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Appel d'offres introuvable.");
		}
	}

	private static void checkKind(String kind) {
		if (!KINDS.contains(kind)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Type de ligne inconnu : " + kind);
		}
	}

	private static String status(String kind, String status) {
		Set<String> allowed = STATUSES.get(kind);
		if (allowed == null) {
			return null;
		}
		String value = clean(status);
		if (value == null) {
			return kind.equals("QUESTION") ? "TO_ASK" : "TO_CHECK";
		}
		if (!allowed.contains(value)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "État inconnu : " + status);
		}
		return value;
	}

	private static String required(String text) {
		String value = clean(text);
		if (value == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Écrivez le texte de la ligne.");
		}
		return value;
	}

	private static Instant instant(ResultSet row, String column) throws SQLException {
		Timestamp value = row.getTimestamp(column);
		return value == null ? null : value.toInstant();
	}

	private static String bullets(List<Read> read) {
		return String.join("\n", read.stream().map((value) -> "- " + value.value()).toList());
	}

	static BigDecimal decimal(String text) {
		return new BigDecimal(text.replace(" ", "").replace(" ", "").replace(',', '.'));
	}

	private static String french(BigDecimal value) {
		return value.stripTrailingZeros().toPlainString().replace('.', ',');
	}

	private static String plain(String text) {
		return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD)
			.replaceAll("\\p{M}", "")
			.toLowerCase(Locale.ROOT)
			.replaceAll("[^a-z0-9]+", " ")
			.strip();
	}

	private static String clean(String text) {
		return text == null || text.isBlank() ? null : text.strip();
	}

	private static String cut(String text, int max) {
		return text == null || text.length() <= max ? text : text.substring(0, max);
	}

	private static final Map<String, String> VALORISATION_BY_LABEL = Map.of(ExtractionGrid.VALORISATION_SELF_TOTAL,
			"SELF_TOTAL", ExtractionGrid.VALORISATION_SELF_SURPLUS, "SELF_SURPLUS", ExtractionGrid.VALORISATION_RESALE_TOTAL,
			"RESALE_TOTAL");

	private static Map<String, String> valorisations() {
		Map<String, String> labels = new LinkedHashMap<>();
		labels.put("SELF_TOTAL", "autoconsommation totale");
		labels.put("SELF_SURPLUS", "autoconsommation avec revente du surplus");
		labels.put("RESALE_TOTAL", "revente totale");
		return labels;
	}

}
