package eu.gpefrance.tenders.analysis;

/**
 * Access to an AI provider, isolated here so that the provider can be changed
 * without touching the rest of the analysis. An implementation only sends the
 * request and returns the raw answer: the instructions, the reading of the
 * answer and the checks stay in the application. No class of a provider SDK
 * may appear outside its implementation.
 * <p>
 * Implementations are Spring components; the one whose {@link #provider()}
 * equals {@code analysis.provider} is used, and only when
 * {@link AnalysisGate} allows it. None is part of the application today.
 */
public interface ExtractionClient {

	/**
	 * @return identifier chosen with {@code analysis.provider}, such as
	 * "mistral" or "anthropic"
	 */
	String provider();

	/**
	 * @return the model used, recorded with each analysis, or {@code null}
	 * when the client cannot be used (key missing)
	 */
	String model();

	/**
	 * @return the answer of the model and the tokens it consumed
	 * @throws ProviderException for any failure of the provider
	 */
	ModelAnswer complete(ExtractionRequest request);

	record ModelAnswer(String text, int inputTokens, int outputTokens) {
	}

}
