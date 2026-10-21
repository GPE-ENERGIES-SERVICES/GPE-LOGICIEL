package eu.gpefrance.tenders.collect.approch;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import eu.gpefrance.tenders.collect.NoticeType;
import eu.gpefrance.tenders.collect.SourceNotice;
import eu.gpefrance.tenders.collect.SourceParser;
import eu.gpefrance.tenders.tender.MarketType;
import eu.gpefrance.tenders.tender.ProcedureType;
import eu.gpefrance.tenders.tender.TenderDetails;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Converts a purchase project of APProch, as stored by {@link ApprochClient},
 * to the common format. A project is a forecast: its procedure type is
 * {@link ProcedureType#PURCHASE_PROJECT}, it has no deadline, and its
 * forecast dates, procedure, amount range and duration are written after its
 * description. Its publication date is the day it was first read. A project
 * has no rectification: each change republishes it whole under its code.
 */
@Component
public class ApprochNoticeParser implements SourceParser {

	static final String PROJECT_URL = "https://projets-achats.marches-publics.gouv.fr/project/";

	private static final String OPEN = "Ouvert";

	private static final Pattern CPV = Pattern.compile("^\\d{8}$");

	private static final Pattern DEPARTMENT = Pattern.compile("^(\\d{2,3}|2A|2B)$");

	private static final Map<String, MarketType> CATEGORIES = Map.of("travaux", MarketType.WORKS, "services",
			MarketType.SERVICES, "fournitures", MarketType.SUPPLIES);

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final JsonMapper jsonMapper;

	public ApprochNoticeParser(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	@Override
	public String source() {
		return ApprochConnector.SOURCE;
	}

	@Override
	public boolean republishesChangedNotices() {
		return true;
	}

	@Override
	public SourceNotice parse(String payload) {
		JsonNode stored = jsonMapper.readTree(payload);
		JsonNode project = stored.path("project");
		String code = text(project.get("code"));
		if (code == null) {
			throw new IllegalArgumentException("APProch project without code");
		}
		TenderDetails details = new TenderDetails(text(project.get("libelle")), description(project),
				buyer(stored, project), null, null, departments(project), marketTypes(project),
				ProcedureType.PURCHASE_PROJECT, cpvCodes(project), null, date(stored.get("collectedOn")), null,
				PROJECT_URL + code, consultationUrl(project), List.of());
		NoticeType type = OPEN.equals(text(project.get("statut"))) ? NoticeType.CALL_FOR_TENDERS : NoticeType.OTHER;
		return new SourceNotice(code, type, List.of(), details, null, null);
	}

	/**
	 * The description of the buyer, then what the project forecasts.
	 */
	private static String description(JsonNode project) {
		List<String> parts = new ArrayList<>();
		String description = text(project.get("description"));
		if (description != null) {
			parts.add(description);
		}
		List<String> forecast = new ArrayList<>();
		forecast.add("Projet d'achat prévisionnel publié sur APProch : ce n'est pas un avis d'appel d'offres.");
		addLine(forecast, "Publication prévisionnelle de la consultation", formatted(project.get("date_previsionnelle_de_publication")));
		addLine(forecast, "Date cible de remise des offres", formatted(project.get("date_cible_de_remise_des_offres")));
		addLine(forecast, "Procédure envisagée", text(project.get("type_de_procedure")));
		addLine(forecast, "Montant estimé", text(project.get("montant_estime_du_marche")));
		String duration = text(project.get("duree_previsionnelle_du_marche"));
		addLine(forecast, "Durée prévisionnelle", duration == null ? null : duration + " mois");
		parts.add(String.join("\n", forecast));
		return String.join("\n\n", parts);
	}

	private static void addLine(List<String> lines, String label, String value) {
		if (value != null) {
			lines.add(label + " : " + value);
		}
	}

	/**
	 * The name found from the SIREN, otherwise the SIREN itself.
	 */
	private static String buyer(JsonNode stored, JsonNode project) {
		String name = text(stored.get("buyerName"));
		if (name != null) {
			return name;
		}
		String siren = text(project.get("siren_de_l_entite_acheteuse"));
		return siren == null ? null : "Entité acheteuse SIREN " + siren;
	}

	private static List<String> departments(JsonNode project) {
		return split(project.get("departement_s_d_execution_du_marche"), DEPARTMENT);
	}

	private static List<String> cpvCodes(JsonNode project) {
		return split(project.get("code_s_cpv"), CPV);
	}

	private static List<MarketType> marketTypes(JsonNode project) {
		String category = text(project.get("categorie_d_achat"));
		MarketType type = category == null ? null : CATEGORIES.get(category.toLowerCase(Locale.ROOT));
		return type == null ? List.of() : List.of(type);
	}

	/**
	 * The consultation published since on a platform, when the buyer gave it.
	 */
	private static String consultationUrl(JsonNode project) {
		String url = text(project.get("lien_vers_la_consultation"));
		return url != null && (url.startsWith("https://") || url.startsWith("http://")) ? url : null;
	}

	/**
	 * The values of a field separated by "|", kept when they have the
	 * expected form, without repetition.
	 */
	private static List<String> split(JsonNode node, Pattern valid) {
		String value = text(node);
		if (value == null) {
			return List.of();
		}
		Set<String> values = new LinkedHashSet<>();
		for (String part : value.split("\\|")) {
			String code = part.strip().toUpperCase(Locale.ROOT);
			if (valid.matcher(code).matches()) {
				values.add(code);
			}
		}
		return List.copyOf(values);
	}

	private static LocalDate date(JsonNode node) {
		String text = text(node);
		if (text == null) {
			return null;
		}
		try {
			return LocalDate.parse(text);
		}
		catch (DateTimeParseException ex) {
			return null;
		}
	}

	private static String formatted(JsonNode node) {
		LocalDate date = date(node);
		return date == null ? null : date.format(DATE);
	}

	private static String text(JsonNode node) {
		if (node == null || node.isNull()) {
			return null;
		}
		String text = node.asString().strip();
		return text.isEmpty() ? null : text;
	}

}
