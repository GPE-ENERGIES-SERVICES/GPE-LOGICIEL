package eu.gpefrance.tenders.estimate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import eu.gpefrance.tenders.tender.TenderRepository;

/**
 * An estimate written as a spreadsheet: the items as in a price schedule,
 * then the costs of each item. Every figure is the one computed by the
 * application, written as a value, not as a formula.
 */
@Service
public class EstimateExportService {

	private static final String[] ITEM_COLUMNS = { "Chapitre", "N°", "Désignation", "Unité", "Quantité",
			"PU HT", "Montant HT", "Option", "Déboursé unitaire" };

	private static final String[] COMPONENT_COLUMNS = { "Poste", "Désignation du poste", "Type", "Libellé",
			"Quantité par unité", "Unité", "Coût unitaire", "Perte", "Coefficient", "Fournisseur", "Réf. fournisseur",
			"Réf. fabricant", "Date du prix", "Taux GPE" };

	private static final String[] TYPE_LABELS = { "Fourniture", "Main-d'œuvre", "Prestation", "Déplacement",
			"Location", "Sous-traitance", "Forfait", "Divers" };

	private final EstimateService estimates;

	private final TenderRepository tenders;

	public EstimateExportService(EstimateService estimates, TenderRepository tenders) {
		this.estimates = estimates;
		this.tenders = tenders;
	}

	public record Export(String fileName, byte[] content) {
	}

	@Transactional(readOnly = true)
	public Export export(Long id) {
		Estimate estimate = estimates.found(id);
		String title = tenders.findById(estimate.getTenderId()).map(tender -> tender.getTitle()).orElse("");
		try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			CellStyle bold = workbook.createCellStyle();
			Font font = workbook.createFont();
			font.setBold(true);
			bold.setFont(font);
			CellStyle money = workbook.createCellStyle();
			money.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));

			Sheet items = workbook.createSheet("Chiffrage");
			int r = 0;
			text(items.createRow(r++), 0, title, bold);
			text(items.createRow(r++), 0, "Version " + estimate.getVersion() + " — " + status(estimate)
					+ (estimate.getLotNumber() == null ? "" : " — lot " + estimate.getLotNumber())
					+ (estimate.getLabel() == null ? "" : " — " + estimate.getLabel()), null);
			r++;
			header(items.createRow(r++), ITEM_COLUMNS, bold);
			for (EstimateItem item : estimate.getItems()) {
				Row row = items.createRow(r++);
				text(row, 0, item.getChapter(), null);
				text(row, 1, item.getReference(), null);
				text(row, 2, item.getDesignation(), null);
				text(row, 3, item.getUnit(), null);
				number(row, 4, item.getQuantity(), null);
				if (item.getPriceMode() == PriceMode.TEXT) {
					text(row, 5, item.getTextPrice(), null);
				}
				else {
					number(row, 5, item.getUnitPrice(), money);
				}
				number(row, 6, item.getAmount(), money);
				text(row, 7, item.isOption() ? "Option" : null, null);
				number(row, 8, item.getUnitCost(), money);
			}
			r++;
			r = total(items, r, "Total HT", estimate.getTotalHt(), bold, money);
			r = total(items, r, "TVA " + estimate.getVatRate().movePointRight(2).stripTrailingZeros().toPlainString()
					+ " %", estimate.getTotalVat(), bold, money);
			r = total(items, r, "Total TTC", estimate.getTotalTtc(), bold, money);
			if (estimate.getOptionsHt().signum() != 0) {
				r = total(items, r, "Options HT (hors total)", estimate.getOptionsHt(), bold, money);
			}
			if (estimate.getTotalCost() != null) {
				r = total(items, r, "Déboursé total", estimate.getTotalCost(), bold, money);
				total(items, r, "Marge", estimate.getTotalHt().subtract(estimate.getTotalCost()), bold, money);
			}
			widths(items, 18, 8, 60, 8, 12, 14, 16, 10, 16);

			Sheet costs = workbook.createSheet("Sous-détail");
			r = 0;
			header(costs.createRow(r++), COMPONENT_COLUMNS, bold);
			for (EstimateItem item : estimate.getItems()) {
				for (EstimateComponent component : item.getComponents()) {
					Row row = costs.createRow(r++);
					text(row, 0, item.getReference() == null ? String.valueOf(item.getPosition()) : item.getReference(),
							null);
					text(row, 1, item.getDesignation(), null);
					text(row, 2, TYPE_LABELS[component.getType().ordinal()], null);
					text(row, 3, component.getLabel(), null);
					number(row, 4, component.getQuantity(), null);
					text(row, 5, component.getUnit(), null);
					number(row, 6, component.getUnitCost(), null);
					number(row, 7, component.getWasteRate(), null);
					number(row, 8, component.getCoefficient() != null ? component.getCoefficient()
							: estimate.getCoefficients().get(component.getType()), null);
					text(row, 9, component.getSupplierName(), null);
					text(row, 10, component.getSupplierRef(), null);
					text(row, 11, component.getManufacturerRef(), null);
					text(row, 12, component.getCostDate() == null ? null : component.getCostDate().toString(), null);
					text(row, 13, component.getRateCode(), null);
				}
			}
			widths(costs, 10, 40, 14, 40, 12, 8, 14, 8, 10, 16, 16, 16, 12, 10);
			workbook.write(out);
			return new Export("chiffrage-" + estimate.getTenderId() + "-v" + estimate.getVersion() + ".xlsx",
					out.toByteArray());
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static String status(Estimate estimate) {
		return switch (estimate.getStatus()) {
			case DRAFT -> "brouillon";
			case VALIDATED -> "validée par " + estimate.getValidatedBy();
			case SUPERSEDED -> "remplacée";
		};
	}

	private static int total(Sheet sheet, int r, String label, BigDecimal value, CellStyle bold, CellStyle money) {
		Row row = sheet.createRow(r);
		text(row, 5, label, bold);
		number(row, 6, value, money);
		return r + 1;
	}

	private static void header(Row row, String[] columns, CellStyle bold) {
		for (int i = 0; i < columns.length; i++) {
			text(row, i, columns[i], bold);
		}
	}

	private static void text(Row row, int column, String value, CellStyle style) {
		if (value != null) {
			row.createCell(column).setCellValue(value);
			if (style != null) {
				row.getCell(column).setCellStyle(style);
			}
		}
	}

	private static void number(Row row, int column, BigDecimal value, CellStyle style) {
		if (value != null) {
			row.createCell(column).setCellValue(value.doubleValue());
			if (style != null) {
				row.getCell(column).setCellStyle(style);
			}
		}
	}

	private static void widths(Sheet sheet, int... characters) {
		for (int i = 0; i < characters.length; i++) {
			sheet.setColumnWidth(i, characters[i] * 256);
		}
	}

}
