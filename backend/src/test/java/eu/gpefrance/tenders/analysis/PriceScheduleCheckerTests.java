package eu.gpefrance.tenders.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import eu.gpefrance.tenders.analysis.PriceCheck.Column;
import eu.gpefrance.tenders.analysis.PriceCheck.Issue;
import eu.gpefrance.tenders.analysis.PriceCheck.Role;
import eu.gpefrance.tenders.analysis.PriceCheck.Total;
import eu.gpefrance.tenders.document.Spreadsheet;
import eu.gpefrance.tenders.document.Spreadsheet.Cell;
import eu.gpefrance.tenders.document.Spreadsheet.CellType;
import eu.gpefrance.tenders.document.Spreadsheet.Row;
import eu.gpefrance.tenders.document.Spreadsheet.Sheet;
import eu.gpefrance.tenders.document.SpreadsheetExtractor;
import eu.gpefrance.tenders.document.TestSpreadsheets;

class PriceScheduleCheckerTests {

	@TempDir
	private Path folder;

	@Test
	void comparesEachLineAndEachTotalWithWhatItAddsUpTo() throws IOException {
		List<PriceCheck> checks = PriceScheduleChecker.check(7L, "DPGF.xlsx", read("DPGF.xlsx", TestSpreadsheets.xlsx()));

		PriceCheck dpgf = checks.getFirst();
		assertThat(dpgf.sheet()).isEqualTo("DPGF Lot 1");
		assertThat(dpgf.headerRow()).isEqualTo(3);
		assertThat(dpgf.columns()).extracting(Column::role, Column::ref)
			.contains(tuple(Role.REFERENCE, "A3"), tuple(Role.DESIGNATION, "B3"), tuple(Role.UNIT, "C3"),
					tuple(Role.QUANTITY, "D3"), tuple(Role.UNIT_PRICE, "E3"), tuple(Role.AMOUNT, "F3"));
		assertThat(dpgf.amountBasis()).isEqualTo("HT");
		// Three lines priced, and the revision whose formula has no saved value.
		assertThat(dpgf.items()).isEqualTo(4);
		assertThat(dpgf.withAmount()).isEqualTo(3);
		assertThat(dpgf.computedTotal()).isEqualByComparingTo("13740");
		assertThat(dpgf.blankPrices()).isFalse();
		assertThat(dpgf.issues()).extracting(Issue::kind, Issue::ref)
			.containsExactly(tuple("LINE_MISMATCH", "F6"), tuple("NOT_SAVED", "F14"));
		assertThat(dpgf.issues().getFirst().message()).isEqualTo("Quantité × prix unitaire = 4200,00, montant écrit 4300,00");
		assertThat(dpgf.totals()).extracting(Total::kind, Total::ref, Total::consistent)
			.containsExactly(tuple("HT", "F7", true), tuple("HT", "F9", true), tuple("VAT", "F10", null),
					tuple("TTC", "F11", true));
		assertThat(dpgf.totals().getLast().computed()).isEqualByComparingTo("16488");
	}

	@Test
	void recognisesOtherColumnNamesAndASheduleToBeFilledIn() throws IOException {
		PriceCheck bpu = PriceScheduleChecker.check(7L, "DPGF.xlsx", read("DPGF.xlsx", TestSpreadsheets.xlsx())).get(1);

		assertThat(bpu.columns()).extracting(Column::role)
			.containsExactlyInAnyOrder(Role.REFERENCE, Role.DESIGNATION, Role.UNIT, Role.UNIT_PRICE);
		assertThat(bpu.items()).isEqualTo(2);
		assertThat(bpu.withUnitPrice()).isZero();
		assertThat(bpu.textPrices()).isEqualTo(1);
		assertThat(bpu.blankPrices()).isTrue();
		assertThat(bpu.computedTotal()).isNull();
	}

	@Test
	void checksACsvScheduleWrittenWithDecimalCommasAndAnOdsOne() throws IOException {
		PriceCheck csv = PriceScheduleChecker
			.check(1L, "DPGF.csv", read("DPGF.csv", TestSpreadsheets.csv().getBytes(StandardCharsets.UTF_8)))
			.getFirst();
		PriceCheck ods = PriceScheduleChecker.check(2L, "DQE.ods", read("DQE.ods", TestSpreadsheets.ods())).getFirst();

		assertThat(csv.items()).isEqualTo(3);
		assertThat(csv.computedTotal()).isEqualByComparingTo("16186.20");
		assertThat(csv.totals()).extracting(Total::consistent).containsExactly(true);
		assertThat(csv.issues()).isEmpty();
		assertThat(ods.items()).isEqualTo(1);
		assertThat(ods.computedTotal()).isEqualByComparingTo("1402");
		assertThat(ods.issues()).isEmpty();
	}

	@Test
	void reportsATotalThatDoesNotAddUpAndAVatThatDoesNotMatch() {
		Sheet sheet = sheet(row(1, text("A1", "Désignation"), text("B1", "Quantité"), text("C1", "PU"),
				text("D1", "Montant")), row(2, text("A2", "Béton"), number("B2", "10"), number("C2", "100"),
						number("D2", "1000")),
				row(3, text("A3", "Acier"), number("B3", "2"), text("C3", "1 250,50"), number("D3", "2501")),
				row(4, text("A4", "Total HT"), number("D4", "3600")), row(5, text("A5", "TVA"), number("D5", "720")),
				row(6, text("A6", "Montant TTC"), number("D6", "4300")),
				row(7, text("A7", "Coffrage"), number("B7", "1"), error("C7", "#DIV/0!")));

		PriceCheck check = PriceScheduleChecker.check(1L, "DPGF.xlsx", sheet);

		assertThat(check.computedTotal()).isEqualByComparingTo("3501");
		assertThat(check.issues()).extracting(Issue::kind, Issue::ref)
			.containsExactly(tuple("TOTAL_MISMATCH", "D4"), tuple("VAT_MISMATCH", "D6"), tuple("ERROR_CELL", "C7"));
		assertThat(check.issues().getFirst().message()).isEqualTo("« Total HT » : 3600,00 écrit, 3501,00 calculé");
	}

	@Test
	void recognisesAScheduleToBeFilledInWhoseAmountsAreFormulasWorthZero() {
		Sheet sheet = sheet(row(1, text("A1", "Désignation"), text("B1", "Qté"), text("C1", "PU €"), text("D1", "TOTAL €")),
				row(2, text("A2", "Dépose couverture"), number("B2", "320"), formula("D2", "0", "B2*C2")),
				row(3, text("A3", "Couverture bac acier"), number("B3", "320"), formula("D3", "0", "B3*C3")));

		PriceCheck check = PriceScheduleChecker.check(1L, "DPGF.xlsx", sheet);

		assertThat(check.withAmount()).isEqualTo(2);
		assertThat(check.blankPrices()).isTrue();
		assertThat(check.issues()).isEmpty();
	}

	@Test
	void saysWhenNoHeaderIsRecognisedInsteadOfGuessing() {
		Sheet sheet = sheet(row(1, text("A1", "Planning prévisionnel")), row(2, text("A2", "Semaine 12"),
				number("B2", "3")));

		PriceCheck check = PriceScheduleChecker.check(1L, "planning.xlsx", sheet);

		assertThat(check.headerRow()).isNull();
		assertThat(check.items()).isZero();
		assertThat(check.issues()).extracting(Issue::kind).containsExactly("NO_HEADER");
	}

	@Test
	void comparesTotalsToTheCentAndLinesWithRoundedUnitPrices() {
		assertThat(PriceScheduleChecker.close(new BigDecimal("100.00"), new BigDecimal("100.01"))).isTrue();
		assertThat(PriceScheduleChecker.close(new BigDecimal("100.00"), new BigDecimal("100.05"))).isFalse();
		assertThat(PriceScheduleChecker.close(new BigDecimal("250000.00"), new BigDecimal("250002.00"))).isTrue();
		assertThat(PriceScheduleChecker.close(new BigDecimal("250000.00"), new BigDecimal("250100.00"))).isFalse();
		assertThat(PriceScheduleChecker.closeLine(new BigDecimal("1000.00"), new BigDecimal("1000.40"),
				new BigDecimal("100"))).isTrue();
		assertThat(PriceScheduleChecker.closeLine(new BigDecimal("4200.00"), new BigDecimal("4300.00"),
				new BigDecimal("350"))).isFalse();
	}

	private Spreadsheet read(String name, byte[] content) throws IOException {
		Path file = Files.write(folder.resolve(name), content);
		return new SpreadsheetExtractor().read(file, name.substring(name.lastIndexOf('.') + 1).toLowerCase());
	}

	private static Sheet sheet(Row... rows) {
		return new Sheet("Feuille 1", List.of(rows), false);
	}

	private static Row row(int number, Cell... cells) {
		return new Row(number, List.of(cells));
	}

	private static Cell text(String ref, String text) {
		return new Cell(ref, PriceScheduleChecker.columnIndex(ref), CellType.TEXT, text, null, null, false);
	}

	private static Cell number(String ref, String value) {
		return new Cell(ref, PriceScheduleChecker.columnIndex(ref), CellType.NUMBER, null, new BigDecimal(value), null,
				false);
	}

	private static Cell formula(String ref, String value, String formula) {
		return new Cell(ref, PriceScheduleChecker.columnIndex(ref), CellType.NUMBER, null, new BigDecimal(value), formula,
				false);
	}

	private static Cell error(String ref, String text) {
		return new Cell(ref, PriceScheduleChecker.columnIndex(ref), CellType.ERROR, text, null, "1/0", false);
	}

}
