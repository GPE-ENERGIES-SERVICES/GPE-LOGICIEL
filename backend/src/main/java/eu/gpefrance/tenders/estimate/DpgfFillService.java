package eu.gpefrance.tenders.estimate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import eu.gpefrance.tenders.analysis.PriceCheck;
import eu.gpefrance.tenders.analysis.PriceScheduleChecker;
import eu.gpefrance.tenders.analysis.PriceScheduleChecker.FillColumns;
import eu.gpefrance.tenders.document.Document;
import eu.gpefrance.tenders.document.DocumentProcessor;
import eu.gpefrance.tenders.document.DocumentRepository;
import eu.gpefrance.tenders.document.FileStorage;
import eu.gpefrance.tenders.document.Spreadsheet;
import eu.gpefrance.tenders.document.SpreadsheetExtractor;

/**
 * The price schedule of the project manager given back filled: its own file,
 * layout, sheets and formulas untouched, with the quantity of the company and
 * the unit price of each item imported from it written on the row it came
 * from. An amount is written only where the frame has no formula; Excel
 * computes the others, and the totals, when the file is opened.
 */
@Service
public class DpgfFillService {

	private static final Logger log = LoggerFactory.getLogger(DpgfFillService.class);

	private static final Set<String> FILLABLE = Set.of("xlsx", "xlsm", "xls");

	private final EstimateService estimates;

	private final DocumentRepository documents;

	private final FileStorage storage;

	private final SpreadsheetExtractor spreadsheets;

	public DpgfFillService(EstimateService estimates, DocumentRepository documents, FileStorage storage,
			SpreadsheetExtractor spreadsheets) {
		this.estimates = estimates;
		this.documents = documents;
		this.storage = storage;
		this.spreadsheets = spreadsheets;
	}

	public record Filled(String fileName, String contentType, byte[] content) {
	}

	@Transactional(readOnly = true)
	public Filled fill(Long estimateId, Long documentId) {
		Estimate estimate = estimates.found(estimateId);
		Document document = documents.findById(documentId)
			.orElseThrow(() -> EstimateRuleException.notFound("Document introuvable."));
		if (!document.getDossier().getTenderId().equals(estimate.getTenderId())) {
			throw EstimateRuleException.invalid("Ce document n'appartient pas au marché du chiffrage.");
		}
		String extension = DocumentProcessor.extension(document.getFileName());
		if (!FILLABLE.contains(extension)) {
			throw EstimateRuleException.invalid("Seul un DPGF Excel (.xlsx, .xls) peut être rempli : « "
					+ document.getFileName() + " » ne l'est pas.");
		}
		Path file = storage.resolve(document.getStoragePath());
		try (InputStream in = Files.newInputStream(file); Workbook workbook = WorkbookFactory.create(in);
				ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Map<String, Optional<FillColumns>> columns = columns(file, extension);
			// A frame whose price column is not found is never filled halfway, quantities without prices.
			estimate.getItems()
				.stream()
				.filter(item -> documentId.equals(item.getSourceDocumentId()))
				.map(EstimateItem::getSourceSheet)
				.distinct()
				.filter(sheet -> columns.getOrDefault(sheet, Optional.empty())
					.map(found -> found.unitPrice() == null)
					.orElse(true))
				.findFirst()
				.ifPresent(sheet -> {
					throw EstimateRuleException.invalid("Colonne du prix unitaire non reconnue dans la feuille « " + sheet
							+ " » de « " + document.getFileName() + " » : le DPGF n'est pas rempli.");
				});
			int written = 0;
			for (EstimateItem item : estimate.getItems()) {
				if (!documentId.equals(item.getSourceDocumentId()) || item.getSourceRow() == null) {
					continue;
				}
				Sheet sheet = workbook.getSheet(item.getSourceSheet());
				Optional<FillColumns> sheetColumns = columns.getOrDefault(item.getSourceSheet(), Optional.empty());
				Row row = sheet == null ? null : sheet.getRow(item.getSourceRow() - 1);
				if (row != null && sheetColumns.isPresent() && write(row, sheetColumns.get(), item)) {
					written++;
				}
			}
			workbook.setForceFormulaRecalculation(true);
			try {
				workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
			}
			catch (RuntimeException ex) {
				// A function POI does not know: Excel computes it when the file is opened.
				log.info("Formulas of schedule {} left to Excel: {}", documentId, ex.getClass().getSimpleName());
			}
			workbook.write(out);
			log.info("Schedule {} filled from estimate {}: {} rows", documentId, estimateId, written);
			return new Filled(filledName(document.getFileName(), estimate.getVersion()), contentType(extension),
					out.toByteArray());
		}
		catch (IOException ex) {
			log.warn("Schedule {} could not be filled: {}", documentId, ex.getClass().getSimpleName());
			throw EstimateRuleException.invalid("Le DPGF n'a pas pu être relu : " + document.getFileName());
		}
	}

	/**
	 * What to check in the filled schedule before sending it: subtotals and
	 * totals that do not add up their lines, a VAT not matching, an amount other
	 * than quantity × unit price, a cell in error; and its total excluding tax.
	 * @param problems one sentence each, with the sheet and the cell
	 * @param total the sum of the lines as computed, null when not readable
	 */
	public record Checked(List<String> problems, java.math.BigDecimal total) {
	}

	private static final Set<String> CHECKED = Set.of("TOTAL_MISMATCH", "VAT_MISMATCH", "LINE_MISMATCH",
			"ERROR_CELL", "NOT_SAVED");

	@Transactional(readOnly = true)
	public Checked check(Long estimateId, Long documentId) {
		Filled filled = fill(estimateId, documentId);
		String extension = DocumentProcessor.extension(filled.fileName());
		Path copy = null;
		try {
			copy = Files.createTempFile("filled-", "." + extension);
			Files.write(copy, filled.content());
			List<PriceCheck> checks = PriceScheduleChecker.check(documentId, filled.fileName(),
					spreadsheets.read(copy, extension));
			List<String> problems = new java.util.ArrayList<>();
			java.math.BigDecimal total = null;
			for (PriceCheck check : checks) {
				if (check.computedTotal() != null) {
					total = total == null ? check.computedTotal() : total.add(check.computedTotal());
				}
				check.issues()
					.stream()
					.filter(issue -> CHECKED.contains(issue.kind()))
					.forEach(issue -> problems.add(check.sheet() + (issue.ref() == null ? "" : " " + issue.ref()) + " : "
							+ issue.message()));
			}
			return new Checked(List.copyOf(problems), total);
		}
		catch (IOException ex) {
			throw EstimateRuleException.invalid("Le DPGF rempli n'a pas pu être relu pour le contrôle.");
		}
		finally {
			if (copy != null) {
				try {
					Files.deleteIfExists(copy);
				}
				catch (IOException ex) {
					log.warn("Temporary filled schedule not deleted: {}", ex.getClass().getSimpleName());
				}
			}
		}
	}

	/** The columns of each sheet, found as the import found them. */
	private Map<String, Optional<FillColumns>> columns(Path file, String extension) throws IOException {
		Map<String, Optional<FillColumns>> columns = new HashMap<>();
		for (Spreadsheet.Sheet sheet : spreadsheets.read(file, extension).sheets()) {
			columns.put(sheet.name(), PriceScheduleChecker.fillColumns(sheet));
		}
		return columns;
	}

	/**
	 * The quantity of the company in its column (in the quantity column when the
	 * frame has none), the unit price, and the amount where no formula computes
	 * it.
	 * @return whether something was written
	 */
	static boolean write(Row row, FillColumns columns, EstimateItem item) {
		// A line left without price is left as the frame gives it: no quantity of the company either.
		if (item.getPriceMode() != PriceMode.TEXT && item.getUnitPrice() == null) {
			return false;
		}
		boolean written = false;
		BigDecimal quantity = item.getQuantity();
		Integer quantityColumn = columns.companyQuantity() != null ? columns.companyQuantity() : columns.quantity();
		if (quantity != null && quantityColumn != null && !isFormula(row, quantityColumn)) {
			number(row, quantityColumn, quantity);
			written = true;
		}
		if (columns.unitPrice() == null) {
			return written;
		}
		if (item.getPriceMode() == PriceMode.TEXT) {
			if (item.getTextPrice() != null && !isFormula(row, columns.unitPrice())) {
				cell(row, columns.unitPrice()).setCellValue(item.getTextPrice());
				written = true;
			}
			return written;
		}
		BigDecimal price = item.getUnitPrice();
		if (price == null || isFormula(row, columns.unitPrice())) {
			return written;
		}
		number(row, columns.unitPrice(), price);
		if (columns.amount() != null && quantity != null && !isFormula(row, columns.amount())) {
			number(row, columns.amount(), price.multiply(quantity).setScale(2, RoundingMode.HALF_UP));
		}
		return true;
	}

	private static boolean isFormula(Row row, int column) {
		Cell cell = row.getCell(column);
		return cell != null && cell.getCellType() == CellType.FORMULA;
	}

	/** Written in the cell as it is, keeping its style (borders, number format). */
	private static void number(Row row, int column, BigDecimal value) {
		cell(row, column).setCellValue(value.doubleValue());
	}

	private static Cell cell(Row row, int column) {
		Cell cell = row.getCell(column);
		return cell != null ? cell : row.createCell(column);
	}

	static String filledName(String fileName, int version) {
		int dot = fileName.lastIndexOf('.');
		String base = dot < 0 ? fileName : fileName.substring(0, dot);
		String extension = dot < 0 ? "" : fileName.substring(dot);
		return base + " - GPE v" + version + extension;
	}

	private static String contentType(String extension) {
		return switch (extension) {
			case "xls" -> "application/vnd.ms-excel";
			case "xlsm" -> "application/vnd.ms-excel.sheet.macroEnabled.12";
			default -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
		};
	}

}
