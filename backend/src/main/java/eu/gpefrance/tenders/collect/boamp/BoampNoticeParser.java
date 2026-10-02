package eu.gpefrance.tenders.collect.boamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import eu.gpefrance.tenders.ClockConfiguration;
import eu.gpefrance.tenders.tender.LotDetails;
import eu.gpefrance.tenders.tender.MarketType;
import eu.gpefrance.tenders.tender.ProcedureType;
import eu.gpefrance.tenders.tender.TenderDetails;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Converts a BOAMP notice to the common format.
 * <p>
 * The indexed fields at the top level of the record are reliable and used
 * first. The full notice ({@code donnees}) comes in several schemas (eForms,
 * FNSimple, MAPA, DSP...), so the information found only there (CPV codes,
 * amount, lots, links) is looked up by key name rather than by fixed path.
 */
@Component
public class BoampNoticeParser {

	private static final String NOTICE_URL = "https://www.boamp.fr/pages/avis/?q=idweb:";

	private static final Pattern CPV = Pattern.compile("^(\\d{8})");

	private static final Pattern EFORMS_LOT = Pattern.compile("^LOT-0*(\\d+)$");

	private static final Set<String> AMOUNT_KEYS = Set.of("cbc:EstimatedOverallContractAmount", "valeurEstimee",
			"estimationValeurTotal");

	private static final Set<String> PLATFORM_KEYS = Set.of("urlProfilAch", "urlProfilAcheteur",
			"cbc:BuyerProfileURI");

	private static final Set<String> LOT_KEYS = Set.of("cac:ProcurementProjectLot", "lot");

	private static final Map<String, ProcedureType> PROCEDURES = Map.of("OUVERT", ProcedureType.OPEN, "RESTREINT",
			ProcedureType.RESTRICTED, "NEGOCIE", ProcedureType.NEGOTIATED, "DIALOGUE_COMPETITIF",
			ProcedureType.COMPETITIVE_DIALOGUE, "PROCEDURE_ADAPTE", ProcedureType.ADAPTED, "DSP",
			ProcedureType.CONCESSION, "AUTRE", ProcedureType.OTHER);

	private static final Map<String, MarketType> MARKET_TYPES = Map.of("TRAVAUX", MarketType.WORKS, "SERVICES",
			MarketType.SERVICES, "FOURNITURES", MarketType.SUPPLIES);

	private final JsonMapper jsonMapper;

	public BoampNoticeParser(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	public ParsedNotice parse(String json) {
		JsonNode record = jsonMapper.readTree(json);
		JsonNode donnees = parseJson(record.get("donnees"));
		String idweb = text(record.get("idweb"));

		TenderDetails details = new TenderDetails(title(record, donnees), description(donnees),
				TextCleaner.cleanLine(text(record.get("nomacheteur"))), reference(donnees), location(donnees),
				departments(record), marketTypes(record), procedure(record), cpvCodes(donnees),
				amount(findFirst(donnees, AMOUNT_KEYS::contains)), date(record.get("dateparution")), deadline(record),
				noticeUrl(record, idweb), platformUrl(donnees), lots(donnees));

		return new ParsedNotice(idweb, type(record), linkedIds(record), details, changeDescription(donnees));
	}

	/**
	 * Parses a field that holds JSON as text, such as {@code donnees}.
	 */
	private JsonNode parseJson(JsonNode field) {
		String json = text(field);
		return json == null ? jsonMapper.createObjectNode() : jsonMapper.readTree(json);
	}

	private static BoampNoticeType type(JsonNode record) {
		String nature = text(record.get("nature"));
		String etat = text(record.get("etat"));
		if ("ATTRIBUTION".equals(nature)) {
			return BoampNoticeType.AWARD;
		}
		if ("ANNULATION".equals(nature) || "ANNULATION".equals(etat)) {
			return BoampNoticeType.CANCELLATION;
		}
		if ("RECTIFICATIF".equals(nature)) {
			return BoampNoticeType.RECTIFICATION;
		}
		if ("APPEL_OFFRE".equals(nature)) {
			return "RECTIFICATIF".equals(etat) ? BoampNoticeType.COMPLETE_RECTIFICATION
					: BoampNoticeType.CALL_FOR_TENDERS;
		}
		return BoampNoticeType.OTHER;
	}

	private static List<String> linkedIds(JsonNode record) {
		Set<String> ids = new LinkedHashSet<>();
		ids.addAll(texts(record.get("annonce_lie")));
		ids.addAll(texts(record.get("annonces_anterieures")));
		return List.copyOf(ids);
	}

	private static String title(JsonNode record, JsonNode donnees) {
		String title = TextCleaner.cleanLine(text(record.get("objet")));
		return title != null ? title : TextCleaner.cleanLine(text(findFirst(donnees, "intitule"::equals)));
	}

	/**
	 * The description of the whole consultation: the project description in
	 * eForms, otherwise the first plain-text description (lots come later in
	 * a breadth-first search), or the main characteristics of a MAPA notice.
	 */
	private static String description(JsonNode donnees) {
		for (JsonNode notice : donnees.path("EFORMS").values()) {
			String description = text(notice.path("cac:ProcurementProject").get("cbc:Description"));
			if (description != null) {
				return TextCleaner.clean(description);
			}
		}
		JsonNode description = findFirst(donnees, "description"::equals, JsonNode::isString);
		if (description == null) {
			description = findFirst(donnees, "principales"::equals, JsonNode::isString);
		}
		return TextCleaner.clean(text(description));
	}

	/**
	 * The buyer's own reference of the consultation.
	 */
	private static String reference(JsonNode donnees) {
		String reference = text(
				findFirst(donnees, key -> key.equals("identifiantInterne") || key.equals("idMarche")));
		if (reference == null) {
			JsonNode eforms = donnees.path("EFORMS");
			for (JsonNode notice : eforms.values()) {
				reference = text(notice.path("cac:ProcurementProject").get("cbc:ID"));
				if (reference != null) {
					break;
				}
			}
		}
		return TextCleaner.cleanLine(reference);
	}

	/**
	 * The place of execution as published (city, area or address).
	 */
	private static String location(JsonNode donnees) {
		JsonNode realized = findFirst(donnees, "cac:RealizedLocation"::equals);
		if (realized != null) {
			String city = text(findFirst(realized, "cbc:CityName"::equals));
			if (city != null) {
				return TextCleaner.cleanLine(city);
			}
		}
		JsonNode place = findFirst(donnees,
				key -> key.equals("lieuExecution") || key.equals("lieuExecutionLivraison"));
		if (place == null) {
			return null;
		}
		List<String> parts = new ArrayList<>();
		collectTexts(place, parts);
		return TextCleaner.cleanLine(String.join(", ", parts));
	}

	/**
	 * Departments of execution when given, otherwise of publication, as
	 * two-character codes ("05", "2A", "971").
	 */
	private static List<String> departments(JsonNode record) {
		List<String> departments = texts(record.get("code_departement_prestation"));
		if (departments.isEmpty()) {
			departments = texts(record.get("code_departement"));
		}
		return departments.stream()
			.map(String::strip)
			.filter(code -> !code.isEmpty())
			.map(code -> code.length() == 1 ? "0" + code : code)
			.distinct()
			.toList();
	}

	private static List<MarketType> marketTypes(JsonNode record) {
		return texts(record.get("type_marche")).stream()
			.map(MARKET_TYPES::get)
			.filter(type -> type != null)
			.distinct()
			.toList();
	}

	private static ProcedureType procedure(JsonNode record) {
		String procedure = text(record.get("type_procedure"));
		if (procedure != null) {
			return PROCEDURES.getOrDefault(procedure, ProcedureType.OTHER);
		}
		return "MAPA".equals(text(record.get("famille"))) ? ProcedureType.ADAPTED : null;
	}

	private static List<String> cpvCodes(JsonNode donnees) {
		Set<String> codes = new LinkedHashSet<>();
		collectCpvCodes(donnees, codes);
		return List.copyOf(codes);
	}

	private static void collectCpvCodes(JsonNode node, Set<String> codes) {
		if (node.isObject()) {
			if ("cpv".equals(text(node.get("@listName")))) {
				addCpv(text(node.get("#text")), codes);
			}
			for (Map.Entry<String, JsonNode> property : node.properties()) {
				String key = property.getKey();
				if (key.equals("classPrincipale") || key.equals("classSupplementaire")) {
					texts(property.getValue()).forEach(code -> addCpv(code, codes));
				}
				collectCpvCodes(property.getValue(), codes);
			}
		}
		else if (node.isArray()) {
			node.values().forEach(child -> collectCpvCodes(child, codes));
		}
	}

	private static void addCpv(String code, Set<String> codes) {
		if (code != null) {
			Matcher matcher = CPV.matcher(code.strip());
			if (matcher.find()) {
				codes.add(matcher.group(1));
			}
		}
	}

	/**
	 * An amount in euros, or {@code null}. A zero amount means the buyer did
	 * not give it: it is never kept as zero.
	 */
	private static BigDecimal amount(JsonNode node) {
		if (node == null) {
			return null;
		}
		String value = text(node);
		if (node.isObject()) {
			String currency = text(node.has("@currencyID") ? node.get("@currencyID") : node.get("@devise"));
			if (currency != null && !currency.equals("EUR")) {
				return null;
			}
			value = text(node.has("valeur") ? node.get("valeur") : node.get("#text"));
		}
		if (value == null) {
			return null;
		}
		try {
			BigDecimal amount = new BigDecimal(value.strip().replace(" ", "").replace(',', '.'));
			return amount.signum() > 0 ? amount : null;
		}
		catch (NumberFormatException ex) {
			return null;
		}
	}

	private static LocalDate date(JsonNode node) {
		String value = text(node);
		if (value == null) {
			return null;
		}
		try {
			return LocalDate.parse(value.length() > 10 ? value.substring(0, 10) : value);
		}
		catch (DateTimeParseException ex) {
			return null;
		}
	}

	/**
	 * The deadline as written by the buyer, read as Paris time.
	 * <p>
	 * The indexed {@code datelimitereponse} is unreliable: FNSimple and MAPA
	 * local times are labelled UTC, and eForms notices often carry a winter
	 * offset in summer. The management block keeps the time as published
	 * ("12:00"), which is what the buyer meant. Overseas departments, whose
	 * local time differs, are not handled.
	 */
	private Instant deadline(JsonNode record) {
		String published = text(parseJson(record.get("gestion")).path("INDEXATION").get("DATE_LIMITE_REPONSE"));
		if (published != null) {
			Instant deadline = parisTime(published);
			if (deadline != null) {
				return deadline;
			}
		}
		String indexed = text(record.get("datelimitereponse"));
		try {
			return indexed == null ? null : OffsetDateTime.parse(indexed).toInstant();
		}
		catch (DateTimeParseException ex) {
			return parisTime(indexed);
		}
	}

	/**
	 * Reads the local date and time, ignoring any offset. A date without a
	 * time is taken as the end of that day, so it never looks passed too early.
	 */
	private static Instant parisTime(String value) {
		String text = value.strip();
		try {
			if (text.length() >= 19) {
				return LocalDateTime.parse(text.substring(0, 19)).atZone(ClockConfiguration.ZONE).toInstant();
			}
			if (text.length() >= 16) {
				return LocalDateTime.parse(text.substring(0, 16)).atZone(ClockConfiguration.ZONE).toInstant();
			}
			return LocalDate.parse(text.substring(0, Math.min(10, text.length())))
				.atTime(LocalTime.of(23, 59))
				.atZone(ClockConfiguration.ZONE)
				.toInstant();
		}
		catch (DateTimeParseException | IndexOutOfBoundsException ex) {
			return null;
		}
	}

	private static String noticeUrl(JsonNode record, String idweb) {
		String url = url(text(record.get("url_avis")));
		return url != null || idweb == null ? url : NOTICE_URL + idweb;
	}

	/**
	 * Where the consultation documents can be downloaded, or failing that
	 * the buyer's platform.
	 */
	private static String platformUrl(JsonNode donnees) {
		JsonNode documents = findFirst(donnees, "cac:CallForTendersDocumentReference"::equals);
		if (documents != null) {
			String url = url(text(findFirst(documents, "cbc:URI"::equals)));
			if (url != null) {
				return url;
			}
		}
		return url(text(findFirst(donnees, PLATFORM_KEYS::contains)));
	}

	private static String url(String value) {
		if (value == null) {
			return null;
		}
		String url = value.strip().replace("&amp;", "&");
		if (url.startsWith("www.")) {
			url = "https://" + url;
		}
		return url.startsWith("http://") || url.startsWith("https://") ? url : null;
	}

	/**
	 * Lots are kept only when the tender is really divided: a single lot is
	 * the tender itself.
	 */
	private static List<LotDetails> lots(JsonNode donnees) {
		JsonNode node = findFirst(donnees, LOT_KEYS::contains);
		if (node == null) {
			return List.of();
		}
		List<JsonNode> items = node.isArray() ? List.copyOf(node.values()) : List.of(node);
		if (items.size() < 2) {
			return List.of();
		}
		List<LotDetails> lots = new ArrayList<>();
		for (int i = 0; i < items.size(); i++) {
			JsonNode item = items.get(i);
			JsonNode project = item.path("cac:ProcurementProject");
			String number = firstText(item.get("numLot"), item.get("cbc:ID"));
			String title = firstText(item.get("intitule"), item.get("intituleLot"), item.get("description"),
					project.get("cbc:Name"));
			BigDecimal amount = amount(item.get("estimationValeur"));
			if (amount == null) {
				amount = amount(project.path("cac:RequestedTenderTotal").get("cbc:EstimatedOverallContractAmount"));
			}
			lots.add(new LotDetails(lotNumber(number, i + 1), TextCleaner.cleanLine(title), amount));
		}
		return lots;
	}

	private static String lotNumber(String number, int position) {
		if (number == null || number.isBlank()) {
			return String.valueOf(position);
		}
		Matcher eforms = EFORMS_LOT.matcher(number.strip());
		return eforms.matches() ? eforms.group(1) : number.strip();
	}

	private static String changeDescription(JsonNode donnees) {
		List<String> changes = new ArrayList<>();
		collectByKey(donnees, key -> key.equals("efbc:ChangeDescription") || key.equals("infosRectif"), changes);
		return TextCleaner.clean(String.join("\n", changes));
	}

	private static void collectByKey(JsonNode node, Predicate<String> key, List<String> values) {
		if (node.isObject()) {
			for (Map.Entry<String, JsonNode> property : node.properties()) {
				if (key.test(property.getKey())) {
					collectTexts(property.getValue(), values);
				}
				else {
					collectByKey(property.getValue(), key, values);
				}
			}
		}
		else if (node.isArray()) {
			node.values().forEach(child -> collectByKey(child, key, values));
		}
	}

	/**
	 * Breadth-first search, so the notice-level value is found before the
	 * same key repeated inside each lot.
	 */
	private static JsonNode findFirst(JsonNode root, Predicate<String> key) {
		return findFirst(root, key, value -> !value.isNull());
	}

	private static JsonNode findFirst(JsonNode root, Predicate<String> key, Predicate<JsonNode> value) {
		Deque<JsonNode> queue = new ArrayDeque<>();
		queue.add(root);
		while (!queue.isEmpty()) {
			JsonNode node = queue.poll();
			if (node.isObject()) {
				for (Map.Entry<String, JsonNode> property : node.properties()) {
					if (key.test(property.getKey()) && value.test(property.getValue())) {
						return property.getValue();
					}
					queue.add(property.getValue());
				}
			}
			else if (node.isArray()) {
				queue.addAll(node.values());
			}
		}
		return null;
	}

	/**
	 * Text of a value: plain text, or the {@code #text} of an eForms element,
	 * or the first text of a list.
	 */
	private static String text(JsonNode node) {
		if (node == null || node.isNull() || node.isMissingNode()) {
			return null;
		}
		if (node.isString()) {
			String value = node.stringValue();
			return value.isBlank() ? null : value;
		}
		if (node.isNumber()) {
			return node.asString();
		}
		if (node.isObject()) {
			return text(node.get("#text"));
		}
		if (node.isArray()) {
			for (JsonNode child : node.values()) {
				String value = text(child);
				if (value != null) {
					return value;
				}
			}
		}
		return null;
	}

	private static String firstText(JsonNode... nodes) {
		for (JsonNode node : nodes) {
			String value = text(node);
			if (value != null) {
				return value;
			}
		}
		return null;
	}

	private static List<String> texts(JsonNode node) {
		if (node == null || node.isNull()) {
			return List.of();
		}
		if (node.isArray()) {
			List<String> values = new ArrayList<>();
			for (JsonNode child : node.values()) {
				String value = text(child);
				if (value != null) {
					values.add(value);
				}
			}
			return values;
		}
		String value = text(node);
		return value == null ? List.of() : List.of(value);
	}

	/**
	 * All texts under a node, skipping eForms attributes such as language codes.
	 */
	private static void collectTexts(JsonNode node, List<String> values) {
		if (node.isString() || node.isNumber()) {
			String value = text(node);
			if (value != null) {
				values.add(value);
			}
		}
		else if (node.isObject()) {
			for (Map.Entry<String, JsonNode> property : node.properties()) {
				if (!property.getKey().startsWith("@")) {
					collectTexts(property.getValue(), values);
				}
			}
		}
		else if (node.isArray()) {
			node.values().forEach(child -> collectTexts(child, values));
		}
	}

}
