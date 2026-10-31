package eu.gpefrance.tenders.document;

import java.util.ArrayList;
import java.util.List;

import eu.gpefrance.tenders.document.PdfTable.Cell;
import eu.gpefrance.tenders.document.PdfTable.Column;
import eu.gpefrance.tenders.document.PdfTable.Row;

/**
 * Turns the tables rebuilt from a PDF into sheets, so that a price schedule
 * in PDF is checked by the same code as a spreadsheet, and writes them for
 * the model with the page and position of every row. In a sheet, row 1 is the
 * header and each next row is a line of the PDF: column letters follow the
 * columns of the table, values are kept as written.
 */
public final class PdfTableText {

	private PdfTableText() {
	}

	/** The name of the sheet of a table, saying whether its structure is reliable. */
	public static String name(PdfTable table, int number) {
		List<Integer> pages = table.pages();
		String range = pages.size() == 1 ? "p. " + pages.getFirst() : "p. " + pages.getFirst() + " à " + pages.getLast();
		return "Tableau " + number + " (" + range + ")" + (table.reliable() ? "" : " — structure ambiguë");
	}

	public static Spreadsheet.Sheet sheet(PdfTable table, int number) {
		List<Spreadsheet.Row> rows = new ArrayList<>();
		List<Spreadsheet.Cell> header = new ArrayList<>();
		for (int index = 0; index < table.columns().size(); index++) {
			header.add(text(index, 1, table.columns().get(index).label()));
		}
		rows.add(new Spreadsheet.Row(1, List.copyOf(header)));
		for (int i = 0; i < table.rows().size(); i++) {
			int rowNumber = i + 2;
			rows.add(new Spreadsheet.Row(rowNumber, table.rows()
				.get(i)
				.cells()
				.stream()
				.map(cell -> text(cell.column(), rowNumber, cell.text()))
				.toList()));
		}
		return new Spreadsheet.Sheet(name(table, number), List.copyOf(rows), false);
	}

	private static Spreadsheet.Cell text(int column, int row, String text) {
		return new Spreadsheet.Cell(Spreadsheet.columnName(column) + row, column, Spreadsheet.CellType.TEXT, text,
				null, null, false);
	}

	/** The page of a row of the sheet of a table, or null for its header. */
	public static Integer page(PdfTable table, int row) {
		return row < 2 || row - 2 >= table.rows().size() ? null : table.rows().get(row - 2).page();
	}

	/**
	 * The tables of a document, for the model: columns with their position,
	 * then one line per row with its page, its height on the page and each
	 * cell; a doubtful cell is marked.
	 */
	public static String render(Long documentId, String fileName, List<PdfTable> tables) {
		StringBuilder text = new StringBuilder();
		for (int i = 0; i < tables.size(); i++) {
			PdfTable table = tables.get(i);
			text.append("<tableau document=\"").append(documentId).append("\" nom=\"")
				.append(fileName.replace("\"", "'"))
				.append("\" feuille=\"")
				.append(name(table, i + 1))
				.append("\" fiabilite=\"")
				.append(table.reliable() ? "fiable" : "ambigue")
				.append("\">\n");
			if (!table.reliable()) {
				table.problems().forEach(problem -> text.append("! ").append(problem).append('\n'));
			}
			text.append("Colonnes : ");
			List<String> columns = new ArrayList<>();
			for (int index = 0; index < table.columns().size(); index++) {
				Column column = table.columns().get(index);
				columns.add(Spreadsheet.columnName(index) + " « " + column.label() + " »");
			}
			text.append(String.join(" | ", columns)).append('\n');
			for (int r = 0; r < table.rows().size(); r++) {
				Row row = table.rows().get(r);
				text.append('L').append(r + 2).append(" p").append(row.page()).append(" y").append(Math.round(row.y()));
				for (Cell cell : row.cells()) {
					text.append(" | ").append(Spreadsheet.columnName(cell.column())).append(r + 2).append(' ')
						.append('"').append(cell.text().replace("\"", "\"\"")).append('"');
					if (cell.ambiguous()) {
						text.append(" [position ambiguë]");
					}
				}
				text.append('\n');
			}
			text.append("</tableau>\n");
		}
		return text.toString();
	}

}
