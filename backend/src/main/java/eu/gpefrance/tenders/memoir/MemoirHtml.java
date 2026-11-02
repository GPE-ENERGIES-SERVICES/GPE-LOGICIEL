package eu.gpefrance.tenders.memoir;

import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;

/**
 * The content of a section, kept as a small subset of HTML: headings,
 * paragraphs, lists, emphasis, tables, and figures holding an image of the
 * memoir or a placeholder. Anything else (scripts, styles, links, outside
 * images, attributes) is removed, whoever wrote it, a user or the AI.
 */
final class MemoirHtml {

	/** Only the images of the memoir, served by the application. */
	private static final Pattern IMAGE = Pattern.compile("/api/memoirs/files/\\d+");

	private static final Safelist SAFELIST = Safelist.none()
		.addTags("p", "br", "h3", "h4", "h5", "ul", "ol", "li", "strong", "b", "em", "i", "u", "blockquote", "figure",
				"figcaption", "img", "table", "thead", "tbody", "tr", "th", "td")
		.addAttributes("img", "src", "alt")
		.addAttributes("figure", "class", "data-kind")
		.preserveRelativeLinks(true);

	private MemoirHtml() {
	}

	/** The content as stored: allowed tags only, empty when nothing is written. */
	static String clean(String html) {
		if (html == null || html.isBlank()) {
			return null;
		}
		Document document = Jsoup.parseBodyFragment(Jsoup.clean(html, "https://memoir.local", SAFELIST));
		for (Element image : document.select("img")) {
			if (!IMAGE.matcher(image.attr("src")).matches()) {
				image.remove();
			}
		}
		for (Element figure : document.select("figure")) {
			if (figure.hasAttr("class") && !figure.attr("class").equals("placeholder")) {
				figure.removeAttr("class");
			}
			if (figure.hasAttr("data-kind") && !figure.attr("data-kind").matches("[a-z]{1,20}")) {
				figure.removeAttr("data-kind");
			}
		}
		String cleaned = document.body().html().strip();
		return Jsoup.parse(cleaned).text().isBlank() && document.select("img, figure").isEmpty() ? null : cleaned;
	}

	/** The text alone, for the AI and the counts. */
	static String text(String html) {
		return html == null ? "" : Jsoup.parse(html).text();
	}

	static String escape(String text) {
		return text == null ? "" : org.jsoup.nodes.Entities.escape(text);
	}

}
