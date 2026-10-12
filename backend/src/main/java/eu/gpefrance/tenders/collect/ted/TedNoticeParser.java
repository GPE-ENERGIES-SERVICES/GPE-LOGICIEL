package eu.gpefrance.tenders.collect.ted;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import eu.gpefrance.tenders.ClockConfiguration;
import eu.gpefrance.tenders.collect.NoticeType;
import eu.gpefrance.tenders.collect.SourceNotice;
import eu.gpefrance.tenders.collect.SourceParser;
import eu.gpefrance.tenders.tender.LotDetails;
import eu.gpefrance.tenders.tender.MarketType;
import eu.gpefrance.tenders.tender.ProcedureType;
import eu.gpefrance.tenders.tender.TenderDetails;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Converts a TED notice, as returned by the search API with the fields of
 * {@link TedClient#FIELDS}, to the common format. TED only publishes eForms
 * notices: a change notice repeats the whole notice and cites the one it
 * changes, sometimes by its publication number, sometimes by its eForms
 * identifier; every notice of a consultation gives the same procedure
 * identifier, which links them.
 */
@Component
public class TedNoticeParser implements SourceParser {

	private static final String NOTICE_URL = "https://ted.europa.eu/fr/notice/-/detail/";

	private static final List<String> LANGUAGES = List.of("fra", "mul", "eng");

	private static final Pattern PUBLICATION_NUMBER = Pattern.compile("^\\d+-\\d{4}$");

	private static final Pattern LOT = Pattern.compile("^LOT-0*(\\d+)$");

	private static final Pattern POST_CODE = Pattern.compile("^(\\d{5})");

	private static final Pattern CPV = Pattern.compile("^(\\d{8})");

	private static final Set<String> AWARD_TYPES = Set.of("can-standard", "can-social", "can-desg", "can-tran");

	private static final Set<String> CALL_TYPES = Set.of("cn-standard", "cn-social", "cn-desg");

	private static final Map<String, ProcedureType> PROCEDURES = Map.of("open", ProcedureType.OPEN, "restricted",
			ProcedureType.RESTRICTED, "neg-w-call", ProcedureType.NEGOTIATED, "neg-wo-call", ProcedureType.NEGOTIATED,
			"comp-dial", ProcedureType.COMPETITIVE_DIALOGUE);

	private static final Map<String, MarketType> NATURES = Map.of("works", MarketType.WORKS, "services",
			MarketType.SERVICES, "supplies", MarketType.SUPPLIES);

	private final JsonMapper jsonMapper;

	public TedNoticeParser(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	@Override
	public String source() {
		return TedConnector.SOURCE;
	}

	/**
	 * The notice a TED change cites is not always given by its publication
	 * number: the procedure identifier is the reliable link.
	 */
	@Override
	public boolean linksNoticesByProcedure() {
		return true;
	}

	@Override
	public SourceNotice parse(String payload) {
		JsonNode notice = jsonMapper.readTree(payload);
		String number = text(notice.get("publication-number"));
		if (number == null) {
			throw new IllegalArgumentException("TED notice without publication number");
		}
		TenderDetails details = new TenderDetails(language(notice.get("title-proc")),
				language(notice.get("description-proc")), language(notice.get("buyer-name")),
				text(notice.get("internal-identifier-proc")), location(notice), departments(notice),
				marketTypes(notice), procedure(notice), cpvCodes(notice), amount(notice),
				publishedAt(notice), deadline(notice), noticeUrl(notice, number), platformUrl(notice), lots(notice));
		return new SourceNotice(number, type(notice), linkedIds(notice), details, changeDescription(notice),
				text(notice.get("procedure-identifier")));
	}

	private static NoticeType type(JsonNode notice) {
		String type = text(notice.get("notice-type"));
		if (type == null) {
			return NoticeType.OTHER;
		}
		if (AWARD_TYPES.contains(type)) {
			return NoticeType.AWARD;
		}
		if (!CALL_TYPES.contains(type)) {
			return NoticeType.OTHER;
		}
		if (text(notice.get("change-notice-version-identifier")) == null) {
			return NoticeType.CALL_FOR_TENDERS;
		}
		return "cancel".equals(text(notice.get("change-reason-code"))) ? NoticeType.CANCELLATION
				: NoticeType.COMPLETE_RECTIFICATION;
	}

	/**
	 * The changed notice, when cited by its publication number; otherwise the
	 * procedure identifier links it.
	 */
	private static List<String> linkedIds(JsonNode notice) {
		String changed = text(notice.get("change-notice-version-identifier"));
		return changed != null && PUBLICATION_NUMBER.matcher(changed).matches() ? List.of(changed) : List.of();
	}

	/** Repeated for every lot: each different text once. */
	private static String changeDescription(JsonNode notice) {
		List<String> texts = new ArrayList<>(new LinkedHashSet<>(languageTexts(notice.get("change-description"))));
		return texts.isEmpty() ? null : String.join("\n", texts);
	}

	private static String location(JsonNode notice) {
		String city = language(notice.get("place-of-performance-city-proc"));
		return city != null ? city : language(notice.get("buyer-city"));
	}

	/**
	 * Where the work is done: the post codes of the place of performance, or
	 * else its NUTS regions; the post code of the buyer only when the notice
	 * says nothing of the place.
	 */
	private static List<String> departments(JsonNode notice) {
		Set<String> departments = new LinkedHashSet<>();
		for (String field : List.of("place-of-performance-post-code-proc", "place-of-performance-post-code-lot")) {
			texts(notice.get(field)).forEach(code -> addDepartment(departmentOfPostCode(code), departments));
		}
		if (departments.isEmpty()) {
			for (String field : List.of("place-of-performance-subdiv-proc", "place-of-performance-subdiv-lot")) {
				texts(notice.get(field)).forEach(nuts -> addDepartment(NutsDepartments.departmentOf(nuts), departments));
			}
		}
		if (departments.isEmpty()) {
			texts(notice.get("buyer-post-code"))
				.forEach(code -> addDepartment(departmentOfPostCode(code), departments));
		}
		return List.copyOf(departments);
	}

	private static void addDepartment(String department, Set<String> departments) {
		if (department != null) {
			departments.add(department);
		}
	}

	/**
	 * The department of a French post code, CEDEX included: two digits, three
	 * for overseas, 2A or 2B for Corsica. Null for a code that is not one of a
	 * department (Monaco, overseas collectivities).
	 */
	static String departmentOfPostCode(String postCode) {
		Matcher matcher = postCode == null ? null : POST_CODE.matcher(postCode.strip());
		if (matcher == null || !matcher.find()) {
			return null;
		}
		String code = matcher.group(1);
		if (code.startsWith("97")) {
			String overseas = code.substring(0, 3);
			return Set.of("971", "972", "973", "974", "976").contains(overseas) ? overseas : null;
		}
		if (code.startsWith("98") || code.startsWith("00")) {
			return null;
		}
		if (code.startsWith("20")) {
			return code.compareTo("20200") < 0 ? "2A" : "2B";
		}
		return code.substring(0, 2);
	}

	private static List<MarketType> marketTypes(JsonNode notice) {
		String nature = text(notice.get("contract-nature-main-proc"));
		MarketType type = nature == null ? null : NATURES.get(nature);
		return type == null ? List.of() : List.of(type);
	}

	private static ProcedureType procedure(JsonNode notice) {
		String type = text(notice.get("procedure-type"));
		return type == null ? null : PROCEDURES.getOrDefault(type, ProcedureType.OTHER);
	}

	private static List<String> cpvCodes(JsonNode notice) {
		Set<String> codes = new LinkedHashSet<>();
		for (String code : texts(notice.get("classification-cpv"))) {
			Matcher matcher = CPV.matcher(code);
			if (matcher.find()) {
				codes.add(matcher.group(1));
			}
		}
		return List.copyOf(codes);
	}

	/** In euros only; zero means not given. */
	private static BigDecimal amount(JsonNode notice) {
		String value = text(notice.get("estimated-value-proc"));
		String currency = text(notice.get("estimated-value-cur-proc"));
		if (value == null || (currency != null && !currency.equals("EUR"))) {
			return null;
		}
		try {
			BigDecimal amount = new BigDecimal(value.strip());
			return amount.signum() > 0 ? amount : null;
		}
		catch (NumberFormatException ex) {
			return null;
		}
	}

	private static LocalDate publishedAt(JsonNode notice) {
		String date = text(notice.get("publication-date"));
		return date == null || date.length() < 10 ? null : LocalDate.parse(date.substring(0, 10));
	}

	/**
	 * The earliest deadline of the lots for the tenders, or, in a restricted
	 * or negotiated procedure that gives none yet, for the requests to
	 * participate: the first date the company must act. A date without a
	 * time is taken as the end of that day, Paris time, so it never looks
	 * passed too early.
	 */
	private static Instant deadline(JsonNode notice) {
		Instant tenders = earliest(notice, "deadline-receipt-tender");
		return tenders != null ? tenders : earliest(notice, "deadline-receipt-request");
	}

	private static Instant earliest(JsonNode notice, String field) {
		List<String> dates = texts(notice.get(field + "-date-lot"));
		List<String> times = texts(notice.get(field + "-time-lot"));
		Instant earliest = null;
		for (int i = 0; i < dates.size(); i++) {
			Instant deadline = deadline(dates.get(i), i < times.size() ? times.get(i) : null);
			if (deadline != null && (earliest == null || deadline.isBefore(earliest))) {
				earliest = deadline;
			}
		}
		return earliest;
	}

	private static Instant deadline(String date, String time) {
		try {
			LocalDate day = LocalDate.parse(date.substring(0, 10));
			if (time == null) {
				return day.atTime(LocalTime.of(23, 59)).atZone(ClockConfiguration.ZONE).toInstant();
			}
			OffsetTime at = time.length() > 8 ? OffsetTime.parse(time)
					: LocalTime.parse(time).atOffset(ClockConfiguration.ZONE.getRules().getOffset(day.atStartOfDay()));
			return day.atTime(at).toInstant();
		}
		catch (DateTimeParseException | IndexOutOfBoundsException ex) {
			return null;
		}
	}

	private static String noticeUrl(JsonNode notice, String number) {
		String url = text(notice.path("links").path("html").get("FRA"));
		return url != null ? url : NOTICE_URL + number;
	}

	/**
	 * Where the consultation documents are: the address given for the
	 * documents, or else for the submission, or else the buyer profile.
	 */
	private static String platformUrl(JsonNode notice) {
		for (String field : List.of("document-url-lot", "submission-url-lot", "buyer-profile")) {
			List<String> urls = texts(notice.get(field));
			if (!urls.isEmpty()) {
				return urls.getFirst().replace("&amp;", "&");
			}
		}
		return null;
	}

	/**
	 * The lots with their title, by number, when the notice has several;
	 * left out if their identifiers and titles do not match one to one.
	 */
	private static List<LotDetails> lots(JsonNode notice) {
		List<String> identifiers = texts(notice.get("identifier-lot"));
		List<String> titles = languageTexts(notice.get("title-lot"));
		if (identifiers.size() < 2 || identifiers.size() != titles.size()) {
			return List.of();
		}
		List<LotDetails> lots = new ArrayList<>();
		for (int i = 0; i < identifiers.size(); i++) {
			Matcher matcher = LOT.matcher(identifiers.get(i));
			String number = matcher.matches() ? matcher.group(1) : identifiers.get(i);
			lots.add(new LotDetails(number, clean(titles.get(i)), null));
		}
		lots.sort(Comparator.comparing((LotDetails lot) -> lot.number().length()).thenComparing(LotDetails::number));
		return List.copyOf(lots);
	}

	/** The text in French, or else in another language given. */
	private static String language(JsonNode node) {
		List<String> texts = languageTexts(node);
		return texts.isEmpty() ? null : clean(texts.getFirst());
	}

	private static List<String> languageTexts(JsonNode node) {
		if (node == null || !node.isObject()) {
			return texts(node);
		}
		for (String language : LANGUAGES) {
			if (node.has(language)) {
				return texts(node.get(language));
			}
		}
		return node.values().stream().findFirst().map(TedNoticeParser::texts).orElse(List.of());
	}

	private static List<String> texts(JsonNode node) {
		if (node == null || node.isNull() || node.isMissingNode()) {
			return List.of();
		}
		if (node.isArray()) {
			return node.values().stream().map(TedNoticeParser::text).filter(Objects::nonNull).toList();
		}
		String text = text(node);
		return text == null ? List.of() : List.of(text);
	}

	private static String text(JsonNode node) {
		if (node == null || node.isNull() || node.isMissingNode() || node.isContainer()) {
			return null;
		}
		String value = node.asString();
		return value.isBlank() ? null : value.strip();
	}

	private static String clean(String text) {
		return text == null ? null : text.replaceAll("\\s+", " ").strip();
	}

}
