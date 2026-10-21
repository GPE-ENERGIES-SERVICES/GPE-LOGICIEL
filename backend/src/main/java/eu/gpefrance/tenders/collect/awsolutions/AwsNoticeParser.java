package eu.gpefrance.tenders.collect.awsolutions;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import eu.gpefrance.tenders.ClockConfiguration;
import eu.gpefrance.tenders.collect.NoticeType;
import eu.gpefrance.tenders.collect.SourceNotice;
import eu.gpefrance.tenders.collect.SourceParser;
import eu.gpefrance.tenders.collect.ted.NutsDepartments;
import eu.gpefrance.tenders.tender.MarketType;
import eu.gpefrance.tenders.tender.ProcedureType;
import eu.gpefrance.tenders.tender.TenderDetails;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Converts a notice of marches-publics.info, as read by {@link AwsClient}
 * from the list of results and the page of the notice, to the common format.
 * The consultation is identified by its number on the platform (IDM), the
 * same for all its notices; a notice read again because it changed updates
 * its tender. The site gives no eForms procedure identifier: a tender of
 * another source with the same buyer reference and the same deadline is
 * only proposed as the same consultation.
 */
@Component
public class AwsNoticeParser implements SourceParser {

	private static final Pattern DEADLINE = Pattern.compile("(\\d{2}/\\d{2}/\\d{2}) à (\\d{1,2})h(\\d{2})");

	private static final Pattern NUTS = Pattern.compile("\\b(FR[A-Z0-9]{3})\\b");

	private static final Pattern POST_CODE = Pattern.compile("\\b(\\d{5})\\b");

	private static final Pattern CPV = Pattern.compile("\\b(\\d{8})\\b");

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yy");

	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yy H mm");

	private final JsonMapper jsonMapper;

	public AwsNoticeParser(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	@Override
	public String source() {
		return AwsConnector.SOURCE;
	}

	@Override
	public boolean republishesChangedNotices() {
		return true;
	}

	@Override
	public boolean proposesMatchesByReference() {
		return true;
	}

	@Override
	public SourceNotice parse(String payload) {
		JsonNode stored = jsonMapper.readTree(payload);
		JsonNode listing = stored.path("listing");
		JsonNode notice = stored.path("notice");
		JsonNode fields = notice.path("fields");
		String noticeId = text(listing.get("noticeId"));
		if (noticeId == null) {
			throw new IllegalArgumentException("marches-publics.info notice without page");
		}
		String consultation = text(listing.get("consultationId"));
		String externalId = consultation == null ? noticeId : "IDM-" + consultation;
		TenderDetails details = new TenderDetails(firstOf(fields.get("Objet"), listing.get("title")),
				description(fields), firstOf(listing.get("buyer"), notice.get("buyer")),
				firstOf(fields.get("Référence"), listing.get("reference")), location(fields),
				departments(fields, listing), marketTypes(fields), procedure(fields), cpvCodes(fields), null,
				date(text(listing.get("publishedOn"))), deadline(fields, listing), text(listing.get("noticeUrl")),
				text(listing.get("dceUrl")), List.of());
		NoticeType type = type(text(notice.get("heading")));
		List<String> linked = type == NoticeType.CANCELLATION ? List.of(externalId) : List.of();
		return new SourceNotice(externalId, type, linked, details, null, null);
	}

	/**
	 * Only calls for tenders in progress are searched; a cancellation is
	 * recognised by its heading. A rectification republishes the whole
	 * notice and updates the tender.
	 */
	private static NoticeType type(String heading) {
		String upper = heading == null ? "" : heading.toUpperCase(Locale.ROOT);
		if (upper.contains("ANNULATION") || upper.contains("SANS SUITE")) {
			return NoticeType.CANCELLATION;
		}
		return NoticeType.CALL_FOR_TENDERS;
	}

	private static String description(JsonNode fields) {
		List<String> lines = new ArrayList<>();
		addLine(lines, null, text(fields.get("Description")));
		addLine(lines, "Durée", text(fields.get("Durée")));
		addLine(lines, "Lieu d'exécution", text(fields.get("Lieu d'exécution")));
		return lines.isEmpty() ? null : String.join("\n", lines);
	}

	private static void addLine(List<String> lines, String label, String value) {
		if (value != null) {
			lines.add(label == null ? value : label + " : " + value);
		}
	}

	/** "FRK12 - Cantal" gives "Cantal". */
	private static String location(JsonNode fields) {
		String main = text(fields.get("Lieu d'exécution principal"));
		if (main == null) {
			return null;
		}
		int dash = main.indexOf(" - ");
		return dash >= 0 ? main.substring(dash + 3).strip() : main;
	}

	/**
	 * From the NUTS region of the place of performance, otherwise the post
	 * codes of the place, otherwise the post code of the buyer.
	 */
	private static List<String> departments(JsonNode fields, JsonNode listing) {
		Set<String> departments = new LinkedHashSet<>();
		Matcher nuts = NUTS.matcher(String.valueOf(text(fields.get("Lieu d'exécution principal"))));
		while (nuts.find()) {
			addDepartment(NutsDepartments.departmentOf(nuts.group(1)), departments);
		}
		if (departments.isEmpty()) {
			Matcher postCodes = POST_CODE.matcher(String.valueOf(text(fields.get("Lieu d'exécution"))));
			while (postCodes.find()) {
				addDepartment(departmentOfPostCode(postCodes.group(1)), departments);
			}
		}
		if (departments.isEmpty()) {
			addDepartment(departmentOfPostCode(text(listing.get("postCode"))), departments);
		}
		return List.copyOf(departments);
	}

	private static void addDepartment(String department, Set<String> departments) {
		if (department != null) {
			departments.add(department);
		}
	}

	private static String departmentOfPostCode(String postCode) {
		if (postCode == null || !postCode.matches("\\d{5}")) {
			return null;
		}
		if (postCode.startsWith("97")) {
			return postCode.substring(0, 3);
		}
		if (postCode.startsWith("20")) {
			return postCode.compareTo("20200") < 0 ? "2A" : "2B";
		}
		return postCode.substring(0, 2);
	}

	private static List<MarketType> marketTypes(JsonNode fields) {
		String type = text(fields.get("Type de marché"));
		if (type == null) {
			return List.of();
		}
		String lower = type.toLowerCase(Locale.ROOT);
		List<MarketType> types = new ArrayList<>();
		if (lower.contains("travaux")) {
			types.add(MarketType.WORKS);
		}
		if (lower.contains("service")) {
			types.add(MarketType.SERVICES);
		}
		if (lower.contains("fourniture")) {
			types.add(MarketType.SUPPLIES);
		}
		return types;
	}

	private static ProcedureType procedure(JsonNode fields) {
		String mode = text(fields.get("Mode"));
		if (mode == null) {
			return null;
		}
		String lower = mode.toLowerCase(Locale.ROOT);
		if (lower.contains("adaptée")) {
			return ProcedureType.ADAPTED;
		}
		if (lower.contains("dialogue")) {
			return ProcedureType.COMPETITIVE_DIALOGUE;
		}
		if (lower.contains("négoci")) {
			return ProcedureType.NEGOTIATED;
		}
		if (lower.contains("restreint")) {
			return ProcedureType.RESTRICTED;
		}
		if (lower.contains("ouvert")) {
			return ProcedureType.OPEN;
		}
		if (lower.contains("concession") || lower.contains("délégation")) {
			return ProcedureType.CONCESSION;
		}
		return ProcedureType.OTHER;
	}

	private static List<String> cpvCodes(JsonNode fields) {
		Set<String> codes = new LinkedHashSet<>();
		for (String label : List.of("Code CPV principal", "Code CPV complémentaire")) {
			Matcher matcher = CPV.matcher(String.valueOf(text(fields.get(label))));
			while (matcher.find()) {
				codes.add(matcher.group(1));
			}
		}
		return List.copyOf(codes);
	}

	/**
	 * The deadline for the offers on the page of the notice, otherwise the
	 * one of the list, Paris time.
	 */
	private static Instant deadline(JsonNode fields, JsonNode listing) {
		for (String text : new String[] { text(fields.get("Offres")), text(fields.get("Candidatures")),
				text(listing.get("deadline")) }) {
			Instant deadline = deadline(text);
			if (deadline != null) {
				return deadline;
			}
		}
		return null;
	}

	private static Instant deadline(String text) {
		if (text == null) {
			return null;
		}
		Matcher matcher = DEADLINE.matcher(text);
		if (!matcher.find()) {
			return null;
		}
		try {
			return LocalDateTime
				.parse(matcher.group(1) + " " + matcher.group(2) + " " + matcher.group(3), DATE_TIME)
				.atZone(ClockConfiguration.ZONE)
				.toInstant();
		}
		catch (DateTimeParseException ex) {
			return null;
		}
	}

	private static LocalDate date(String text) {
		if (text == null) {
			return null;
		}
		try {
			return LocalDate.parse(text, DATE);
		}
		catch (DateTimeParseException ex) {
			return null;
		}
	}

	private static String firstOf(JsonNode first, JsonNode second) {
		String text = text(first);
		return text != null ? text : text(second);
	}

	private static String text(JsonNode node) {
		if (node == null || node.isNull()) {
			return null;
		}
		String text = node.asString().strip();
		return text.isEmpty() ? null : text;
	}

}
