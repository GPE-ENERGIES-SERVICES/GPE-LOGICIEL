package eu.gpefrance.tenders.supplier;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import eu.gpefrance.tenders.document.Spreadsheet;
import eu.gpefrance.tenders.document.Spreadsheet.Cell;
import eu.gpefrance.tenders.document.Spreadsheet.Row;
import eu.gpefrance.tenders.document.Spreadsheet.Sheet;

/**
 * Reads the prices of a supplier from its file (CSV, XLSX...), by plain code:
 * the columns are found from their headers, which vary from one supplier to
 * another. A line without reference, designation or price is left out and
 * counted; a net price is never guessed, only computed from a list price and
 * a discount written in the file.
 */
final class OfferFileReader {

	private static final int HEADER_SEARCH = 20;

	enum Role {

		MANUFACTURER_CODE, MANUFACTURER_REF, EAN, SUPPLIER_REF, DESIGNATION, PACK, UNIT, DISCOUNT, NET_PRICE, LIST_PRICE

	}

	/** Checked in this order: a manufacturer reference also says "référence". */
	private static final Map<Role, Pattern> HEADERS = new LinkedHashMap<>();

	static {
		HEADERS.put(Role.MANUFACTURER_CODE, Pattern.compile("trigramme|code mot|mot (clef? )?fab|supplier ?code"));
		HEADERS.put(Role.MANUFACTURER_REF, Pattern.compile("ref\\w* (fabricant|constructeur|fab|fournisseur fabricant)|^ref fab"));
		HEADERS.put(Role.EAN, Pattern.compile("\\bean\\b|gencod|code barre"));
		HEADERS.put(Role.SUPPLIER_REF, Pattern.compile("^ref\\w*|code article|^article|^code\\b|ref\\w* (article|distributeur|fournisseur)"));
		HEADERS.put(Role.DESIGNATION, Pattern.compile("designation|libelle|description"));
		HEADERS.put(Role.PACK, Pattern.compile("conditionnement|colisage|^cond\\b|quantite (par|du) (prix|conditionnement)|prix par"));
		HEADERS.put(Role.UNIT, Pattern.compile("^unite|^u$|^uv$|unite de (vente|prix)"));
		HEADERS.put(Role.DISCOUNT, Pattern.compile("remise"));
		HEADERS.put(Role.NET_PRICE, Pattern.compile("prix net|^net\\b|net ht"));
		HEADERS.put(Role.LIST_PRICE, Pattern.compile("prix (public|tarif|catalogue|brut|de base)|^tarif|^prix\\b|\\bppi\\b"));
	}

	private OfferFileReader() {
	}

	/**
	 * @param discountRate share deducted, 0.35 for 35 %
	 */
	record Line(String supplierRef, String manufacturerCode, String manufacturerRef, String ean, String designation, String unit,
			BigDecimal packQuantity, BigDecimal listPrice, BigDecimal discountRate, BigDecimal netPrice) {
	}

	/**
	 * @param columns the header found for each role, by sheet read
	 * @param rejected lines left out: no reference, no designation or no price
	 */
	record Result(List<Line> lines, Map<Role, String> columns, int rejected, List<String> warnings) {
	}

	static Result read(Spreadsheet spreadsheet) {
		List<Line> lines = new ArrayList<>();
		Map<Role, String> found = new EnumMap<>(Role.class);
		int rejected = 0;
		List<String> warnings = new ArrayList<>(spreadsheet.warnings());
		for (Sheet sheet : spreadsheet.sheets()) {
			Header header = header(sheet);
			if (header == null) {
				continue;
			}
			header.columns().forEach((role, column) -> found.putIfAbsent(role, column.text()));
			if (sheet.truncated()) {
				warnings.add("Feuille « " + sheet.name() + " » lue en partie seulement (limites de lecture) : "
						+ "découpez le fichier.");
			}
			for (Row row : sheet.rows()) {
				if (row.number() <= header.row()) {
					continue;
				}
				Line line = line(row, header);
				if (line == null) {
					rejected++;
				}
				else {
					lines.add(line);
				}
			}
		}
		return new Result(List.copyOf(lines), Map.copyOf(found), rejected, List.copyOf(warnings));
	}

	private static Line line(Row row, Header header) {
		String ref = text(row, header, Role.SUPPLIER_REF);
		String manufacturerRef = text(row, header, Role.MANUFACTURER_REF);
		if (ref == null) {
			ref = manufacturerRef;
		}
		String designation = text(row, header, Role.DESIGNATION);
		if (ref == null || designation == null) {
			return null;
		}
		BigDecimal list = number(row, header, Role.LIST_PRICE);
		BigDecimal discount = number(row, header, Role.DISCOUNT);
		if (discount != null && discount.compareTo(BigDecimal.ONE) > 0) {
			// Written as a percentage: 35 for 35 %.
			discount = discount.movePointLeft(2);
		}
		if (discount != null && (discount.signum() < 0 || discount.compareTo(BigDecimal.ONE) >= 0)) {
			return null;
		}
		BigDecimal net = number(row, header, Role.NET_PRICE);
		if (net == null && list != null) {
			net = list.multiply(BigDecimal.ONE.subtract(discount == null ? BigDecimal.ZERO : discount))
				.setScale(4, RoundingMode.HALF_UP);
		}
		BigDecimal pack = number(row, header, Role.PACK);
		if (net == null || net.signum() < 0 || pack != null && pack.signum() <= 0) {
			return null;
		}
		String manufacturerCode = text(row, header, Role.MANUFACTURER_CODE);
		return new Line(cut(ref, 100),
				manufacturerCode == null ? null : cut(manufacturerCode.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", ""), 10),
				cut(manufacturerRef, 100), cut(text(row, header, Role.EAN), 20), designation,
				cut(text(row, header, Role.UNIT), 20), pack == null ? BigDecimal.ONE : pack, list, discount, net);
	}

	private static Header header(Sheet sheet) {
		for (Row row : sheet.rows().subList(0, Math.min(HEADER_SEARCH, sheet.rows().size()))) {
			Map<Role, Column> columns = new EnumMap<>(Role.class);
			for (Cell cell : row.cells()) {
				if (cell.text() == null || cell.text().length() > 60 || cell.number() != null) {
					continue;
				}
				String text = normalize(cell.text());
				for (Map.Entry<Role, Pattern> entry : HEADERS.entrySet()) {
					if (!columns.containsKey(entry.getKey()) && entry.getValue().matcher(text).find()) {
						columns.put(entry.getKey(), new Column(cell.column(), cell.text()));
						break;
					}
				}
			}
			boolean referenced = columns.containsKey(Role.SUPPLIER_REF) || columns.containsKey(Role.MANUFACTURER_REF);
			boolean priced = columns.containsKey(Role.NET_PRICE) || columns.containsKey(Role.LIST_PRICE);
			if (referenced && priced && columns.containsKey(Role.DESIGNATION)) {
				return new Header(row.number(), columns);
			}
		}
		return null;
	}

	private static String text(Row row, Header header, Role role) {
		Column column = header.columns().get(role);
		Cell cell = column == null ? null : row.cell(column.index());
		if (cell == null) {
			return null;
		}
		String text = cell.text() != null ? cell.text().strip()
				: cell.number() != null ? cell.number().stripTrailingZeros().toPlainString() : null;
		return text == null || text.isEmpty() ? null : text;
	}

	private static BigDecimal number(Row row, Header header, Role role) {
		Column column = header.columns().get(role);
		Cell cell = column == null ? null : row.cell(column.index());
		return cell == null || cell.valueMissing() ? null : cell.numeric();
	}

	private static String cut(String text, int length) {
		return text == null || text.length() <= length ? text : text.substring(0, length);
	}

	static String normalize(String text) {
		return Normalizer.normalize(text, Normalizer.Form.NFD)
			.replaceAll("\\p{M}", "")
			.toLowerCase(Locale.ROOT)
			.replaceAll("[^a-z0-9]+", " ")
			.strip();
	}

	private record Column(int index, String text) {
	}

	private record Header(int row, Map<Role, Column> columns) {
	}

}
