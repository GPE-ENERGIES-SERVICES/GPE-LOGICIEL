package eu.gpefrance.tenders.equipment;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * The equipment table of GPE (« Tableau matériel ») for a tender: what is
 * installed, its brand and its reference, in the order of the model of GPE,
 * with the sentence saying the technical sheets are attached.
 */
final class EquipmentTable {

	static final String SHEETS_ATTACHED = "Les fiches techniques de l’ensemble du matériel retenu dans le cadre de ce projet "
			+ "sont annexées au présent dossier de candidature.";

	private EquipmentTable() {
	}

	static byte[] write(String tenderTitle, List<EquipmentService.Chosen> equipment) {
		try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Tableau matériel");
			CellStyle header = style(workbook, true);
			CellStyle cell = style(workbook, false);
			sheet.createRow(0).createCell(0).setCellValue("Tableau matériel – " + tenderTitle);
			Row titles = sheet.createRow(2);
			String[] columns = { "Matériel", "Marque", "Référence" };
			for (int column = 0; column < columns.length; column++) {
				titles.createCell(column).setCellValue(columns[column]);
				titles.getCell(column).setCellStyle(header);
			}
			Row note = sheet.createRow(3);
			note.createCell(0).setCellValue(SHEETS_ATTACHED);
			sheet.addMergedRegion(new CellRangeAddress(3, 3, 0, 2));
			int number = 5;
			for (EquipmentService.Chosen chosen : equipment) {
				EquipmentService.Sheet item = chosen.sheet();
				Row row = sheet.createRow(number++);
				// As GPE writes it: « SUN2000-40-KTL (x2) » for several of a model, not for modules or cables.
				boolean several = chosen.quantity() != null && chosen.quantity().compareTo(java.math.BigDecimal.ONE) > 0
						&& !item.category().equals("MODULE") && !item.category().startsWith("CABLE");
				String[] values = { EquipmentService.TABLE_LABELS.get(item.category()),
						item.brand() == null ? "-" : item.brand(), item.reference()
								+ (several ? " (x" + chosen.quantity().stripTrailingZeros().toPlainString() + ")" : "") };
				for (int column = 0; column < values.length; column++) {
					row.createCell(column).setCellValue(values[column]);
					row.getCell(column).setCellStyle(cell);
				}
			}
			sheet.setColumnWidth(0, 50 * 256);
			sheet.setColumnWidth(1, 25 * 256);
			sheet.setColumnWidth(2, 45 * 256);
			workbook.write(out);
			return out.toByteArray();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static CellStyle style(XSSFWorkbook workbook, boolean header) {
		CellStyle style = workbook.createCellStyle();
		style.setBorderBottom(BorderStyle.THIN);
		style.setBorderTop(BorderStyle.THIN);
		style.setBorderLeft(BorderStyle.THIN);
		style.setBorderRight(BorderStyle.THIN);
		style.setWrapText(true);
		if (header) {
			Font font = workbook.createFont();
			font.setBold(true);
			style.setFont(font);
			style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
			style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		}
		return style;
	}

}
