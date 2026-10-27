package eu.gpefrance.tenders.analysis;

/**
 * A typed failure of the AI provider. An {@link ExtractionClient} turns the
 * errors of its API (HTTP status, timeouts) into one of these, so that the
 * queue knows whether to try again. The message must never hold the key, the
 * headers nor the text of the documents: it is stored and logged, after
 * {@link SecretRedactor}.
 */
public class ProviderException extends RuntimeException {

	private final FailureKind kind;

	public ProviderException(FailureKind kind, String message) {
		super(message);
		this.kind = kind;
	}

	public ProviderException(FailureKind kind, String message, Throwable cause) {
		super(message, cause);
		this.kind = kind;
	}

	public FailureKind kind() {
		return kind;
	}

}
