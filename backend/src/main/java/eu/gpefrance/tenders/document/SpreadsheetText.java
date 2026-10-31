package eu.gpefrance.tenders.document;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eu.gpefrance.tenders.document.Spreadsheet.Cell;
import eu.gpefrance.tenders.document.Spreadsheet.Row;
import eu.gpefrance.tenders.document.Spreadsheet.Sheet;

/**
 * Writes a spreadsheet as pages of text that keep its structure: one page
 * per part of a sheet, its name first, then one line per row with its number
 * and each cell with its coordinates, its type, its formula and whether the
 * saved value is missing. A page is the unit cited as a source, the row
 * number tells where in it.
 *
 * <pre>
 * Feuille « DPGF lot 1 », lignes 1 à 3
 * L1 | A1 "N°" | B1 "Désignation" | C1 "U" | D1 "Qté" | E1 "PU HT" | F1 "Montant HT"
 * L2 | A2 "1.1" | B2 "Terrassement" | C2 "m3" | D2 120 | E2 15.5 | F2 1860 [formule D2*E2]
 * L3 | F3 valeur non enregistrée [formule SUM(F2:F2)]
 * </pre>
 */
public final class SpreadsheetText {

	/** Rows of a page, and its length, at most. */
	static final int ROWS_PER_PAGE = 150;

	static final int PAGE_LENGTH = 12_000;

	private static final Pattern SHEET = Pattern.compile("^Feuille « (.*) », lignes ");

	private SpreadsheetText() {
	}

	public static List<String> pages(Spreadsheet spreadsheet) {
		List<String> pages = new ArrayList<>();
		for (Sheet sheet : spreadsheet.sheets()) {
			List<String> lines = new ArrayList<>();
			int length = 0;
			int first = 0;
			for (Row row : sheet.rows()) {
				String line = line(row);
				if (!lines.isEmpty() && (lines.size() >= ROWS_PER_PAGE || length + line.length() > PAGE_LENGTH)) {
					pages.add(page(sheet, first, lines, false));
					lines = new ArrayList<>();
					length = 0;
				}
				if (lines.isEmpty()) {
					first = row.number();
				}
				lines.add(line);
				length += line.length() + 1;
			}
			if (!lines.isEmpty()) {
				pages.add(page(sheet, first, lines, sheet.truncated()));
			}
		}
		return pages;
	}

	private static String page(Sheet sheet, int first, List<String> lines, boolean last) {
		String lastRow = lines.getLast().substring(1, lines.getLast().indexOf(' '));
		StringBuilder text = new StringBuilder("Feuille « ").append(sheet.name())
			.append(" », lignes ")
			.append(first)
			.append(" à ")
			.append(lastRow)
			.append('\n');
		lines.forEach(line -> text.append(line).append('\n'));
		if (last) {
			text.append("… feuille coupée : les lignes suivantes n'ont pas été lues (limite de lecture)\n");
		}
		return text.toString().strip();
	}

	static String line(Row row) {
		StringBuilder line = new StringBuilder("L").append(row.number());
		for (Cell cell : row.cells()) {
			line.append(" | ").append(cell.ref()).append(' ').append(value(cell));
			if (cell.formula() != null) {
				line.append(" [formule ").append(cell.formula().replace(']', ')')).append(']');
			}
		}
		return line.toString();
	}

	private static String value(Cell cell) {
		return switch (cell.type()) {
			case NUMBER -> cell.text() != null && !cell.text().equals(cell.number().toPlainString())
					? cell.number().toPlainString() + " (écrit " + quoted(cell.text()) + ")"
					: cell.number().toPlainString();
			case TEXT -> quoted(cell.text());
			case DATE -> "date " + cell.text();
			case BOOLEAN -> cell.text();
			case ERROR -> "erreur " + cell.text();
			case NO_VALUE -> "valeur non enregistrée";
		};
	}

	private static String quoted(String text) {
		return '"' + text.replace("\"", "\"\"").replace("\r", "").replace("\n", " / ") + '"';
	}

	/** The name of the sheet of a page written here, if it is one. */
	public static Optional<String> sheetName(String page) {
		Matcher matcher = SHEET.matcher(page);
		return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
	}

	/** The line of a row in a page written here. */
	public static Optional<String> rowLine(String page, int row) {
		String prefix = "L" + row + " |";
		return page.lines().filter(line -> line.startsWith(prefix)).findFirst();
	}

}
