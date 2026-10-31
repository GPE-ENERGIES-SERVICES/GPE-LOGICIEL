package eu.gpefrance.tenders.document;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Synthetic price schedules, made for the tests: no real consultation file.
 */
public final class TestSpreadsheets {

	private TestSpreadsheets() {
	}

	/**
	 * A DPGF of two sheets: the first with a header, three lines (the third
	 * with a wrong amount), a subtotal, a total HT, the VAT and the TTC, and a
	 * formula left without saved value; the second with other column names.
	 */
	public static byte[] xlsx() {
		return workbook(new XSSFWorkbook(), true);
	}

	/** The same DPGF in the old Excel format. */
	public static byte[] xls() {
		return workbook(new HSSFWorkbook(), false);
	}

	private static byte[] workbook(Workbook workbook, boolean leaveOneFormulaUnsaved) {
		try (workbook; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Sheet dpgf = workbook.createSheet("DPGF Lot 1");
			row(dpgf, 0, "DPGF - Lot 1 Terrassement");
			row(dpgf, 2, "N°", "Désignation", "U", "Qté", "PU HT", "Montant HT");
			Row first = row(dpgf, 3, "1.1", "Décapage de terre végétale", "m2");
			first.createCell(3).setCellValue(1200);
			first.createCell(4).setCellValue(2.5);
			first.createCell(5).setCellFormula("D4*E4");
			Row second = row(dpgf, 4, "1.2", "Déblais en pleine masse", "m3");
			second.createCell(3).setCellValue(350);
			second.createCell(4).setCellValue(18.4);
			second.createCell(5).setCellFormula("D5*E5");
			Row third = row(dpgf, 5, "1.3", "Évacuation des déblais", "m3");
			third.createCell(3).setCellValue(350);
			third.createCell(4).setCellValue(12);
			third.createCell(5).setCellValue(4300);
			Row subtotal = row(dpgf, 6, "", "Sous-total terrassement");
			subtotal.createCell(5).setCellFormula("SUM(F4:F6)");
			Row total = row(dpgf, 8, "", "Total HT");
			total.createCell(5).setCellValue(13740);
			Row vat = row(dpgf, 9, "", "TVA 20 %");
			vat.createCell(5).setCellValue(2748);
			Row ttc = row(dpgf, 10, "", "Total TTC");
			ttc.createCell(5).setCellValue(16488);
			Row date = row(dpgf, 12, "", "Date de valeur");
			CellStyle style = workbook.createCellStyle();
			style.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy"));
			date.createCell(6).setCellValue(LocalDate.of(2026, 11, 3));
			date.getCell(6).setCellStyle(style);
			workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
			if (leaveOneFormulaUnsaved) {
				// A formula written after the computation: its result is not saved.
				row(dpgf, 13, "", "Révision").createCell(5).setCellFormula("F9*0.02");
			}

			Sheet bpu = workbook.createSheet("BPU");
			row(bpu, 0, "Code", "Libellé de la prestation", "Unité", "Prix unitaire en euros HT");
			row(bpu, 1, "A01", "Enrobé BBSG 0/10 ép. 6 cm", "t");
			row(bpu, 2, "A02", "Bordure T2 béton", "ml", "PM");
			workbook.write(out);
			return out.toByteArray();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static Row row(Sheet sheet, int index, String... texts) {
		Row row = sheet.createRow(index);
		for (int i = 0; i < texts.length; i++) {
			if (!texts[i].isEmpty()) {
				row.createCell(i).setCellValue(texts[i]);
			}
		}
		return row;
	}

	/**
	 * An ODS file: repeated cells and rows, a currency value, a formula with
	 * its value and one without, and thousands of empty rows at the end.
	 */
	public static byte[] ods() {
		String content = """
				<?xml version="1.0" encoding="UTF-8"?>
				<office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
				 xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0"
				 xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0"><office:body><office:spreadsheet>
				<table:table table:name="DQE">
				<table:table-row><table:table-cell office:value-type="string"><text:p>Désignation</text:p></table:table-cell>
				<table:table-cell office:value-type="string"><text:p>Unité</text:p></table:table-cell>
				<table:table-cell office:value-type="string"><text:p>Quantité</text:p></table:table-cell>
				<table:table-cell office:value-type="string"><text:p>Prix unitaire</text:p></table:table-cell>
				<table:table-cell office:value-type="string"><text:p>Montant</text:p></table:table-cell></table:table-row>
				<table:table-row><table:table-cell office:value-type="string"><text:p>Pose<text:s text:c="2"/>de regards</text:p></table:table-cell>
				<table:table-cell office:value-type="string"><text:p>u</text:p></table:table-cell>
				<table:table-cell office:value-type="float" office:value="4"><text:p>4</text:p></table:table-cell>
				<table:table-cell office:value-type="currency" office:currency="EUR" office:value="350.5"><text:p>350,50 €</text:p></table:table-cell>
				<table:table-cell table:formula="of:=[.C2]*[.D2]" office:value-type="float" office:value="1402"><text:p>1402</text:p></table:table-cell></table:table-row>
				<table:table-row table:number-rows-repeated="2"><table:table-cell table:number-columns-repeated="5"/></table:table-row>
				<table:table-row><table:table-cell table:number-columns-repeated="2"/>
				<table:table-cell office:value-type="float" office:value="7"><text:p>7</text:p></table:table-cell>
				<table:table-cell table:number-columns-repeated="1"/>
				<table:table-cell table:formula="of:=[.C5]*[.D5]"/></table:table-row>
				<table:table-row table:number-rows-repeated="1048000"><table:table-cell table:number-columns-repeated="1024"/></table:table-row>
				</table:table>
				</office:spreadsheet></office:body></office:document-content>
				""".strip();
		return zip("content.xml", content);
	}

	/** An archive that is not an ODS file. */
	public static byte[] zip(String entry, String content) {
		try (ByteArrayOutputStream out = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(out)) {
			zip.putNextEntry(new ZipEntry("mimetype"));
			zip.write("application/vnd.oasis.opendocument.spreadsheet".getBytes(StandardCharsets.US_ASCII));
			zip.closeEntry();
			zip.putNextEntry(new ZipEntry(entry));
			zip.write(content.getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
			zip.finish();
			return out.toByteArray();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/** A CSV file as Excel writes it in French: semicolons, decimal commas. */
	public static String csv() {
		return """
				Désignation;Unité;Quantité;Prix unitaire HT;Montant HT
				"Fouilles en tranchée; terrain ordinaire";m3;"1 250,5";12,40;15506,20
				"Grillage avertisseur
				rouge";ml;800;0,85;680
				=HYPERLINK("http://example.invalid");u;1;;
				Total HT;;;;16186,20
				""";
	}

}
