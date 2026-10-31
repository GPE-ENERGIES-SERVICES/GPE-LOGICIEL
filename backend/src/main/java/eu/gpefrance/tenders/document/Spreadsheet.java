package eu.gpefrance.tenders.document;

import java.math.BigDecimal;
import java.util.List;

/**
 * What was read from a spreadsheet (XLS, XLSX, ODS, CSV), cell by cell, with
 * where each value comes from. Nothing is computed here: a formula keeps the
 * value saved in the file, or none when the file has none.
 * @param warnings what could not be read in full (sheet cut at the limits,
 * cells in error), shown with the document
 */
public record Spreadsheet(List<Sheet> sheets, List<String> warnings) {

	public boolean isEmpty() {
		return sheets.stream().allMatch(sheet -> sheet.rows().isEmpty());
	}

	/**
	 * @param truncated whether rows or columns beyond the limits were left out
	 */
	public record Sheet(String name, List<Row> rows, boolean truncated) {
	}

	/**
	 * A row with at least one cell holding something.
	 * @param number row number as shown by the spreadsheet software, from 1
	 */
	public record Row(int number, List<Cell> cells) {

		public Cell cell(int column) {
			return cells.stream().filter(cell -> cell.column() == column).findFirst().orElse(null);
		}

	}

	/**
	 * One cell holding a value or a formula.
	 * @param ref coordinates as shown by the spreadsheet software (B12)
	 * @param column column index, from 0
	 * @param text the text of a text cell; for a CSV file, the value as
	 * written, numbers included
	 * @param number the value of a number cell, or the number read in a CSV
	 * value
	 * @param formula the formula, without its leading equal sign, or null
	 * @param valueMissing a formula whose result is not saved in the file: its
	 * value is unknown and never computed here
	 */
	public record Cell(String ref, int column, CellType type, String text, BigDecimal number, String formula,
			boolean valueMissing) {

		/** The number held, a number written in a text cell included, or null. */
		public BigDecimal numeric() {
			if (number != null) {
				return number;
			}
			return type == CellType.TEXT ? FrenchNumbers.parse(text) : null;
		}

	}

	public enum CellType {

		TEXT, NUMBER, DATE, BOOLEAN, ERROR,
		/** A formula without saved result. */
		NO_VALUE

	}

	/** Column letters as shown by the spreadsheet software: 0 is A, 26 is AA. */
	public static String columnName(int column) {
		StringBuilder name = new StringBuilder();
		for (int index = column + 1; index > 0; index = (index - 1) / 26) {
			name.insert(0, (char) ('A' + (index - 1) % 26));
		}
		return name.toString();
	}

}
