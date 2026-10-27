package eu.gpefrance.tenders.analysis;

import java.time.Duration;

/**
 * What is given to the provider for one analysis. Everything the application
 * decides (instructions, expected format, limits) is here; the provider only
 * adapts it to its API: a provider without structured output may ignore the
 * schema, the answer is checked anyway.
 * @param analysisType kind of analysis, {@link #DCE_SUMMARY} only today
 * @param promptVersion version of the grid and of the instructions
 * @param instructions system instructions: what to extract and how to answer
 * @param documents text of the documents, page by page, each tagged with its
 * document id and page number
 * @param outputSchema JSON Schema of the expected answer
 * @param maxOutputTokens longest answer allowed
 * @param temperature 0: the same documents should give the same answer
 * @param timeout longest wait for the answer; the provider must give up after
 */
public record ExtractionRequest(String analysisType, String promptVersion, String instructions, String documents,
		String outputSchema, int maxOutputTokens, double temperature, Duration timeout) {

	public static final String DCE_SUMMARY = "DCE_SUMMARY";

}
