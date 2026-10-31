package eu.gpefrance.tenders.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import eu.gpefrance.tenders.analysis.FinancialResult.Point;
import eu.gpefrance.tenders.document.SpreadsheetText;
import tools.jackson.databind.JsonNode;

/**
 * Reads the answer of the model to an analysis of the prices, never trusted:
 * lists cut to their maximum, and each cited text looked for at the place
 * given, the row of a sheet or the page of a PDF. A text not found there is
 * dropped and the point stays unverified.
 */
final class FinancialReader {

	static final int MAX_ITEMS = 12;

	static final int MAX_POINTS = 8;

	static final int MAX_LIMITS = 4;

	private static final int MAX_TEXT_LENGTH = 600;

	private FinancialReader() {
	}

	/**
	 * @param pages text of each page of the documents sent, by document and
	 * page number
	 * @throws AnswerReader.UnreadableAnswerException when the answer is not the
	 * expected JSON
	 */
	static FinancialResult read(String answer, Map<Long, Map<Integer, String>> pages, List<PriceCheck> checks,
			List<String> notSent) {
		JsonNode root = AnswerReader.parse(answer);
		if (!root.path("items").isArray() && !root.path("points").isArray()) {
			throw new AnswerReader.UnreadableAnswerException("les listes « items » et « points » sont absentes");
		}
		List<String> limits = new ArrayList<>();
		for (JsonNode limit : root.path("limits")) {
			String text = text(limit);
			if (text != null && limits.size() < MAX_LIMITS) {
				limits.add(text);
			}
		}
		return new FinancialResult(text(root.path("overview")), points(root.path("items"), pages, MAX_ITEMS),
				points(root.path("points"), pages, MAX_POINTS), List.copyOf(limits), checks, notSent);
	}

	private static List<Point> points(JsonNode entries, Map<Long, Map<Integer, String>> pages, int max) {
		List<Point> points = new ArrayList<>();
		for (JsonNode entry : entries) {
			String text = text(entry.path("text"));
			if (text == null) {
				continue;
			}
			points.add(point(text, entry, pages));
			if (points.size() >= max) {
				break;
			}
		}
		return List.copyOf(points);
	}

	private static Point point(String text, JsonNode entry, Map<Long, Map<Integer, String>> pages) {
		Long documentId = number(entry.path("document_id"));
		Long pageNumber = number(entry.path("page"));
		Long row = number(entry.path("row"));
		String quote = text(entry.path("quote"));
		Map<Integer, String> document = documentId == null ? null : pages.get(documentId);
		if (document == null) {
			return new Point(text, null, null, null, null, null, false);
		}
		String page = pageNumber == null || pageNumber > Integer.MAX_VALUE ? null
				: document.get(pageNumber.intValue());
		if (page == null) {
			return new Point(text, documentId, null, null, null, null, false);
		}
		String sheet = SpreadsheetText.sheetName(page).orElse(null);
		Integer rowNumber = row == null || row < 1 || row > Integer.MAX_VALUE ? null : row.intValue();
		boolean found = quote != null && found(quote, page, sheet, rowNumber);
		return new Point(text, documentId, pageNumber.intValue(), sheet == null ? null : rowNumber, sheet,
				found ? quote : null, found);
	}

	/**
	 * In a sheet, the quote must be in the cited row; in a PDF, on the cited
	 * page.
	 */
	private static boolean found(String quote, String page, String sheet, Integer row) {
		if (sheet == null) {
			return CitationVerifier.locate(quote, Map.of(1, page), 1).isPresent();
		}
		if (row == null) {
			return false;
		}
		Optional<String> line = SpreadsheetText.rowLine(page, row);
		String compactQuote = CitationVerifier.compact(quote);
		return line.isPresent() && compactQuote.length() >= 3
				&& CitationVerifier.compact(line.get()).contains(compactQuote);
	}

	private static String text(JsonNode node) {
		if (!node.isString()) {
			return null;
		}
		String text = node.asString().strip();
		if (text.isEmpty()) {
			return null;
		}
		return text.length() <= MAX_TEXT_LENGTH ? text : text.substring(0, MAX_TEXT_LENGTH);
	}

	private static Long number(JsonNode node) {
		if (node.isIntegralNumber()) {
			return node.asLong();
		}
		if (node.isString()) {
			try {
				return Long.valueOf(node.asString().strip());
			}
			catch (NumberFormatException ex) {
				return null;
			}
		}
		return null;
	}

}
