package eu.gpefrance.tenders.analysis;

import java.util.List;

/**
 * The analysis of the price schedules: the checks made by plain code on the
 * spreadsheets, then the synthesis of the model, each of its points with the
 * place it comes from, looked for in the document.
 * @param overview what the schedules hold, in a few sentences, or null
 * @param items main lines of work: quantities, units, amounts written
 * @param points what to check: inconsistencies, missing values, ambiguities
 * @param limits what the analysis could not take into account
 * @param checks checks of each sheet, made without AI
 * @param notSent financial pieces left out (unreadable, too long), with why
 */
public record FinancialResult(String overview, List<Point> items, List<Point> points, List<String> limits,
		List<PriceCheck> checks, List<String> notSent) {

	/**
	 * @param page the page of the document (part of a sheet, or page of a PDF)
	 * @param row the row of the sheet, null for a PDF
	 * @param sheet the sheet, read from the page, null for a PDF
	 * @param quote the cited text, kept only when found at that place
	 * @param verified whether the cited text was found at that place
	 */
	public record Point(String text, Long documentId, Integer page, Integer row, String sheet, String quote,
			boolean verified) {
	}

}
