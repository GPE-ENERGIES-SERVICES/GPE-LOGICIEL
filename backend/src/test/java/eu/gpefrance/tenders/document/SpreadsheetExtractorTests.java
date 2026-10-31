package eu.gpefrance.tenders.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import eu.gpefrance.tenders.document.Spreadsheet.Cell;
import eu.gpefrance.tenders.document.Spreadsheet.CellType;
import eu.gpefrance.tenders.document.Spreadsheet.Row;
import eu.gpefrance.tenders.document.Spreadsheet.Sheet;

class SpreadsheetExtractorTests {

	private final SpreadsheetExtractor extractor = new SpreadsheetExtractor();

	@TempDir
	private Path folder;

	@Test
	void readsTheCellsOfAnXlsxFileWithTheirCoordinatesTypesAndFormulas() throws IOException {
		Spreadsheet spreadsheet = extractor.read(file("dpgf.xlsx", TestSpreadsheets.xlsx()), "xlsx");

		assertThat(spreadsheet.sheets()).extracting(Sheet::name).containsExactly("DPGF Lot 1", "BPU");
		Sheet dpgf = spreadsheet.sheets().getFirst();
		assertThat(cell(dpgf, "B4")).extracting(Cell::type, Cell::text).containsExactly(CellType.TEXT,
				"Décapage de terre végétale");
		assertThat(cell(dpgf, "E5").number()).isEqualByComparingTo("18.4");
		// The formula and the value saved by the spreadsheet software, apart.
		assertThat(cell(dpgf, "F4")).extracting(Cell::type, Cell::formula, Cell::valueMissing)
			.containsExactly(CellType.NUMBER, "D4*E4", false);
		assertThat(cell(dpgf, "F4").number()).isEqualByComparingTo("3000");
		// A formula without saved result: no value, never computed here.
		assertThat(cell(dpgf, "F14")).extracting(Cell::type, Cell::number, Cell::formula, Cell::valueMissing)
			.containsExactly(CellType.NO_VALUE, null, "F9*0.02", true);
		assertThat(cell(dpgf, "G13")).extracting(Cell::type, Cell::text).containsExactly(CellType.DATE, "2026-11-03");
		// Empty cells and rows are left out, the coordinates of the others kept.
		assertThat(dpgf.rows()).extracting(Row::number).containsExactly(1, 3, 4, 5, 6, 7, 9, 10, 11, 13, 14);
		assertThat(cell(dpgf, "A7")).isNull();
		assertThat(spreadsheet.warnings()).isEmpty();
	}

	@Test
	void readsAnXlsFileTheSameWay() throws IOException {
		Spreadsheet spreadsheet = extractor.read(file("dpgf.xls", TestSpreadsheets.xls()), "xls");

		Sheet dpgf = spreadsheet.sheets().getFirst();
		assertThat(cell(dpgf, "F5")).extracting(Cell::formula, Cell::valueMissing).containsExactly("D5*E5", false);
		assertThat(cell(dpgf, "F5").number()).isEqualByComparingTo("6440");
		assertThat(cell(dpgf, "F7").number()).isEqualByComparingTo("13740");
		assertThat(spreadsheet.sheets().get(1).rows()).hasSize(3);
	}

	@Test
	void readsAnOdsFileWithItsRepeatedCellsWithoutExpandingTheEmptyRows() throws IOException {
		Spreadsheet spreadsheet = extractor.read(file("dqe.ods", TestSpreadsheets.ods()), "ods");

		Sheet dqe = spreadsheet.sheets().getFirst();
		assertThat(dqe.name()).isEqualTo("DQE");
		assertThat(dqe.rows()).extracting(Row::number).containsExactly(1, 2, 5);
		assertThat(cell(dqe, "A2").text()).isEqualTo("Pose  de regards");
		assertThat(cell(dqe, "D2").number()).isEqualByComparingTo("350.5");
		assertThat(cell(dqe, "E2")).extracting(Cell::formula, Cell::valueMissing).containsExactly("[.C2]*[.D2]", false);
		assertThat(cell(dqe, "E2").number()).isEqualByComparingTo("1402");
		assertThat(cell(dqe, "C5").number()).isEqualByComparingTo("7");
		assertThat(cell(dqe, "E5")).extracting(Cell::type, Cell::valueMissing).containsExactly(CellType.NO_VALUE, true);
		assertThat(dqe.truncated()).isFalse();
	}

	@Test
	void readsAFrenchCsvFileWithQuotesDecimalCommasAndNeverAFormula() throws IOException {
		Spreadsheet spreadsheet = extractor.read(file("dpgf.csv", TestSpreadsheets.csv().getBytes(StandardCharsets.UTF_8)),
				"csv");

		Sheet sheet = spreadsheet.sheets().getFirst();
		assertThat(cell(sheet, "A2").text()).isEqualTo("Fouilles en tranchée; terrain ordinaire");
		assertThat(cell(sheet, "C2")).extracting(Cell::type, Cell::text).containsExactly(CellType.NUMBER, "1 250,5");
		assertThat(cell(sheet, "C2").number()).isEqualByComparingTo("1250.5");
		assertThat(cell(sheet, "E2").number()).isEqualByComparingTo("15506.20");
		assertThat(cell(sheet, "A3").text()).isEqualTo("Grillage avertisseur\nrouge");
		assertThat(cell(sheet, "A4")).extracting(Cell::type, Cell::formula)
			.containsExactly(CellType.TEXT, null);
		assertThat(sheet.rows()).extracting(Row::number).containsExactly(1, 2, 3, 4, 5);
	}

	@Test
	void readsACsvFileWrittenOnWindowsWithTabs() throws IOException {
		byte[] bytes = "Désignation\tQuantité\nBéton\t12,5\n".getBytes(Charset.forName("windows-1252"));

		Sheet sheet = extractor.read(file("bpu.csv", bytes), "csv").sheets().getFirst();

		assertThat(cell(sheet, "A2").text()).isEqualTo("Béton");
		assertThat(cell(sheet, "B2").number()).isEqualByComparingTo("12.5");
	}

	@Test
	void refusesEmptyDamagedAndMismatchedFiles() throws IOException {
		assertThatThrownBy(() -> extractor.read(file("vide.xlsx", new byte[0]), "xlsx"))
			.isInstanceOf(SpreadsheetExtractor.UnreadableSpreadsheetException.class)
			.hasMessage("Fichier vide");
		assertThatThrownBy(() -> extractor.read(file("abime.xlsx", "PK\u0003\u0004 abîmé".getBytes()), "xlsx"))
			.isInstanceOf(SpreadsheetExtractor.UnreadableSpreadsheetException.class);
		assertThatThrownBy(() -> extractor.read(file("faux.xlsx", TestFiles.pdf("Pas un tableur")), "xlsx"))
			.isInstanceOf(SpreadsheetExtractor.UnreadableSpreadsheetException.class)
			.hasMessageContaining("ne correspond pas à l'extension");
		assertThatThrownBy(() -> extractor.read(file("faux.xls", TestSpreadsheets.xlsx()), "ods"))
			.isInstanceOf(SpreadsheetExtractor.UnreadableSpreadsheetException.class);
		assertThatThrownBy(() -> extractor.read(file("sans-contenu.ods", TestSpreadsheets.zip("autre.xml", "<a/>")),
				"ods"))
			.isInstanceOf(SpreadsheetExtractor.UnreadableSpreadsheetException.class);
		assertThatThrownBy(() -> extractor.read(file("binaire.csv", new byte[] { 'a', 0, 'b' }), "csv"))
			.isInstanceOf(SpreadsheetExtractor.UnreadableSpreadsheetException.class);
	}

	@Test
	void neverReadsAnOdsFileWithAnExternalEntity() throws IOException {
		String content = """
				<?xml version="1.0"?>
				<!DOCTYPE x [<!ENTITY secret SYSTEM "file:///etc/passwd">]>
				<office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
				 xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0"
				 xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0"><table:table table:name="A">
				<table:table-row><table:table-cell office:value-type="string"><text:p>&secret;</text:p></table:table-cell>
				</table:table-row></table:table></office:document-content>""";

		Path ods = file("entite.ods", TestSpreadsheets.zip("content.xml", content));

		// Refused, or read without the entity: never the content of a local file.
		try {
			assertThat(extractor.read(ods, "ods").sheets())
				.allSatisfy(sheet -> assertThat(sheet.rows().toString()).doesNotContain("root:"));
		}
		catch (SpreadsheetExtractor.UnreadableSpreadsheetException ex) {
			assertThat(ex).hasMessage("Tableur ODS illisible");
		}
	}

	@Test
	void cutsASheetBeyondTheLimitsAndSaysSo() throws IOException {
		SpreadsheetExtractor small = new SpreadsheetExtractor(
				new SpreadsheetExtractor.Limits(1024 * 1024, 1024 * 1024, 10, 3, 50, 1000));

		Spreadsheet spreadsheet = small.read(file("dpgf.xlsx", TestSpreadsheets.xlsx()), "xlsx");

		assertThat(spreadsheet.sheets().getFirst().rows()).hasSize(3);
		assertThat(spreadsheet.sheets().getFirst().truncated()).isTrue();
		assertThat(spreadsheet.warnings()).anySatisfy(warning -> assertThat(warning).contains("DPGF Lot 1"));
		assertThat(SpreadsheetText.pages(spreadsheet).getFirst()).endsWith("(limite de lecture)");
	}

	@Test
	void refusesAFileLargerThanAllowed() throws IOException {
		SpreadsheetExtractor small = new SpreadsheetExtractor(
				new SpreadsheetExtractor.Limits(100, 1024 * 1024, 10, 100, 50, 1000));

		assertThatThrownBy(() -> small.read(file("dpgf.xlsx", TestSpreadsheets.xlsx()), "xlsx"))
			.isInstanceOf(SpreadsheetExtractor.TooLargeException.class);
	}

	@Test
	void writesEachRowWithItsCellsAndFormulasAndFindsThemBack() throws IOException {
		List<String> pages = SpreadsheetText.pages(extractor.read(file("dpgf.xlsx", TestSpreadsheets.xlsx()), "xlsx"));

		assertThat(pages).hasSize(2);
		assertThat(pages.getFirst()).startsWith("Feuille « DPGF Lot 1 », lignes 1 à 14\n")
			.contains("L4 | A4 \"1.1\" | B4 \"Décapage de terre végétale\" | C4 \"m2\" | D4 1200 | E4 2.5 | F4 3000 "
					+ "[formule D4*E4]")
			.contains("L14 | B14 \"Révision\" | F14 valeur non enregistrée [formule F9*0.02]")
			.contains("G13 date 2026-11-03");
		assertThat(SpreadsheetText.sheetName(pages.get(1))).contains("BPU");
		assertThat(SpreadsheetText.rowLine(pages.getFirst(), 5)).hasValueSatisfying(
				line -> assertThat(line).startsWith("L5 | A5 \"1.2\""));
		assertThat(SpreadsheetText.rowLine(pages.getFirst(), 2)).isEmpty();
	}

	@Test
	void writesTheValueOfACsvNumberAndHowItWasWritten() throws IOException {
		List<String> pages = SpreadsheetText.pages(
				extractor.read(file("dpgf.csv", TestSpreadsheets.csv().getBytes(StandardCharsets.UTF_8)), "csv"));

		assertThat(pages.getFirst()).contains("C2 1250.5 (écrit \"1 250,5\")", "A3 \"Grillage avertisseur / rouge\"");
	}

	@Test
	void readsNumbersWrittenTheFrenchWayOrNot() {
		assertThat(List.of("1 234,56 €", "1.234,56", "1234.56", "1,234.56", "1 234,56 € HT", "(12,50)", "-3",
				"1 500"))
			.extracting(FrenchNumbers::parse)
			.extracting(value -> value.stripTrailingZeros().toPlainString())
			.containsExactly("1234.56", "1234.56", "1234.56", "1234.56", "1234.56", "-12.5", "-3", "1500");
		assertThat(List.of("1.2.3", "PM", "inclus", "20 %", "03/11/2026", "", "A12"))
			.extracting(FrenchNumbers::parse)
			.allMatch(Objects::isNull);
		assertThat(FrenchNumbers.parse("12,5")).isEqualByComparingTo(new BigDecimal("12.5"));
	}

	@Test
	void namesTheColumnsAsTheSpreadsheetSoftwareDoes() {
		assertThat(List.of(0, 25, 26, 27, 701, 702)).extracting(Spreadsheet::columnName)
			.containsExactly("A", "Z", "AA", "AB", "ZZ", "AAA");
	}

	private static Cell cell(Sheet sheet, String ref) {
		return sheet.rows()
			.stream()
			.flatMap(row -> row.cells().stream())
			.filter(cell -> cell.ref().equals(ref))
			.findFirst()
			.orElse(null);
	}

	private Path file(String name, byte[] content) throws IOException {
		return Files.write(folder.resolve(name), content);
	}

}
