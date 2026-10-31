package eu.gpefrance.tenders.document;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.springframework.stereotype.Component;

import eu.gpefrance.tenders.document.Spreadsheet.Cell;
import eu.gpefrance.tenders.document.Spreadsheet.Row;
import eu.gpefrance.tenders.document.Spreadsheet.Sheet;

/**
 * Reads the spreadsheets of a consultation file (price schedules: DPGF, BPU,
 * DQE) cell by cell: XLS and XLSX with Apache POI, ODS and CSV with plain
 * code. Nothing is computed or run: a formula keeps the result saved in the
 * file, macros are never run (POI does not run them, they are not even
 * read), and the XML of an ODS file is read without DTD nor external entity.
 * Sizes are bounded at every step: file, uncompressed content, sheets, rows,
 * columns and cells; beyond, the sheet is cut and says so.
 */
@Component
public class SpreadsheetExtractor {

	/** Extensions read as spreadsheets. */
	public static final Set<String> FORMATS = Set.of("xls", "xlsx", "xlsm", "ods", "csv");

	private static final Charset WINDOWS = Charset.forName("windows-1252");

	private static final int MAX_TEXT_LENGTH = 1000;

	static {
		// Archives of XLSX files: no entry larger than this once uncompressed.
		ZipSecureFile.setMaxEntrySize(Limits.DEFAULT.maxUncompressedBytes());
	}

	private final Limits limits;

	public SpreadsheetExtractor() {
		this(Limits.DEFAULT);
	}

	SpreadsheetExtractor(Limits limits) {
		this.limits = limits;
	}

	public static boolean handles(String fileName) {
		return FORMATS.contains(DocumentProcessor.extension(fileName));
	}

	/**
	 * @param extension the extension of the file, which decides how it is read;
	 * a content that does not match it is refused as unreadable
	 * @throws TooLargeException beyond the size allowed
	 * @throws UnreadableSpreadsheetException when the file is empty, damaged,
	 * protected or not of its type
	 */
	public Spreadsheet read(Path file, String extension) throws IOException {
		long size = Files.size(file);
		if (size == 0) {
			throw new UnreadableSpreadsheetException("Fichier vide");
		}
		if (size > limits.maxBytes()) {
			throw new TooLargeException();
		}
		return switch (extension.toLowerCase(Locale.ROOT)) {
			case "xls", "xlsx", "xlsm" -> readWorkbook(file);
			case "ods" -> readOds(file);
			case "csv" -> readCsv(file);
			default -> throw new UnreadableSpreadsheetException("Format de tableur non pris en charge");
		};
	}

	// XLS and XLSX

	private Spreadsheet readWorkbook(Path file) throws IOException {
		Collector collector = new Collector(limits);
		try (Workbook workbook = WorkbookFactory.create(file.toFile(), null, true)) {
			for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
				org.apache.poi.ss.usermodel.Sheet sheet = workbook.getSheetAt(index);
				if (!collector.startSheet(sheet.getSheetName() + (workbook.isSheetHidden(index) ? " (masquée)" : ""))) {
					break;
				}
				for (org.apache.poi.ss.usermodel.Row row : sheet) {
					if (!collector.startRow(row.getRowNum() + 1)) {
						break;
					}
					for (org.apache.poi.ss.usermodel.Cell cell : row) {
						if (!collector.add(cell.getColumnIndex(), cell(cell))) {
							break;
						}
					}
				}
			}
		}
		catch (EncryptedDocumentException ex) {
			throw new UnreadableSpreadsheetException("Tableur protégé par un mot de passe");
		}
		catch (IOException | RuntimeException ex) {
			// Damaged file, or a content that is not a spreadsheet (EmptyFileException,
			// NotOfficeXmlFileException, RecordFormatException...).
			throw new UnreadableSpreadsheetException("Tableur illisible ou dont le contenu ne correspond pas à l'extension");
		}
		return collector.result();
	}

	private static Value cell(org.apache.poi.ss.usermodel.Cell cell) {
		String formula = null;
		CellType type = cell.getCellType();
		if (type == CellType.FORMULA) {
			formula = cell.getCellFormula();
			if (cell instanceof XSSFCell xssf && !xssf.getCTCell().isSetV()) {
				return new Value(Spreadsheet.CellType.NO_VALUE, null, null, formula, true);
			}
			type = cell.getCachedFormulaResultType();
		}
		return switch (type) {
			case NUMERIC -> DateUtil.isCellDateFormatted(cell) ? date(cell.getLocalDateTimeCellValue(), formula)
					: new Value(Spreadsheet.CellType.NUMBER, null,
							plain(new BigDecimal(Double.toString(cell.getNumericCellValue()))), formula,
							false);
			case STRING -> text(cell.getStringCellValue(), formula);
			case BOOLEAN -> new Value(Spreadsheet.CellType.BOOLEAN, cell.getBooleanCellValue() ? "VRAI" : "FAUX", null,
					formula, false);
			case ERROR -> new Value(Spreadsheet.CellType.ERROR, errorText(cell.getErrorCellValue()), null, formula,
					false);
			default -> formula == null ? null : new Value(Spreadsheet.CellType.NO_VALUE, null, null, formula, true);
		};
	}

	private static String errorText(byte code) {
		try {
			return FormulaError.forInt(code).getString();
		}
		catch (IllegalArgumentException ex) {
			return "#ERREUR";
		}
	}

	private static Value date(LocalDateTime value, String formula) {
		String text = value.toLocalTime().equals(java.time.LocalTime.MIDNIGHT) ? value.toLocalDate().toString()
				: value.toString();
		return new Value(Spreadsheet.CellType.DATE, text, null, formula, false);
	}

	private static Value text(String value, String formula) {
		if (value == null || value.isBlank()) {
			return formula == null ? null : new Value(Spreadsheet.CellType.TEXT, "", null, formula, false);
		}
		return new Value(Spreadsheet.CellType.TEXT, shorten(value.strip()), null, formula, false);
	}

	// ODS

	private Spreadsheet readOds(Path file) throws IOException {
		try (ZipFile zip = new ZipFile(file.toFile())) {
			ZipEntry content = zip.getEntry("content.xml");
			if (content == null) {
				throw new UnreadableSpreadsheetException("Tableur ODS illisible (contenu absent)");
			}
			try (InputStream in = new BoundedInputStream(zip.getInputStream(content), limits.maxUncompressedBytes())) {
				return readOdsContent(in);
			}
		}
		catch (ZipException ex) {
			throw new UnreadableSpreadsheetException("Tableur illisible ou dont le contenu ne correspond pas à l'extension");
		}
		catch (XMLStreamException | RuntimeException ex) {
			for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
				if (cause instanceof TooLargeException tooLarge) {
					throw tooLarge;
				}
			}
			throw new UnreadableSpreadsheetException("Tableur ODS illisible");
		}
	}

	private static final String TABLE = "urn:oasis:names:tc:opendocument:xmlns:table:1.0";

	private static final String OFFICE = "urn:oasis:names:tc:opendocument:xmlns:office:1.0";

	private static final String TEXT = "urn:oasis:names:tc:opendocument:xmlns:text:1.0";

	Spreadsheet readOdsContent(InputStream in) throws XMLStreamException {
		XMLInputFactory factory = XMLInputFactory.newFactory();
		factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
		factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
		XMLStreamReader xml = factory.createXMLStreamReader(in);
		Collector collector = new Collector(limits);
		boolean sheetOpen = false;
		boolean stopped = false;
		int rowNumber = 0;
		int column = 0;
		int rowRepeat = 1;
		List<PendingCell> rowCells = new ArrayList<>();
		PendingCell current = null;
		StringBuilder paragraph = null;
		try {
			while (xml.hasNext() && !stopped) {
				int event = xml.next();
				if (event == XMLStreamConstants.START_ELEMENT) {
					String ns = xml.getNamespaceURI();
					String name = xml.getLocalName();
					if (TABLE.equals(ns) && name.equals("table")) {
						sheetOpen = collector.startSheet(xml.getAttributeValue(TABLE, "name"));
						stopped = !sheetOpen;
						rowNumber = 0;
					}
					else if (TABLE.equals(ns) && name.equals("table-row")) {
						rowRepeat = repeat(xml.getAttributeValue(TABLE, "number-rows-repeated"));
						column = 0;
						rowCells.clear();
					}
					else if (TABLE.equals(ns) && (name.equals("table-cell") || name.equals("covered-table-cell"))) {
						current = new PendingCell(column, repeat(xml.getAttributeValue(TABLE, "number-columns-repeated")),
								xml.getAttributeValue(OFFICE, "value-type"), xml.getAttributeValue(OFFICE, "value"),
								xml.getAttributeValue(OFFICE, "date-value"), xml.getAttributeValue(OFFICE, "boolean-value"),
								xml.getAttributeValue(TABLE, "formula"), new StringBuilder());
					}
					else if (TEXT.equals(ns) && name.equals("p") && current != null) {
						if (!current.text().isEmpty()) {
							current.text().append('\n');
						}
						paragraph = current.text();
					}
					else if (TEXT.equals(ns) && name.equals("s") && paragraph != null) {
						paragraph.append(" ".repeat(Math.min(repeat(xml.getAttributeValue(TEXT, "c")), 50)));
					}
					else if (TEXT.equals(ns) && name.equals("tab") && paragraph != null) {
						paragraph.append('\t');
					}
				}
				else if (event == XMLStreamConstants.CHARACTERS && paragraph != null
						&& paragraph.length() < MAX_TEXT_LENGTH) {
					paragraph.append(xml.getText());
				}
				else if (event == XMLStreamConstants.END_ELEMENT) {
					String ns = xml.getNamespaceURI();
					String name = xml.getLocalName();
					if (TEXT.equals(ns) && name.equals("p")) {
						paragraph = null;
					}
					else if (TABLE.equals(ns) && (name.equals("table-cell") || name.equals("covered-table-cell"))
							&& current != null) {
						if (odsValue(current) != null) {
							rowCells.add(current);
						}
						column += current.repeat();
						current = null;
					}
					else if (TABLE.equals(ns) && name.equals("table-row") && sheetOpen) {
						if (rowCells.isEmpty()) {
							// Empty rows, often repeated by the thousand at the end of a sheet.
							rowNumber += rowRepeat;
						}
						else {
							for (int copy = 0; copy < rowRepeat && !stopped; copy++) {
								rowNumber++;
								if (!collector.startRow(rowNumber)) {
									break;
								}
								addOdsCells(collector, rowCells);
							}
						}
					}
				}
			}
		}
		finally {
			xml.close();
		}
		return collector.result();
	}

	private static void addOdsCells(Collector collector, List<PendingCell> cells) {
		for (PendingCell cell : cells) {
			Value value = odsValue(cell);
			for (int copy = 0; copy < cell.repeat(); copy++) {
				if (!collector.add(cell.column() + copy, value)) {
					return;
				}
			}
		}
	}

	private static Value odsValue(PendingCell cell) {
		String formula = cell.formula() == null ? null
				: cell.formula().replaceFirst("^(of:|oooc:|msoxl:)", "").replaceFirst("^=", "");
		String text = cell.text().toString().strip();
		String type = cell.valueType();
		if (type == null) {
			if (text.isEmpty()) {
				return formula == null ? null : new Value(Spreadsheet.CellType.NO_VALUE, null, null, formula, true);
			}
			boolean error = formula != null && (text.startsWith("#") || text.startsWith("Err:"));
			return new Value(error ? Spreadsheet.CellType.ERROR : Spreadsheet.CellType.TEXT, shorten(text), null,
					formula, false);
		}
		return switch (type) {
			case "float", "currency", "percentage" -> {
				BigDecimal number = parseDecimal(cell.value());
				yield number == null ? new Value(Spreadsheet.CellType.NO_VALUE, null, null, formula, true)
						: new Value(Spreadsheet.CellType.NUMBER, null, number, formula, false);
			}
			case "date" -> cell.dateValue() == null ? null
					: new Value(Spreadsheet.CellType.DATE, cell.dateValue().replace("T00:00:00", ""), null, formula,
							false);
			case "boolean" -> new Value(Spreadsheet.CellType.BOOLEAN, "true".equals(cell.booleanValue()) ? "VRAI" : "FAUX",
					null, formula, false);
			default -> text.isEmpty() ? null : new Value(Spreadsheet.CellType.TEXT, shorten(text), null, formula, false);
		};
	}

	private static BigDecimal parseDecimal(String value) {
		if (value == null) {
			return null;
		}
		try {
			return plain(new BigDecimal(value));
		}
		catch (NumberFormatException ex) {
			return null;
		}
	}

	private static int repeat(String value) {
		if (value == null) {
			return 1;
		}
		try {
			return Math.max(1, Integer.parseInt(value));
		}
		catch (NumberFormatException ex) {
			return 1;
		}
	}

	// CSV

	private Spreadsheet readCsv(Path file) throws IOException {
		byte[] bytes = Files.readAllBytes(file);
		for (int i = 0; i < Math.min(bytes.length, 8192); i++) {
			if (bytes[i] == 0) {
				throw new UnreadableSpreadsheetException("Fichier binaire au lieu d'un fichier CSV");
			}
		}
		String content = decode(bytes).replace("﻿", "");
		char delimiter = delimiter(content);
		Collector collector = new Collector(limits);
		collector.startSheet("CSV");
		List<String> fields = new ArrayList<>();
		StringBuilder field = new StringBuilder();
		boolean quoted = false;
		int rowNumber = 1;
		for (int i = 0; i <= content.length(); i++) {
			char c = i < content.length() ? content.charAt(i) : '\n';
			if (quoted) {
				if (c == '"' && i + 1 < content.length() && content.charAt(i + 1) == '"') {
					field.append('"');
					i++;
				}
				else if (c == '"') {
					quoted = false;
				}
				else if (field.length() < MAX_TEXT_LENGTH) {
					field.append(c);
				}
			}
			else if (c == '"' && field.isEmpty()) {
				quoted = true;
			}
			else if (c == delimiter) {
				fields.add(field.toString());
				field.setLength(0);
			}
			else if (c == '\n' || c == '\r') {
				if (c == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') {
					i++;
				}
				fields.add(field.toString());
				field.setLength(0);
				if (!addCsvRow(collector, rowNumber, fields)) {
					break;
				}
				fields.clear();
				rowNumber++;
			}
			else if (field.length() < MAX_TEXT_LENGTH) {
				field.append(c);
			}
		}
		return collector.result();
	}

	private static boolean addCsvRow(Collector collector, int rowNumber, List<String> fields) {
		if (fields.stream().allMatch(String::isBlank)) {
			return true;
		}
		if (!collector.startRow(rowNumber)) {
			return false;
		}
		for (int column = 0; column < fields.size(); column++) {
			String value = fields.get(column).strip();
			if (value.isEmpty()) {
				continue;
			}
			// A value starting with = + - @ stays a text: nothing in a CSV file is a formula.
			BigDecimal number = FrenchNumbers.parse(value);
			Value cell = new Value(number == null ? Spreadsheet.CellType.TEXT : Spreadsheet.CellType.NUMBER,
					shorten(value), number, null, false);
			if (!collector.add(column, cell)) {
				break;
			}
		}
		return true;
	}

	private static String decode(byte[] bytes) {
		try {
			return StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT)
				.decode(ByteBuffer.wrap(bytes))
				.toString();
		}
		catch (CharacterCodingException ex) {
			// Files written by Excel on Windows are often not UTF-8.
			return new String(bytes, WINDOWS);
		}
	}

	/**
	 * The separator used on most of the first lines, outside quotes: the
	 * semicolon of French Excel first, then the tab, then the comma.
	 */
	static char delimiter(String content) {
		char best = ';';
		int bestLines = -1;
		String[] lines = content.split("\\R", 21);
		for (char candidate : new char[] { ';', '\t', ',' }) {
			int linesWith = 0;
			for (int i = 0; i < Math.min(lines.length, 20); i++) {
				boolean quoted = false;
				for (char c : lines[i].toCharArray()) {
					if (c == '"') {
						quoted = !quoted;
					}
					else if (c == candidate && !quoted) {
						linesWith++;
						break;
					}
				}
			}
			if (linesWith > bestLines) {
				best = candidate;
				bestLines = linesWith;
			}
		}
		return best;
	}

	/** Without useless zeros, and never written with an exponent (1E+3). */
	private static BigDecimal plain(BigDecimal number) {
		BigDecimal stripped = number.stripTrailingZeros();
		return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
	}

	private static String shorten(String text) {
		return text.length() <= MAX_TEXT_LENGTH ? text : text.substring(0, MAX_TEXT_LENGTH) + "…";
	}

	/**
	 * Collects sheets, rows and cells up to the limits; beyond, the current
	 * sheet is marked as cut and the reading stops where it must.
	 */
	private static final class Collector {

		private final Limits limits;

		private final List<Sheet> sheets = new ArrayList<>();

		private final List<String> warnings = new ArrayList<>();

		private String sheetName;

		private List<Row> rows;

		private List<Cell> cells;

		private int rowNumber;

		private boolean truncated;

		private int totalCells;

		Collector(Limits limits) {
			this.limits = limits;
		}

		boolean startSheet(String name) {
			closeSheet();
			if (sheets.size() >= limits.maxSheets()) {
				warnings.add("Seules les " + limits.maxSheets() + " premières feuilles sont lues");
				return false;
			}
			sheetName = name == null || name.isBlank() ? "Feuille " + (sheets.size() + 1) : name;
			rows = new ArrayList<>();
			truncated = false;
			return true;
		}

		boolean startRow(int number) {
			closeRow();
			if (rows.size() >= limits.maxRows() || totalCells >= limits.maxCells()) {
				truncated = true;
				return false;
			}
			rowNumber = number;
			cells = new ArrayList<>();
			return true;
		}

		boolean add(int column, Value value) {
			if (value == null) {
				return true;
			}
			if (column >= limits.maxColumns() || totalCells >= limits.maxCells()) {
				truncated = true;
				return false;
			}
			cells.add(new Cell(Spreadsheet.columnName(column) + rowNumber, column, value.type(), value.text(),
					value.number(), value.formula(), value.valueMissing()));
			totalCells++;
			return true;
		}

		private void closeRow() {
			if (cells != null && !cells.isEmpty()) {
				rows.add(new Row(rowNumber, List.copyOf(cells)));
			}
			cells = null;
		}

		private void closeSheet() {
			if (rows == null) {
				return;
			}
			closeRow();
			sheets.add(new Sheet(sheetName, List.copyOf(rows), truncated));
			if (truncated) {
				warnings.add("Feuille « " + sheetName + " » lue en partie (limite de " + limits.maxRows()
						+ " lignes, " + limits.maxColumns() + " colonnes ou " + limits.maxCells() + " cellules)");
			}
			rows = null;
		}

		Spreadsheet result() {
			closeSheet();
			return new Spreadsheet(List.copyOf(sheets), List.copyOf(warnings));
		}

	}

	private record Value(Spreadsheet.CellType type, String text, BigDecimal number, String formula,
			boolean valueMissing) {
	}

	private record PendingCell(int column, int repeat, String valueType, String value, String dateValue,
			String booleanValue, String formula, StringBuilder text) {
	}

	/**
	 * Stops reading beyond a number of bytes: an archive entry announced small
	 * may hold much more once uncompressed.
	 */
	private static final class BoundedInputStream extends InputStream {

		private final InputStream in;

		private long remaining;

		BoundedInputStream(InputStream in, long max) {
			this.in = in;
			this.remaining = max;
		}

		@Override
		public int read() throws IOException {
			if (remaining <= 0) {
				throw new TooLargeException();
			}
			int b = in.read();
			if (b >= 0) {
				remaining--;
			}
			return b;
		}

		@Override
		public int read(byte[] buffer, int offset, int length) throws IOException {
			if (remaining <= 0) {
				throw new TooLargeException();
			}
			int read = in.read(buffer, offset, (int) Math.min(length, remaining));
			if (read > 0) {
				remaining -= read;
			}
			return read;
		}

		@Override
		public void close() throws IOException {
			in.close();
		}

	}

	/**
	 * @param maxBytes largest file read
	 * @param maxUncompressedBytes largest content of an XLSX or ODS archive entry
	 * @param maxSheets sheets read in a file
	 * @param maxRows rows read in a sheet
	 * @param maxColumns columns read in a row
	 * @param maxCells cells read in a file
	 */
	record Limits(long maxBytes, long maxUncompressedBytes, int maxSheets, int maxRows, int maxColumns, int maxCells) {

		static final Limits DEFAULT = new Limits(20L * 1024 * 1024, 200L * 1024 * 1024, 50, 20_000, 200, 300_000);

	}

	public static class TooLargeException extends IOException {

		TooLargeException() {
			super("Spreadsheet too large");
		}

	}

	public static class UnreadableSpreadsheetException extends IOException {

		UnreadableSpreadsheetException(String reason) {
			super(reason);
		}

	}

}
