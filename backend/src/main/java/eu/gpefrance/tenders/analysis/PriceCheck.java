package eu.gpefrance.tenders.analysis;

import java.math.BigDecimal;
import java.util.List;

/**
 * What plain code found in a sheet of a price schedule (DPGF, BPU, DQE),
 * without AI: the columns recognised, the lines of work, the sums and the
 * checks of the amounts. Every figure here is read in the file or computed
 * from it, never estimated.
 * @param documentId the document, in the dossier
 * @param columns the columns recognised from the header, empty when none was
 * @param headerRow the row of the header, or null
 * @param items lines of work found under the header
 * @param withQuantity lines with a quantity
 * @param withUnitPrice lines with a unit price
 * @param withAmount lines with an amount
 * @param textPrices prices written as words (PM, inclus, sans objet...)
 * @param computedTotal sum of the amounts of the lines, computed here; null
 * when no line has an amount
 * @param amountBasis HT or TTC when the header of the amounts says it, or null
 * @param blankPrices a schedule to be filled in by the candidates: lines
 * without any price
 * @param totals totals written in the sheet, each compared with the sum of its
 * lines
 * @param issues inconsistencies and values that could not be read, first ones
 * only
 * @param moreIssues issues left out of the list
 */
public record PriceCheck(Long documentId, String fileName, String sheet, List<Column> columns, Integer headerRow,
		int items, int withQuantity, int withUnitPrice, int withAmount, int textPrices, BigDecimal computedTotal,
		String amountBasis, boolean blankPrices, List<Total> totals, List<Issue> issues, int moreIssues) {

	public enum Role {

		REFERENCE, DESIGNATION, UNIT, QUANTITY, UNIT_PRICE, AMOUNT

	}

	/**
	 * @param ref the cell of its header
	 */
	public record Column(Role role, String ref, String header) {
	}

	/**
	 * A total written in the sheet.
	 * @param kind HT for a total or subtotal, VAT, TTC
	 * @param computed what the lines add up to, or the HT plus the VAT for a
	 * TTC total; null when nothing to compare with
	 * @param consistent whether both agree to the cent, null when not compared
	 */
	public record Total(String kind, String label, String ref, int row, BigDecimal declared, BigDecimal computed,
			Boolean consistent) {
	}

	/**
	 * @param kind LINE_MISMATCH, TOTAL_MISMATCH, VAT_MISMATCH, NOT_SAVED (a
	 * formula without saved value), ERROR_CELL, NO_HEADER
	 * @param ref the cell concerned, or null
	 * @param row the row concerned, or null
	 */
	public record Issue(String kind, String message, String ref, Integer row) {
	}

}
