package eu.gpefrance.tenders.analysis;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import eu.gpefrance.tenders.analysis.PriceCheck.Column;
import eu.gpefrance.tenders.analysis.PriceCheck.Issue;
import eu.gpefrance.tenders.analysis.PriceCheck.Role;
import eu.gpefrance.tenders.analysis.PriceCheck.Total;
import eu.gpefrance.tenders.document.Spreadsheet;
import eu.gpefrance.tenders.document.Spreadsheet.Cell;
import eu.gpefrance.tenders.document.Spreadsheet.Row;
import eu.gpefrance.tenders.document.Spreadsheet.Sheet;

/**
 * Checks a price schedule with plain code, no AI: finds the header of the
 * columns (their names vary from one buyer to another), then for each line
 * compares quantity times unit price with the amount, and each total written
 * with the sum of its lines, and the HT plus VAT with the TTC. Nothing is
 * guessed: a value missing or unreadable is reported, never replaced.
 */
final class PriceScheduleChecker {

	/** Rows looked at to find the header. */
	private static final int HEADER_SEARCH = 40;

	private static final int MAX_ISSUES = 30;

	private static final BigDecimal CENT = new BigDecimal("0.01");

	/** One euro per 100 000 euros: amounts kept with more decimals than shown. */
	private static final BigDecimal RELATIVE_TOLERANCE = new BigDecimal("0.00001");

	/** Half a cent per unit: the unit price is often rounded to the cent. */
	private static final BigDecimal HALF_CENT = new BigDecimal("0.005");

	private static final Map<Role, Pattern> HEADERS = new EnumMap<>(Map.of(
			Role.UNIT_PRICE, Pattern.compile("\\bp ?u\\b|prix unit|unitaire"),
			Role.AMOUNT, Pattern.compile("montant|prix total|\\btotal\\b|\\bmt\\b"),
			Role.QUANTITY, Pattern.compile("quantite|\\bqte\\b|\\bqt\\b|\\bquant\\b|\\bnombre\\b"),
			Role.UNIT, Pattern.compile("^u$|^un$|^unite|\\bunite\\b"),
			Role.DESIGNATION, Pattern.compile("designation|libelle|description|intitule|nature|ouvrage|prestation"),
			Role.REFERENCE, Pattern.compile("^n$|^no$|^n ?°|numero|^art|article|^ref|reference|^code|^poste|^item|^repere")));

	/** Checked in this order: a unit price header also says "prix". */
	private static final List<Role> ORDER = List.of(Role.UNIT_PRICE, Role.AMOUNT, Role.QUANTITY, Role.UNIT,
			Role.REFERENCE, Role.DESIGNATION);

	private static final Pattern TOTAL = Pattern
		.compile("^(sous ?)?total\\b|montant total|total general|\\btva\\b|\\bt ?v ?a\\b|\\bt ?t ?c\\b|net a payer|"
				+ "total h ?t");

	private static final Pattern TEXT_PRICE = Pattern
		.compile("^(p ?m|pour memoire|inclus|compris|sans objet|offert|neant|a chiffrer|gratuit)$");

	private PriceScheduleChecker() {
	}

	static List<PriceCheck> check(Long documentId, String fileName, Spreadsheet spreadsheet) {
		List<PriceCheck> checks = new ArrayList<>();
		for (Sheet sheet : spreadsheet.sheets()) {
			if (!sheet.rows().isEmpty()) {
				checks.add(check(documentId, fileName, sheet));
			}
		}
		return checks;
	}

	static PriceCheck check(Long documentId, String fileName, Sheet sheet) {
		Issues issues = new Issues();
		Header header = header(sheet);
		if (header == null) {
			issues.add(new Issue("NO_HEADER", "En-tête des colonnes (désignation, quantité, prix, montant) non reconnu : "
					+ "aucun contrôle des montants sur cette feuille", null, null));
			notSaved(sheet.rows(), issues);
			return new PriceCheck(documentId, fileName, sheet.name(), List.of(), null, 0, 0, 0, 0, 0, null, null,
					false, List.of(), List.copyOf(issues.list), issues.more);
		}
		int items = 0;
		int withQuantity = 0;
		int withUnitPrice = 0;
		int withAmount = 0;
		int textPrices = 0;
		int nonZeroAmounts = 0;
		BigDecimal all = null;
		BigDecimal section = null;
		List<Total> totals = new ArrayList<>();
		BigDecimal lastHt = null;
		BigDecimal lastVat = null;
		for (Row row : sheet.rows()) {
			if (row.number() <= header.row() || isHeader(row)) {
				continue;
			}
			String label = label(row);
			if (label != null && TOTAL.matcher(normalize(label)).find()) {
				Cell cell = amountCell(row, header);
				if (cell != null && cell.numeric() != null) {
					Total total = total(label, row, cell, section, all, lastHt, lastVat);
					totals.add(total);
					if (Boolean.FALSE.equals(total.consistent())) {
						issues.add(new Issue(total.kind().equals("TTC") ? "VAT_MISMATCH" : "TOTAL_MISMATCH",
								"« " + label + " » : " + plain(total.declared()) + " écrit, "
										+ plain(total.computed()) + " calculé",
								total.ref(), row.number()));
					}
					switch (total.kind()) {
						case "HT" -> {
							lastHt = total.declared();
							section = null;
						}
						case "VAT" -> lastVat = total.declared();
						default -> {
						}
					}
				}
				continue;
			}
			Cell quantity = cell(row, header, Role.QUANTITY);
			Cell unitPrice = cell(row, header, Role.UNIT_PRICE);
			Cell amount = cell(row, header, Role.AMOUNT);
			boolean described = cell(row, header, Role.DESIGNATION) != null || cell(row, header, Role.REFERENCE) != null;
			if (!described || quantity == null && unitPrice == null && amount == null
					&& cell(row, header, Role.UNIT) == null) {
				continue;
			}
			items++;
			BigDecimal q = value(quantity);
			BigDecimal pu = value(unitPrice);
			BigDecimal amt = value(amount);
			if (q != null) {
				withQuantity++;
			}
			if (pu != null) {
				withUnitPrice++;
			}
			if (amt != null) {
				withAmount++;
				if (amt.signum() != 0) {
					nonZeroAmounts++;
				}
				all = all == null ? amt : all.add(amt);
				section = section == null ? amt : section.add(amt);
			}
			if (isTextPrice(unitPrice) || isTextPrice(amount)) {
				textPrices++;
			}
			for (Cell cell : new Cell[] { quantity, unitPrice, amount }) {
				if (cell != null && cell.valueMissing()) {
					issues.add(new Issue("NOT_SAVED", "Formule sans valeur enregistrée dans le fichier : valeur inconnue",
							cell.ref(), row.number()));
				}
				else if (cell != null && cell.type() == Spreadsheet.CellType.ERROR) {
					issues.add(new Issue("ERROR_CELL", "Cellule en erreur " + cell.text(), cell.ref(), row.number()));
				}
			}
			if (q != null && pu != null && amt != null) {
				BigDecimal expected = q.multiply(pu).setScale(2, RoundingMode.HALF_UP);
				if (!closeLine(expected, amt, q)) {
					issues.add(new Issue("LINE_MISMATCH", "Quantité × prix unitaire = " + plain(expected)
							+ ", montant écrit " + plain(amt), amount.ref(), row.number()));
				}
			}
		}
		// No price, and amounts empty or worth zero (formulas quantity × empty price).
		boolean blank = items > 0 && withUnitPrice == 0 && nonZeroAmounts == 0;
		return new PriceCheck(documentId, fileName, sheet.name(), header.columns(), header.row(), items, withQuantity,
				withUnitPrice, withAmount, textPrices, all, header.basis(), blank, List.copyOf(totals),
				List.copyOf(issues.list), issues.more);
	}

	private static Total total(String label, Row row, Cell cell, BigDecimal section, BigDecimal all,
			BigDecimal lastHt, BigDecimal lastVat) {
		String normalized = normalize(label);
		BigDecimal declared = cell.numeric();
		if (normalized.matches(".*\\bt ?t ?c\\b.*")) {
			BigDecimal computed = lastHt != null && lastVat != null ? lastHt.add(lastVat) : null;
			return new Total("TTC", label, cell.ref(), row.number(), declared, computed,
					computed == null ? null : close(computed, declared));
		}
		if (normalized.matches(".*\\b(tva|t ?v ?a)\\b.*")) {
			return new Total("VAT", label, cell.ref(), row.number(), declared, null, null);
		}
		// A subtotal adds up its section; a total adds up its sections, or every line.
		Boolean consistent = null;
		BigDecimal computed = section != null ? section : all;
		if (computed != null) {
			consistent = close(computed, declared) || all != null && close(all, declared);
			if (!close(computed, declared) && all != null && close(all, declared)) {
				computed = all;
			}
		}
		return new Total("HT", label, cell.ref(), row.number(), declared, computed, consistent);
	}

	/** Two amounts agree to the cent, or to one euro per 100 000 euros. */
	static boolean close(BigDecimal a, BigDecimal b) {
		BigDecimal difference = a.subtract(b).abs();
		return difference.compareTo(CENT.max(a.abs().max(b.abs()).multiply(RELATIVE_TOLERANCE))) <= 0;
	}

	/**
	 * Quantity times unit price agrees with the amount written, allowing half a
	 * cent per unit for a unit price rounded to the cent.
	 */
	static boolean closeLine(BigDecimal expected, BigDecimal amount, BigDecimal quantity) {
		return expected.subtract(amount).abs().compareTo(CENT.add(quantity.abs().multiply(HALF_CENT))) <= 0;
	}

	private static void notSaved(List<Row> rows, Issues issues) {
		for (Row row : rows) {
			for (Cell cell : row.cells()) {
				if (cell.valueMissing()) {
					issues.add(new Issue("NOT_SAVED", "Formule sans valeur enregistrée dans le fichier : valeur inconnue",
							cell.ref(), row.number()));
				}
			}
		}
	}

	private static Header header(Sheet sheet) {
		Header best = null;
		for (Row row : sheet.rows().subList(0, Math.min(HEADER_SEARCH, sheet.rows().size()))) {
			Map<Role, Column> roles = new EnumMap<>(Role.class);
			String basis = null;
			for (Cell cell : row.cells()) {
				if (cell.type() != Spreadsheet.CellType.TEXT || cell.text().length() > 60) {
					continue;
				}
				String text = normalize(cell.text());
				for (Role role : ORDER) {
					if (!roles.containsKey(role) && HEADERS.get(role).matcher(text).find()) {
						roles.put(role, new Column(role, cell.ref(), cell.text()));
						if (role == Role.AMOUNT) {
							basis = text.matches(".*\\bt ?t ?c\\b.*") ? "TTC" : text.matches(".*\\bh ?t\\b.*") ? "HT" : null;
						}
						break;
					}
				}
			}
			boolean priced = roles.containsKey(Role.QUANTITY) || roles.containsKey(Role.UNIT_PRICE)
					|| roles.containsKey(Role.AMOUNT);
			if (priced && roles.size() >= 2 && (best == null || roles.size() > best.columns().size())) {
				best = new Header(row.number(), List.copyOf(roles.values()), basis);
			}
		}
		return best;
	}

	/** A header repeated further down (one per section or per page). */
	private static boolean isHeader(Row row) {
		int found = 0;
		for (Cell cell : row.cells()) {
			if (cell.type() == Spreadsheet.CellType.TEXT && cell.text().length() <= 60) {
				String text = normalize(cell.text());
				if (HEADERS.get(Role.QUANTITY).matcher(text).find() || HEADERS.get(Role.UNIT_PRICE).matcher(text).find()
						|| text.startsWith("designation")) {
					found++;
				}
			}
		}
		return found >= 2;
	}

	private static String label(Row row) {
		for (Cell cell : row.cells()) {
			if (cell.type() == Spreadsheet.CellType.TEXT && !cell.text().isBlank() && cell.numeric() == null) {
				return cell.text();
			}
		}
		return null;
	}

	/** The amount of a total line: in the amount column, else its last number. */
	private static Cell amountCell(Row row, Header header) {
		Cell amount = cell(row, header, Role.AMOUNT);
		if (amount != null && amount.numeric() != null) {
			return amount;
		}
		for (int i = row.cells().size() - 1; i >= 0; i--) {
			if (row.cells().get(i).numeric() != null) {
				return row.cells().get(i);
			}
		}
		return null;
	}

	private static Cell cell(Row row, Header header, Role role) {
		return header.columns()
			.stream()
			.filter(column -> column.role() == role)
			.findFirst()
			.map(column -> row.cell(columnIndex(column.ref())))
			.filter(cell -> cell.type() != Spreadsheet.CellType.TEXT || !cell.text().isBlank())
			.orElse(null);
	}

	private static BigDecimal value(Cell cell) {
		return cell == null || cell.valueMissing() ? null : cell.numeric();
	}

	private static boolean isTextPrice(Cell cell) {
		return cell != null && cell.type() == Spreadsheet.CellType.TEXT && cell.numeric() == null
				&& TEXT_PRICE.matcher(normalize(cell.text())).matches();
	}

	static int columnIndex(String ref) {
		int index = 0;
		for (char c : ref.toCharArray()) {
			if (!Character.isLetter(c)) {
				break;
			}
			index = index * 26 + (c - 'A' + 1);
		}
		return index - 1;
	}

	static String normalize(String text) {
		return Normalizer.normalize(text, Normalizer.Form.NFD)
			.replaceAll("\\p{M}", "")
			.toLowerCase(Locale.ROOT)
			.replace("°", " ° ")
			.replaceAll("[^a-z0-9°]+", " ")
			.strip();
	}

	private static String plain(BigDecimal value) {
		return value == null ? "?" : value.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
	}

	private record Header(int row, List<Column> columns, String basis) {
	}

	private static final class Issues {

		private final List<Issue> list = new ArrayList<>();

		private int more;

		void add(Issue issue) {
			if (list.size() < MAX_ISSUES) {
				list.add(issue);
			}
			else {
				more++;
			}
		}

	}

}
