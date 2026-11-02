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

	/** Tokens billed by the provider for a call that failed after its answer (cut, refused), or null. */
	private final ExtractionClient.ModelAnswer usage;

	public ProviderException(FailureKind kind, String message) {
		this(kind, message, (ExtractionClient.ModelAnswer) null);
	}

	/**
	 * @param usage the tokens of an answer received but not usable: billed, so
	 * counted in the monthly ceiling
	 */
	public ProviderException(FailureKind kind, String message, ExtractionClient.ModelAnswer usage) {
		super(message);
		this.kind = kind;
		this.usage = usage;
	}

	public ProviderException(FailureKind kind, String message, Throwable cause) {
		super(message, cause);
		this.kind = kind;
		this.usage = null;
	}

	public FailureKind kind() {
		return kind;
	}

	public ExtractionClient.ModelAnswer usage() {
		return usage;
	}

}
