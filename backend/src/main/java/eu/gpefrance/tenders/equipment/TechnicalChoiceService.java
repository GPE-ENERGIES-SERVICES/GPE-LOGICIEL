package eu.gpefrance.tenders.equipment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The technical choices of GPE for a tender, between its analysis and its
 * estimate. Entered and changed by hand, with or without a consultation file:
 * what the file and the equipment say is proposed, never written alone. The
 * sizing is computed by plain code.
 */
@Service
public class TechnicalChoiceService {

	/** How the energy produced is used. */
	public static final Map<String, String> VALORISATIONS = valorisations();

	/** Beyond this gap between the power installed and the power asked, the sizing is to look at. */
	private static final BigDecimal GAP_WARNING = BigDecimal.valueOf(5);

	private static final Pattern KWC = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*kWc", Pattern.CASE_INSENSITIVE);

	private static final Pattern WATTS = Pattern.compile("(?<!\\d)([2-7]\\d{2})\\s*Wc?\\b", Pattern.CASE_INSENSITIVE);

	private static final Pattern THREE_DIGITS = Pattern.compile("(?<!\\d)([2-7]\\d{2})(?!\\d)");

	private final JdbcTemplate jdbc;

	private final EquipmentService equipment;

	private final Clock clock;

	public TechnicalChoiceService(JdbcTemplate jdbc, EquipmentService equipment, Clock clock) {
		this.jdbc = jdbc;
		this.equipment = equipment;
		this.clock = clock;
	}

	/** What a user chose; every value may be empty. */
	public record Choices(String valorisation, String roof, BigDecimal requestedKwc, Integer moduleCount, BigDecimal moduleWc,
			Integer inverterCount, BigDecimal inverterKva, String constraints, String openPoints, String notes) {
	}

	/**
	 * @param installedKwc modules × their power
	 * @param gapPercent the installed power against the power asked, + above
	 * @param dcAcRatio the power of the modules against the one of the inverters
	 * @param warnings what to look at, in words
	 */
	public record Sizing(BigDecimal installedKwc, BigDecimal gapPercent, BigDecimal inverterKva, BigDecimal dcAcRatio,
			List<String> warnings) {
	}

	/** A power the consultation file names, and the sentence it comes from. */
	public record PowerHint(BigDecimal kwc, String source) {
	}

	/**
	 * @param saved false while nothing was entered
	 * @param powers the powers the file names: several when it contradicts itself
	 * @param moduleWc the power of the module chosen, read in its reference, or null
	 * @param mountings the mounting systems chosen
	 * @param hasFile whether the tender has a consultation file read
	 */
	public record View(boolean saved, Choices choices, Sizing sizing, String updatedBy, Instant updatedAt,
			String validatedBy, Instant validatedAt, List<PowerHint> powers, BigDecimal moduleWc, List<String> mountings,
			boolean hasFile) {
	}

	public View view(Long tenderId) {
		checkTender(tenderId);
		List<EquipmentService.Sheet> chosen = equipment.ofTender(tenderId);
		BigDecimal moduleWc = chosen.stream()
			.filter((sheet) -> sheet.category().equals("MODULE"))
			.map((sheet) -> watts(sheet.reference()))
			.filter(java.util.Objects::nonNull)
			.findFirst()
			.orElse(null);
		List<String> mountings = chosen.stream()
			.filter((sheet) -> sheet.category().equals("MOUNTING"))
			.map((sheet) -> (sheet.brand() == null ? "" : sheet.brand() + " ") + sheet.reference())
			.toList();
		boolean hasFile = jdbc.queryForObject("SELECT count(*) FROM dossier WHERE tender_id = ?", Long.class, tenderId) > 0;
		Optional<View> saved = jdbc.query("SELECT * FROM technical_choice WHERE tender_id = ?", (row, n) -> {
			Choices choices = new Choices(row.getString("valorisation"), row.getString("roof"),
					row.getBigDecimal("requested_kwc"), (Integer) row.getObject("module_count"), row.getBigDecimal("module_wc"),
					(Integer) row.getObject("inverter_count"), row.getBigDecimal("inverter_kva"), row.getString("constraints"),
					row.getString("open_points"), row.getString("notes"));
			return new View(true, choices, sizing(choices), row.getString("updated_by"),
					row.getTimestamp("updated_at").toInstant(), row.getString("validated_by"),
					row.getTimestamp("validated_at") == null ? null : row.getTimestamp("validated_at").toInstant(),
					powers(tenderId), moduleWc, mountings, hasFile);
		}, tenderId).stream().findFirst();
		Choices empty = new Choices(null, null, null, null, null, null, null, null, null, null);
		return saved.orElseGet(() -> new View(false, empty, sizing(empty), null, null, null, null, powers(tenderId), moduleWc,
				mountings, hasFile));
	}

	/** Keeps the choices; a change after the validation takes it back. */
	@Transactional
	public View save(Long tenderId, Choices choices, String user) {
		checkTender(tenderId);
		String valorisation = clean(choices.valorisation());
		if (valorisation != null && !VALORISATIONS.containsKey(valorisation)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mode de valorisation inconnu : " + valorisation);
		}
		jdbc.update("""
				INSERT INTO technical_choice (tender_id, valorisation, roof, requested_kwc, module_count, module_wc,
				    inverter_count, inverter_kva, constraints, open_points, notes, updated_by, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT (tender_id) DO UPDATE SET valorisation = EXCLUDED.valorisation, roof = EXCLUDED.roof,
				    requested_kwc = EXCLUDED.requested_kwc, module_count = EXCLUDED.module_count,
				    module_wc = EXCLUDED.module_wc, inverter_count = EXCLUDED.inverter_count,
				    inverter_kva = EXCLUDED.inverter_kva, constraints = EXCLUDED.constraints,
				    open_points = EXCLUDED.open_points, notes = EXCLUDED.notes, updated_by = EXCLUDED.updated_by,
				    updated_at = EXCLUDED.updated_at, validated_by = NULL, validated_at = NULL""", tenderId, valorisation,
				clean(choices.roof()), choices.requestedKwc(), choices.moduleCount(), choices.moduleWc(),
				choices.inverterCount(), choices.inverterKva(), clean(choices.constraints()), clean(choices.openPoints()),
				clean(choices.notes()), user, Timestamp.from(clock.instant()));
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

	/** The choices said in words, for the AI writing the memoir; empty while nothing is entered. */
	public List<String> describe(Long tenderId) {
		if (tenderId == null) {
			return List.of();
		}
		View view = view(tenderId);
		if (!view.saved()) {
			return List.of();
		}
		Choices choices = view.choices();
		List<String> lines = new ArrayList<>();
		if (choices.valorisation() != null) {
			lines.add("Valorisation de l'énergie : " + VALORISATIONS.get(choices.valorisation()));
		}
		if (choices.roof() != null) {
			lines.add("Toiture et support : " + choices.roof());
		}
		if (view.sizing().installedKwc() != null) {
			lines.add("Puissance installée : " + view.sizing().installedKwc().toPlainString() + " kWc (" + choices.moduleCount()
					+ " modules de " + choices.moduleWc().stripTrailingZeros().toPlainString() + " Wc)");
		}
		if (choices.inverterCount() != null && choices.inverterKva() != null) {
			lines.add("Onduleurs : " + choices.inverterCount() + " × " + choices.inverterKva().stripTrailingZeros().toPlainString()
					+ " kVA");
		}
		if (choices.constraints() != null) {
			lines.add("Contraintes du projet : " + choices.constraints());
		}
		if (choices.notes() != null) {
			lines.add("Choix et remarques de GPE : " + choices.notes());
		}
		return lines;
	}

	// Plain code

	static Sizing sizing(Choices choices) {
		List<String> warnings = new ArrayList<>();
		BigDecimal installed = choices.moduleCount() == null || choices.moduleWc() == null ? null
				: choices.moduleWc()
					.multiply(BigDecimal.valueOf(choices.moduleCount()))
					.divide(BigDecimal.valueOf(1000), 3, RoundingMode.HALF_UP);
		BigDecimal gap = null;
		if (installed != null && choices.requestedKwc() != null) {
			gap = installed.subtract(choices.requestedKwc())
				.multiply(BigDecimal.valueOf(100))
				.divide(choices.requestedKwc(), 1, RoundingMode.HALF_UP);
			if (gap.abs().compareTo(GAP_WARNING) > 0) {
				warnings.add("La puissance installée (" + installed.stripTrailingZeros().toPlainString() + " kWc) s'écarte de "
						+ gap.toPlainString().replace('.', ',') + " % de la puissance demandée ("
						+ choices.requestedKwc().stripTrailingZeros().toPlainString().replace('.', ',') + " kWc).");
			}
		}
		BigDecimal inverters = choices.inverterCount() == null || choices.inverterKva() == null ? null
				: choices.inverterKva().multiply(BigDecimal.valueOf(choices.inverterCount()));
		BigDecimal ratio = installed == null || inverters == null || inverters.signum() == 0 ? null
				: installed.divide(inverters, 2, RoundingMode.HALF_UP);
		if (ratio != null && ratio.compareTo(BigDecimal.valueOf(1.3)) > 0) {
			warnings.add("Ratio DC/AC de " + ratio.toPlainString().replace('.', ',')
					+ " : onduleurs sous-dimensionnés pour cette puissance de modules.");
		}
		if (ratio != null && ratio.compareTo(BigDecimal.valueOf(0.9)) < 0) {
			warnings.add("Ratio DC/AC de " + ratio.toPlainString().replace('.', ',') + " : onduleurs surdimensionnés.");
		}
		return new Sizing(installed, gap, inverters, ratio, warnings);
	}

	/** The power of a module read in its reference: « 460 Wc », « DM460G12RT », « JKM450N ». */
	static BigDecimal watts(String reference) {
		Matcher written = WATTS.matcher(reference);
		if (written.find()) {
			return new BigDecimal(written.group(1));
		}
		Matcher inside = THREE_DIGITS.matcher(reference);
		return inside.find() ? new BigDecimal(inside.group(1)) : null;
	}

	/** The powers named by the latest analysis of the file, each once, with its sentence. */
	private List<PowerHint> powers(Long tenderId) {
		List<String> values = jdbc.queryForList("""
				SELECT coalesce(f.corrected_value, f.value) FROM extracted_field f
				JOIN analysis a ON a.id = f.analysis_id JOIN dossier d ON d.id = a.dossier_id
				WHERE d.tender_id = ? AND a.kind = 'DCE_SUMMARY' AND a.status = 'DONE'
				  AND a.id = (SELECT max(b.id) FROM analysis b JOIN dossier e ON e.id = b.dossier_id
				              WHERE e.tender_id = ? AND b.kind = 'DCE_SUMMARY' AND b.status = 'DONE')
				  AND coalesce(f.corrected_value, f.value) ILIKE '%kWc%'""", String.class, tenderId, tenderId);
		Map<BigDecimal, String> found = new LinkedHashMap<>();
		for (String value : values) {
			Matcher matcher = KWC.matcher(value);
			while (matcher.find()) {
				BigDecimal kwc = new BigDecimal(matcher.group(1).replace(',', '.')).stripTrailingZeros();
				found.putIfAbsent(kwc, value.length() <= 200 ? value : value.substring(0, 200) + "…");
			}
		}
		return found.entrySet().stream().map((entry) -> new PowerHint(entry.getKey(), entry.getValue())).toList();
	}

	private void checkTender(Long tenderId) {
		if (jdbc.queryForObject("SELECT count(*) FROM tender WHERE id = ?", Long.class, tenderId) == 0) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Appel d'offres introuvable.");
		}
	}

	private static String clean(String text) {
		return text == null || text.isBlank() ? null : text.strip();
	}

	private static Map<String, String> valorisations() {
		Map<String, String> labels = new LinkedHashMap<>();
		labels.put("SELF_TOTAL", "autoconsommation totale");
		labels.put("SELF_SURPLUS", "autoconsommation avec revente du surplus");
		labels.put("RESALE_TOTAL", "revente totale");
		return labels;
	}

}
