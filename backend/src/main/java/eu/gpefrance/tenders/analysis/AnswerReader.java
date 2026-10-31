package eu.gpefrance.tenders.analysis;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the JSON answer of the model into one proposal per grid answer. The
 * answer is never trusted: unknown keys are dropped, sources outside the
 * documents sent are removed, and a grid field left without answer is kept
 * as a value to check.
 */
final class AnswerReader {

	private static final int MAX_TEXT_LENGTH = 4000;

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private AnswerReader() {
	}

	/**
	 * @param documentIds the documents that were sent to the model
	 * @throws UnreadableAnswerException when the answer is not the expected JSON
	 */
	static List<Proposal> read(String answer, Set<Long> documentIds) {
		JsonNode entries = parse(answer).path("fields");
		if (!entries.isArray()) {
			throw new UnreadableAnswerException("la liste « fields » est absente");
		}
		List<Proposal> proposals = new ArrayList<>();
		Set<String> answered = new HashSet<>();
		for (JsonNode entry : entries) {
			GridField field = ExtractionGrid.find(entry.path("key").asString("")).orElse(null);
			if (field == null || !entry.isObject()) {
				continue;
			}
			Proposal proposal = proposal(field, entry, documentIds);
			if (!field.multiple() && answered.contains(field.key())) {
				markContradictory(proposals, proposal);
				continue;
			}
			answered.add(field.key());
			proposals.add(proposal);
		}
		for (GridField field : ExtractionGrid.FIELDS) {
			if (!answered.contains(field.key())) {
				proposals.add(new Proposal(field.key(), FieldState.UNVERIFIED, null, null, null, null));
			}
		}
		return proposals;
	}

	/**
	 * A single field answered twice with two different values: the first
	 * answer is kept, as contradictory, never silently preferred.
	 */
	private static void markContradictory(List<Proposal> proposals, Proposal other) {
		for (int i = 0; i < proposals.size(); i++) {
			Proposal first = proposals.get(i);
			if (first.key().equals(other.key()) && other.value() != null && first.value() != null
					&& !first.value().equalsIgnoreCase(other.value())) {
				proposals.set(i, first.withCheck(FieldState.CONTRADICTORY, first.page()));
			}
		}
	}

	static JsonNode parse(String answer) {
		int start = answer == null ? -1 : answer.indexOf('{');
		int end = answer == null ? -1 : answer.lastIndexOf('}');
		if (start < 0 || end < start) {
			throw new UnreadableAnswerException("aucun objet JSON");
		}
		try {
			return JSON.readTree(answer.substring(start, end + 1));
		}
		catch (JacksonException ex) {
			throw new UnreadableAnswerException("JSON invalide");
		}
	}

	private static Proposal proposal(GridField field, JsonNode entry, Set<Long> documentIds) {
		FieldState state = switch (entry.path("status").asString("").toUpperCase(Locale.ROOT)) {
			case "NOT_INDICATED" -> FieldState.NOT_INDICATED;
			case "AMBIGUOUS" -> FieldState.AMBIGUOUS;
			case "CONTRADICTORY" -> FieldState.CONTRADICTORY;
			default -> FieldState.UNVERIFIED;
		};
		if (state == FieldState.NOT_INDICATED) {
			return new Proposal(field.key(), state, null, null, null, null);
		}
		Long documentId = number(entry.path("document_id"));
		Long page = number(entry.path("page"));
		if (documentId == null || !documentIds.contains(documentId)) {
			documentId = null;
			page = null;
		}
		return new Proposal(field.key(), state, text(entry.path("value")), text(entry.path("quote")), documentId,
				page == null || page < 1 || page > Integer.MAX_VALUE ? null : page.intValue());
	}

	private static String text(JsonNode node) {
		if (node.isMissingNode() || node.isNull()) {
			return null;
		}
		String text = node.isString() ? node.asString() : node.toString();
		text = text.strip();
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

	/**
	 * One answer of the model, before verification.
	 */
	record Proposal(String key, FieldState state, String value, String quote, Long documentId, Integer page) {

		Proposal withCheck(FieldState newState, Integer newPage) {
			return new Proposal(key, newState, value, quote, documentId, newPage);
		}

	}

	static class UnreadableAnswerException extends RuntimeException {

		UnreadableAnswerException(String reason) {
			super("Réponse de l'IA illisible : " + reason);
		}

	}

}
