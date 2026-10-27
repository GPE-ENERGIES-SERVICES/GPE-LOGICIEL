package eu.gpefrance.tenders.analysis;

import java.util.regex.Pattern;

/**
 * Cleans a provider error before it is stored or logged: anything that looks
 * like a key or an authorization header is masked, and the message is cut so
 * that no long excerpt of a document can slip into it.
 */
final class SecretRedactor {

	static final String MASK = "[masqué]";

	private static final int MAX_LENGTH = 500;

	/** The credentials of an authorization header. */
	private static final Pattern SCHEME = Pattern.compile("(?i)\\b(bearer|basic)\\s+[^\\s\",;]+");

	/** A value given after a name that says it is secret. */
	private static final Pattern NAMED = Pattern.compile(
			"(?i)\\b(api[_-]?key|x-api-key|token|secret|password)(\\s*[:=]\\s*|\\s+)(\"?)[^\\s\",;]+");

	/** Values shaped like a key, wherever they are. */
	private static final Pattern KEY_LIKE = Pattern.compile("\\b(?:sk|pk|rk)-[A-Za-z0-9_-]{8,}|\\b[A-Za-z0-9_-]{40,}\\b");

	private SecretRedactor() {
	}

	static String redact(String message) {
		if (message == null) {
			return null;
		}
		String cleaned = SCHEME.matcher(message).replaceAll("$1 " + MASK);
		cleaned = NAMED.matcher(cleaned).replaceAll("$1$2$3" + MASK);
		cleaned = KEY_LIKE.matcher(cleaned).replaceAll(MASK);
		return cleaned.length() <= MAX_LENGTH ? cleaned : cleaned.substring(0, MAX_LENGTH) + "…";
	}

}
