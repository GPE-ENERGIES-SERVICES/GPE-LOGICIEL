package eu.gpefrance.tenders.document;

import static eu.gpefrance.tenders.document.TestPdfTables.AMOUNT;
import static eu.gpefrance.tenders.document.TestPdfTables.DESIGNATION;
import static eu.gpefrance.tenders.document.TestPdfTables.QUANTITY;
import static eu.gpefrance.tenders.document.TestPdfTables.REF;
import static eu.gpefrance.tenders.document.TestPdfTables.UNIT;
import static eu.gpefrance.tenders.document.TestPdfTables.UNIT_PRICE;
import static eu.gpefrance.tenders.document.TestPdfTables.header;
import static eu.gpefrance.tenders.document.TestPdfTables.line;
import static eu.gpefrance.tenders.document.TestPdfTables.page;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import eu.gpefrance.tenders.document.PdfTable.Cell;
import eu.gpefrance.tenders.document.PdfTable.Column;
import eu.gpefrance.tenders.document.PdfTable.Row;
import eu.gpefrance.tenders.document.TestPdfTables.Text;

class PdfTableExtractorTests {

	private final PdfTableExtractor extractor = new PdfTableExtractor();

	@TempDir
	private Path folder;

	@Test
	void rebuildsTheColumnsOfAScheduleAndKeepsEachValueInItsOwn() throws IOException {
		List<PdfTable> tables = extract(TestPdfTables.dpgf());

		assertThat(tables).hasSize(1);
		PdfTable table = tables.getFirst();
		assertThat(table.reliable()).as(table.problems().toString()).isTrue();
		assertThat(table.pages()).containsExactly(1);
		assertThat(table.columns()).extracting(Column::label)
			.containsExactly("N°", "Désignation des ouvrages", "U", "Qté", "PU HT", "Montant HT");
		// The title above the header is not a row; the lines keep their page and height.
		assertThat(table.rows()).extracting(Row::page).containsOnly(1);
		assertThat(table.rows().getFirst().y()).isBetween(116f, 120f);
		assertThat(values(table.rows().get(0))).isEqualTo(Map.of(0, "1.1", 1, "Décapage de terre végétale", 2, "m2",
				3, "1 200", 4, "2,50", 5, "3 000,00"));
		assertThat(values(table.rows().get(1))).containsEntry(3, "350").containsEntry(4, "18,40")
			.containsEntry(5, "6 440,00");
		// A total has only an amount: it stays in the amount column.
		assertThat(values(table.rows().get(3))).isEqualTo(Map.of(1, "Total HT", 5, "13 740,00"));
		assertThat(table.rows().get(0).cells()).noneMatch(Cell::ambiguous);
	}

	@Test
	void joinsAHeaderWrittenOnTwoLines() throws IOException {
		List<Text> header = List.of(Text.right(UNIT_PRICE, 90, "Prix"), Text.right(AMOUNT, 90, "Montant"),
				Text.left(REF, 100, "N°"), Text.left(DESIGNATION, 100, "Désignation"), Text.left(UNIT, 100, "U"),
				Text.right(QUANTITY, 100, "Quantité"), Text.right(UNIT_PRICE, 100, "unitaire HT"),
				Text.right(AMOUNT, 100, "total HT"));

		PdfTable table = extract(TestPdfTables.pdf(page(header,
				line(118, "A01", "Bordure T2 béton", "ml", "120", "35,00", "4 200,00")))).getFirst();

		assertThat(table.headerLines()).isEqualTo(2);
		assertThat(table.columns()).extracting(Column::label)
			.containsExactly("N°", "Désignation", "U", "Quantité", "Prix unitaire HT", "Montant total HT");
		assertThat(values(table.rows().getFirst())).containsEntry(3, "120").containsEntry(4, "35,00")
			.containsEntry(5, "4 200,00");
	}

	@Test
	void followsATableOverSeveralPagesWithOrWithoutItsHeader() throws IOException {
		byte[] pdf = TestPdfTables.pdf(
				page(header(100), line(118, "1.1", "Décapage", "m2", "1 200", "2,50", "3 000,00"),
						line(132, "1.2", "Déblais", "m3", "350", "18,40", "6 440,00")),
				page(line(60, "1.3", "Évacuation", "m3", "350", "12,00", "4 200,00"),
						line(74, "1.4", "Remblais", "m3", "80", "", "")),
				page(header(60), line(78, "2.1", "Grave non traitée", "t", "45,5", "31,20", "1 419,60"),
						line(100, "", "Total HT", "", "", "", "15 059,60")));

		List<PdfTable> tables = extract(pdf);

		assertThat(tables).hasSize(1);
		PdfTable table = tables.getFirst();
		assertThat(table.pages()).containsExactly(1, 2, 3);
		assertThat(table.reliable()).as(table.problems().toString()).isTrue();
		assertThat(table.rows()).extracting(Row::page).containsExactly(1, 1, 2, 2, 3, 3);
		assertThat(values(table.rows().get(2))).containsEntry(3, "350").containsEntry(4, "12,00")
			.containsEntry(5, "4 200,00");
		// Missing prices leave their cells empty, the quantity stays a quantity.
		assertThat(values(table.rows().get(3))).isEqualTo(Map.of(0, "1.4", 1, "Remblais", 2, "m3", 3, "80"));
		assertThat(values(table.rows().get(4))).containsEntry(3, "45,5").containsEntry(4, "31,20");
	}

	@Test
	void neverPutsAPriceOrAQuantityInAnotherColumnWhenCellsAreEmpty() throws IOException {
		PdfTable table = extract(TestPdfTables.pdf(page(header(100),
				line(118, "A01", "Prix seul", "u", "", "150,00", ""),
				line(132, "A02", "Quantité seule", "u", "12", "", ""),
				line(146, "A03", "Montant seul", "ft", "", "", "980,00")))).getFirst();

		assertThat(values(table.rows().get(0))).containsEntry(4, "150,00").doesNotContainKeys(3, 5);
		assertThat(values(table.rows().get(1))).containsEntry(3, "12").doesNotContainKeys(4, 5);
		assertThat(values(table.rows().get(2))).containsEntry(5, "980,00").doesNotContainKeys(3, 4);
	}

	@Test
	void saysWhenAValueLiesAcrossTwoColumnsInsteadOfGuessing() throws IOException {
		PdfTable table = extract(TestPdfTables.pdf(page(header(100),
				line(118, "1.1", "Décapage", "m2", "1 200", "2,50", "3 000,00"),
				line(132, "1.2", "Déblais", "m3", "350", "18,40", "6 440,00"),
				// A value written between the quantity and the price.
				List.of(Text.left(REF, 146, "1.3"), Text.left(DESIGNATION, 146, "Évacuation"),
						Text.right(405, 146, "125 000,00"))))).getFirst();

		assertThat(table.reliable()).isFalse();
		assertThat(table.problems()).anySatisfy(problem -> assertThat(problem).contains("125 000,00", "Page 1"));
		assertThat(table.rows().get(2).cells()).anyMatch(Cell::ambiguous);
		assertThat(table.rows().get(0).cells()).noneMatch(Cell::ambiguous);
	}

	@Test
	void saysWhenTwoValuesFallInOneColumn() throws IOException {
		PdfTable table = extract(TestPdfTables.pdf(page(header(100),
				line(118, "1.1", "Décapage", "m2", "1 200", "2,50", "3 000,00"),
				List.of(Text.left(REF, 132, "1.2"), Text.left(DESIGNATION, 132, "Déblais"),
						Text.right(QUANTITY, 132, "12 15,5"))))).getFirst();

		assertThat(table.reliable()).isFalse();
		assertThat(table.problems()).anySatisfy(problem -> assertThat(problem).contains("regroupe plusieurs valeurs"));
	}

	@Test
	void keepsALongDesignationInItsColumn() throws IOException {
		PdfTable table = extract(TestPdfTables.pdf(page(header(100),
				line(118, "1.1", "Fourniture et pose de canalisation PVC DN 200", "ml", "85", "64,10",
						"5 448,50"),
				line(132, "", "lit de pose et enrobage en sable", "", "", "", "")))).getFirst();

		assertThat(table.reliable()).as(table.problems().toString()).isTrue();
		assertThat(values(table.rows().get(0))).containsEntry(1,
				"Fourniture et pose de canalisation PVC DN 200").containsEntry(2, "ml");
		assertThat(values(table.rows().get(1))).isEqualTo(Map.of(1, "lit de pose et enrobage en sable"));
	}

	@Test
	void acceptsValuesAlignedToTheLeftAndSectionTitles() throws IOException {
		List<Text> lines = List.of(Text.left(DESIGNATION, 118, "LOT 1 - VOIRIE"),
				Text.left(REF, 132, "1.1"), Text.left(DESIGNATION, 132, "Rabotage"), Text.left(UNIT, 132, "m2"),
				Text.left(345, 132, "1 250"), Text.left(400, 132, "4,10"), Text.left(480, 132, "5 125,00"),
				Text.left(REF, 146, "1.2"), Text.left(DESIGNATION, 146, "Enrobé"), Text.left(UNIT, 146, "t"),
				Text.left(345, 146, "86,5"), Text.left(400, 146, "98,00"), Text.left(480, 146, "8 477,00"),
				Text.left(REF, 160, "1.3"), Text.left(DESIGNATION, 160, "Signalisation"), Text.left(UNIT, 160, "ft"),
				Text.left(345, 160, "1"), Text.left(400, 160, "2 300,00"), Text.left(480, 160, "2 300,00"));
		List<Text> header = List.of(Text.left(REF, 100, "N°"), Text.left(DESIGNATION, 100, "Désignation"),
				Text.left(UNIT, 100, "U"), Text.left(345, 100, "Qté"), Text.left(400, 100, "PU HT"),
				Text.left(480, 100, "Montant HT"));

		PdfTable table = extract(TestPdfTables.pdf(page(header, lines))).getFirst();

		assertThat(table.reliable()).as(table.problems().toString()).isTrue();
		assertThat(values(table.rows().getFirst())).isEqualTo(Map.of(1, "LOT 1 - VOIRIE"));
		assertThat(values(table.rows().get(2))).containsEntry(3, "86,5").containsEntry(4, "98,00")
			.containsEntry(5, "8 477,00");
		assertThat(values(table.rows().get(3))).containsEntry(3, "1").containsEntry(4, "2 300,00")
			.containsEntry(5, "2 300,00");
	}

	@Test
	void readsATitleOrASubtotalLabelRunningOverTheColumnsAsOneTextWithoutAmbiguity() throws IOException {
		PdfTable table = extract(TestPdfTables.pdf(page(header(100),
				List.of(Text.left(REF, 118, "01.3.2"),
						Text.left(DESIGNATION, 118, "TRAVAUX PRELIMINAIRES - DEPOSES EN FACADES ET DES MENUISERIES EXTERIEURES")),
				line(132, "", "Échafaudage de pied", "m2", "420", "", ""),
				List.of(Text.left(DESIGNATION, 146,
						"Sous total € H.T. (TRAVAUX PRELIMINAIRES ET DEPOSES DES MENUISERIES)"))))).getFirst();

		assertThat(table.reliable()).as(table.problems().toString()).isTrue();
		assertThat(values(table.rows().getFirst())).isEqualTo(Map.of(0, "01.3.2", 1,
				"TRAVAUX PRELIMINAIRES - DEPOSES EN FACADES ET DES MENUISERIES EXTERIEURES"));
		assertThat(values(table.rows().get(2))).containsOnlyKeys(1);
	}

	@Test
	void joinsADesignationWrittenOnSeveralLinesToTheLineOfItsValues() throws IOException {
		PdfTable table = extract(TestPdfTables.pdf(page(header(100),
				line(118, "", "Installation de chantier", "", "", "", ""),
				line(132, "", "Clôture de chantier grillagée (type HERAS) sur", "", "", "", ""),
				line(144, "", "plots béton préfabriqués", "ml", "120", "", ""),
				line(158, "", "Autorisations (déclaration, accords des", "", "", "", ""),
				line(170, "", "services de la voirie)", "Ens", "1", "", ""),
				line(184, "", "Panneau de chantier", "U", "1", "", "")))).getFirst();

		assertThat(table.rows()).extracting(row -> values(row).get(1)).containsExactly("Installation de chantier",
				"Clôture de chantier grillagée (type HERAS) sur plots béton préfabriqués",
				"Autorisations (déclaration, accords des services de la voirie)",
				"Panneau de chantier");
		assertThat(values(table.rows().get(1))).containsEntry(2, "ml").containsEntry(3, "120");
		// Provenance: the line of the values.
		assertThat(table.rows().get(1).y()).isBetween(142f, 146f);
	}

	@Test
	void neverTakesAColumnOfPricesWrittenInWordsForAColumnOfValues() throws IOException {
		List<Text> header = List.of(Text.left(REF, 100, "N° prix"),
				Text.left(DESIGNATION, 100, "Désignation des prix et prix en toutes lettres"),
				Text.right(AMOUNT, 100, "Prix HT en chiffres"));

		PdfTable table = extract(TestPdfTables.pdf(page(header, line(118, "10", "Installation de chantier", "", "", "", ""),
				line(132, "", "conformément à l'article 31 du CCAG Travaux, 2 fois", "", "", "", ""),
				line(146, "", "Le forfait : ...................... euros", "", "", "", "")))).getFirst();

		assertThat(table.reliable()).as(table.problems().toString()).isFalse();
		assertThat(table.problems()).noneMatch(problem -> problem.contains("regroupe plusieurs valeurs"));
		assertThat(table.problems()).anyMatch(problem -> problem.contains("Aucune valeur chiffrée"));
	}

	@Test
	void rebuildsATableOfAScannedPageFromTheWordsReadByOcrWithoutCallingItReliable() throws IOException {
		Path scan = Files.write(folder.resolve("scan.pdf"), TestFiles.pdf(""));
		List<OcrEngine.OcrWord> words = List.of(ocr("N°", REF, 52, 100, 96), ocr("Désignation", DESIGNATION, 120, 100, 96),
				ocr("U", UNIT, 306, 100, 95), ocr("Qté", 356, QUANTITY, 100, 95), ocr("PU HT", 417, UNIT_PRICE, 100, 94),
				ocr("Montant HT", 480, AMOUNT, 100, 94),
				ocr("1.1", REF, 52, 118, 93), ocr("Décapage", DESIGNATION, 110, 118, 92), ocr("m2", UNIT, 311, 118, 90),
				ocr("1 200", 349, QUANTITY, 118, 91), ocr("2,50", 425, UNIT_PRICE, 118, 88),
				ocr("3 000,00", 495, AMOUNT, 118, 90),
				ocr("1.2", REF, 52, 132, 93), ocr("Déblais", DESIGNATION, 105, 132, 92), ocr("m3", UNIT, 311, 132, 90),
				ocr("350", 356, QUANTITY, 132, 91), ocr("18,40", 419, UNIT_PRICE, 132, 55),
				ocr("6 440,00", 495, AMOUNT, 132, 90));
		DocumentPage page = new DocumentPage(1L, 1, "texte lu", OcrEngine.toJson(words));

		List<PdfTable> tables = extractor.extract(scan, List.of(page));

		assertThat(tables).hasSize(1);
		PdfTable table = tables.getFirst();
		assertThat(values(table.rows().get(0))).containsEntry(3, "1 200").containsEntry(4, "2,50")
			.containsEntry(5, "3 000,00");
		assertThat(table.reliable()).isFalse();
		assertThat(table.problems().getFirst()).startsWith("Page(s) [1] lue(s) par reconnaissance de caractères (OCR)");
		assertThat(table.problems()).anyMatch(problem -> problem.contains("« 18,40 » lu par OCR avec une confiance de 55 %"));
		assertThat(table.rows().get(1).cells()).filteredOn(cell -> cell.column() == 4).allMatch(Cell::ambiguous);
		assertThat(table.rows().get(0).cells()).noneMatch(Cell::ambiguous);
	}

	private static OcrEngine.OcrWord ocr(String text, float x0, float x1, float y, float confidence) {
		return new OcrEngine.OcrWord(text, x0, x1, y, 9, confidence);
	}

	@Test
	void findsNoTableInAPageOfProseNorInAScannedPage() throws IOException {
		byte[] prose = TestPdfTables.pdf(List.of(Text.left(REF, 100, "Article 3 - Les prix sont fermes."),
				Text.left(REF, 114, "Le montant total est indiqué à l'acte d'engagement.")));

		assertThat(extract(prose)).isEmpty();
		assertThat(extract(TestFiles.pdf("", ""))).isEmpty();
		assertThatThrownBy(() -> extractor.extract(Files.write(folder.resolve("faux.pdf"), "pas un PDF".getBytes())))
			.isInstanceOf(IOException.class);
	}

	@Test
	void turnsATableIntoASheetAndWritesItWithItsPages() throws IOException {
		PdfTable table = extract(TestPdfTables.dpgf()).getFirst();

		Spreadsheet.Sheet sheet = PdfTableText.sheet(table, 1);
		String text = PdfTableText.render(12L, "DPGF.pdf", List.of(table));

		assertThat(sheet.name()).isEqualTo("Tableau 1 (p. 1)");
		assertThat(sheet.rows().getFirst().cells()).extracting(Spreadsheet.Cell::text)
			.containsExactly("N°", "Désignation des ouvrages", "U", "Qté", "PU HT", "Montant HT");
		assertThat(sheet.rows().get(1).cell(3).text()).isEqualTo("1 200");
		assertThat(sheet.rows().get(1).cell(3).numeric()).isEqualByComparingTo("1200");
		assertThat(PdfTableText.page(table, 2)).isEqualTo(1);
		assertThat(text).startsWith("<tableau document=\"12\" nom=\"DPGF.pdf\" feuille=\"Tableau 1 (p. 1)\" "
				+ "fiabilite=\"fiable\">")
			.contains("Colonnes : A « N° » | B « Désignation des ouvrages » | C « U » | D « Qté » | E « PU HT » "
					+ "| F « Montant HT »")
			.contains("L2 p1 y118 | A2 \"1.1\" | B2 \"Décapage de terre végétale\" | C2 \"m2\" | D2 \"1 200\" "
					+ "| E2 \"2,50\" | F2 \"3 000,00\"");
	}

	@Test
	void leavesTheTextOfThePagesAsItWas() throws IOException {
		Path pdf = Files.write(folder.resolve("dpgf.pdf"), TestPdfTables.dpgf());

		List<String> pages = new PdfTextExtractor().extractPages(pdf);

		assertThat(pages).hasSize(1);
		assertThat(pages.getFirst()).contains("DPGF - Lot 1 Terrassement",
				"1.1 Décapage de terre végétale m2 1 200 2,50 3 000,00", "Total HT 13 740,00", "Page 1 / 1");
	}

	private List<PdfTable> extract(byte[] pdf) throws IOException {
		return extractor.extract(Files.write(folder.resolve("table.pdf"), pdf));
	}

	private static Map<Integer, String> values(Row row) {
		return row.cells().stream().collect(Collectors.toMap(Cell::column, Cell::text));
	}

}
