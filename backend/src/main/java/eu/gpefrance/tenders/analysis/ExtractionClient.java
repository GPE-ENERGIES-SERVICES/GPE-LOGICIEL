package eu.gpefrance.tenders.analysis;

/**
 * Access to the AI model, isolated here so that the provider can be changed
 * without touching the rest of the analysis. An implementation only sends the
 * text and returns the raw answer: the instructions, the reading of the
 * answer and the checks stay in the application.
 */
public interface ExtractionClient {

	/**
	 * @return the model used, recorded with each analysis, or {@code null}
	 * when no provider is configured
	 */
	String model();

	/**
	 * @param instructions what to extract and the expected JSON format
	 * @param documents text of the documents, page by page
	 * @return the answer of the model and the tokens it consumed
	 */
	ModelAnswer complete(String instructions, String documents);

	record ModelAnswer(String text, int inputTokens, int outputTokens) {
	}

}
