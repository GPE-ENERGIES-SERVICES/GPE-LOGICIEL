package eu.gpefrance.tenders.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import eu.gpefrance.tenders.analysis.Decision.Check;
import eu.gpefrance.tenders.analysis.Decision.ObstacleKind;
import eu.gpefrance.tenders.analysis.Decision.Point;
import eu.gpefrance.tenders.analysis.Decision.Verdict;
import tools.jackson.databind.JsonNode;

/**
 * Reads the decision part of the answer of the model. Like the grid, it is
 * never trusted: lists are cut to their maximum, unknown values fall back to
 * the most careful one, and each quote is looked for in the cited document.
 * A quote not found is dropped, never shown, and an obstacle without a found
 * quote, or deduced, is never presented as confirmed.
 */
final class DecisionReader {

	static final int MAX_STRENGTHS = 3;

	static final int MAX_OBSTACLES = 6;

	static final int MAX_CHECKS = 5;

	static final int MAX_LIMITS = 6;

	private static final int MAX_TEXT_LENGTH = 600;

	private DecisionReader() {
	}

	/**
	 * @param pages text of each page of the documents sent, by document and
	 * page number
	 * @return the decision, or null when the answer has none
	 */
	static Decision read(String answer, Map<Long, Map<Integer, String>> pages) {
		JsonNode node = AnswerReader.parse(answer).path("decision");
		if (!node.isObject()) {
			return null;
		}
		Verdict verdict = switch (node.path("verdict").asString("").toUpperCase(Locale.ROOT)) {
			case "PRIORITY" -> Verdict.PRIORITY;
			case "EXAMINE" -> Verdict.EXAMINE;
			case "POOR_FIT" -> Verdict.POOR_FIT;
			default -> Verdict.INSUFFICIENT;
		};
		List<Point> strengths = new ArrayList<>();
		for (JsonNode entry : node.path("strengths")) {
			point(entry, null, pages).ifPresent(strengths::add);
		}
		List<Point> obstacles = new ArrayList<>();
		for (JsonNode entry : node.path("obstacles")) {
			point(entry, kind(entry), pages).ifPresent(obstacles::add);
		}
		List<Check> checks = new ArrayList<>();
		for (JsonNode entry : node.path("checks")) {
			String action = text(entry.path("action"));
			if (action != null) {
				Long documentId = number(entry.path("document_id"));
				checks.add(new Check(action, text(entry.path("why")),
						documentId != null && pages.containsKey(documentId) ? documentId : null));
			}
		}
		List<String> limits = new ArrayList<>();
		for (JsonNode entry : node.path("limits")) {
			String limit = text(entry);
			if (limit != null) {
				limits.add(limit);
			}
		}
		return new Decision(verdict, text(node.path("conclusion")), first(strengths, MAX_STRENGTHS),
				first(obstacles, MAX_OBSTACLES), first(checks, MAX_CHECKS), first(limits, MAX_LIMITS));
	}

	private static ObstacleKind kind(JsonNode entry) {
		return switch (entry.path("kind").asString("").toUpperCase(Locale.ROOT)) {
			case "CONFIRMED" -> ObstacleKind.CONFIRMED;
			case "MISSING" -> ObstacleKind.MISSING;
			default -> ObstacleKind.TO_CHECK;
		};
	}

	private static Optional<Point> point(JsonNode entry, ObstacleKind kind, Map<Long, Map<Integer, String>> pages) {
		String text = text(entry.path("text"));
		if (text == null) {
			return Optional.empty();
		}
		boolean deduced = "DEDUCTION".equalsIgnoreCase(entry.path("basis").asString(""));
		String quote = text(entry.path("quote"));
		Long documentId = number(entry.path("document_id"));
		Long page = number(entry.path("page"));
		if (documentId == null || !pages.containsKey(documentId)) {
			documentId = null;
		}
		Optional<Integer> found = quote == null || documentId == null ? Optional.empty()
				: CitationVerifier.locate(quote, pages.get(documentId),
						page == null || page < 1 || page > Integer.MAX_VALUE ? null : page.intValue());
		if (kind == ObstacleKind.CONFIRMED && (found.isEmpty() || deduced)) {
			kind = ObstacleKind.TO_CHECK;
		}
		return Optional.of(new Point(text, kind, deduced, found.isPresent() ? quote : null, documentId,
				found.orElse(null), found.isPresent()));
	}

	private static <T> List<T> first(List<T> items, int max) {
		return List.copyOf(items.size() <= max ? items : items.subList(0, max));
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
