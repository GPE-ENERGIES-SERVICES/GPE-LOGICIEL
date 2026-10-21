package eu.gpefrance.tenders.collect.awsolutions;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Reads the public pages of marches-publics.info: the list of the notices
 * found by a search, and the page of one notice, into JSON. Only what the
 * pages show is kept, as written; {@link AwsNoticeParser} interprets it.
 */
final class AwsPages {

	private static final Pattern PUBLISHED = Pattern.compile("Publié le\\s*(\\d{2}/\\d{2}/\\d{2})");

	private static final Pattern DEADLINE = Pattern.compile("(\\d{2}/\\d{2}/\\d{2}) à (\\d{1,2})h(\\d{2})");

	private static final Pattern BUYER = Pattern.compile("^(.*?)\\s*\\((\\w{5})\\)\\s*$");

	private static final Pattern REFERENCE = Pattern.compile("\\[réf\\.\\s*(.+?)\\s*]");

	private static final Pattern LOTS = Pattern.compile("Marché alloti\\s*:\\s*(\\d+)\\s*lots");

	private static final Pattern CONSULTATION = Pattern.compile("[?&]IDM=(\\d+)");

	private static final Pattern PAGE = Pattern.compile("[?&]pager_t=(\\d+)");

	private static final Pattern SIRET = Pattern.compile("SIRET\\s*(\\d{14})");

	private static final String COMPLEMENTARY_CPV = "Code CPV complémentaire";

	private AwsPages() {
	}

	/**
	 * The notices of a page of search results, in the order shown.
	 */
	static List<ObjectNode> listing(Document page) {
		List<ObjectNode> notices = new ArrayList<>();
		for (Element entry : page.select("div#entity")) {
			ObjectNode notice = JsonNodeFactory.instance.objectNode();
			String dates = entry.select(".affiche_date_avis").text();
			notice.put("publishedOn", group(PUBLISHED, dates, 1));
			Matcher deadline = DEADLINE.matcher(dates);
			notice.put("deadline", deadline.find() ? deadline.group() : null);
			Element heading = entry.selectFirst("h2.h2-avis");
			Matcher buyer = BUYER.matcher(heading == null ? "" : heading.text());
			if (buyer.matches()) {
				notice.put("buyer", buyer.group(1));
				notice.put("postCode", buyer.group(2));
			}
			else {
				notice.put("buyer", heading == null ? null : blankToNull(heading.text()));
			}
			Element titleBox = entry.selectFirst("div#titre_box");
			notice.put("reference", titleBox == null ? null : group(REFERENCE, titleBox.text(), 1));
			notice.put("title", titleBox == null ? null : blankToNull(titleBox.ownText()));
			notice.put("lots", titleBox == null ? null : group(LOTS, titleBox.text(), 1));
			Element link = entry.selectFirst("a[title=\"Consulter l'avis\"]");
			String noticeUrl = link == null ? null : blankToNull(link.absUrl("href"));
			notice.put("noticeUrl", noticeUrl);
			notice.put("noticeId", noticeUrl == null ? null : noticeId(noticeUrl));
			Element dce = entry.selectFirst("a[title=\"Dossier de Consultation des Entreprises\"]");
			String dceUrl = dce == null ? null : blankToNull(dce.absUrl("href"));
			notice.put("dceUrl", dceUrl);
			notice.put("consultationId", dceUrl == null ? null : group(CONSULTATION, dceUrl, 1));
			notices.add(notice);
		}
		return notices;
	}

	/**
	 * The number of pages of the results, from the page links; 1 without them.
	 */
	static int pageCount(Document page) {
		int last = 1;
		for (Element link : page.select(".pagerfanta a[href]")) {
			String number = group(PAGE, link.attr("href"), 1);
			if (number != null) {
				last = Math.max(last, Integer.parseInt(number));
			}
		}
		return last;
	}

	/**
	 * The page of one notice: its heading, its buyer and every labelled row
	 * of its tables (label in bold or in a cell of the notice table), the
	 * first one of each label, except the complementary CPV codes, which are
	 * all kept.
	 */
	static ObjectNode notice(Document page) {
		ObjectNode notice = JsonNodeFactory.instance.objectNode();
		Element heading = page.selectFirst("td.AW_Titre");
		notice.put("heading", heading == null ? null : blankToNull(heading.text()));
		Optional<Element> buyerBlock = page.select("td")
			.stream()
			.filter(td -> SIRET.matcher(td.ownText()).find())
			.findFirst();
		notice.put("buyer", buyerBlock.map(td -> td.selectFirst("b")).map(Element::text).orElse(null));
		notice.put("siret", buyerBlock.map(td -> group(SIRET, td.ownText(), 1)).orElse(null));
		ObjectNode fields = notice.putObject("fields");
		for (Element row : page.select("tr")) {
			List<Element> cells = row.children().stream().filter(cell -> cell.tagName().equals("td")).toList();
			if (cells.size() < 2 || !isLabel(cells.getFirst())) {
				continue;
			}
			String label = blankToNull(cells.getFirst().text());
			String value = blankToNull(cells.get(1).text());
			if (label == null || value == null) {
				continue;
			}
			if (!fields.has(label)) {
				fields.put(label, value);
			}
			else if (label.equals(COMPLEMENTARY_CPV)) {
				fields.put(label, fields.path(label).asString() + " | " + value);
			}
		}
		return notice;
	}

	private static boolean isLabel(Element cell) {
		return cell.selectFirst("b") != null || cell.className().startsWith("AW_TableM_Bloc");
	}

	/**
	 * The name of the page of a notice, such as "MPI-pub-20262941419".
	 */
	static String noticeId(String noticeUrl) {
		String name = noticeUrl.substring(noticeUrl.lastIndexOf('/') + 1);
		int extension = name.lastIndexOf('.');
		return blankToNull(extension > 0 ? name.substring(0, extension) : name);
	}

	private static String group(Pattern pattern, String text, int group) {
		Matcher matcher = pattern.matcher(text);
		return matcher.find() ? matcher.group(group).strip() : null;
	}

	private static String blankToNull(String text) {
		if (text == null) {
			return null;
		}
		String stripped = text.replace(' ', ' ').strip();
		return stripped.isEmpty() ? null : stripped;
	}

}
