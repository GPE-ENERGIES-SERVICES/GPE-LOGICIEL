package eu.gpefrance.tenders.estimate;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import eu.gpefrance.tenders.estimate.EstimateCalculator.Cost;
import eu.gpefrance.tenders.estimate.EstimateCalculator.Line;
import eu.gpefrance.tenders.estimate.EstimateCalculator.LineResult;
import eu.gpefrance.tenders.estimate.EstimateCalculator.Result;

/**
 * The figures of an estimate, computed by hand for each case.
 */
class EstimateCalculatorTests {

	private static final Map<ComponentType, BigDecimal> K = Map.of(ComponentType.SUPPLY, d("1.30"),
			ComponentType.LABOR, d("1.50"));

	private static final BigDecimal VAT = d("0.20");

	/** Prise 16 A : 1 u à 3,20 € et 0,25 h à 45 €. */
	private static final List<Cost> SOCKET = List.of(new Cost(ComponentType.SUPPLY, d("1"), d("3.20"), null, null),
			new Cost(ComponentType.LABOR, d("0.25"), d("45"), null, null));

	@Test
	void composesTheUnitPriceFromTheCostsAndTheCoefficients() {
		LineResult line = EstimateCalculator.line(new Line(PriceMode.COMPOSED, d("42"), null, false, SOCKET), K);

		// cost 3.20 + 11.25; sale 4.16 + 16.875 = 21.035, half up
		assertThat(line.unitCost()).isEqualByComparingTo("14.45");
		assertThat(line.unitPrice()).isEqualTo(d("21.04"));
		assertThat(line.amount()).isEqualTo(d("883.68"));
		assertThat(line.complete()).isTrue();
	}

	@Test
	void appliesTheWasteAndTheCoefficientOfAComponent() {
		// 0.8333 x 1.05 = 0.874965 ; x 1.10 = 0.9624615
		LineResult line = EstimateCalculator.line(new Line(PriceMode.COMPOSED, d("100"), null, false,
				List.of(new Cost(ComponentType.SUPPLY, d("1"), d("0.8333"), d("0.05"), d("1.10")))), K);

		assertThat(line.unitCost()).isEqualTo(d("0.8750"));
		assertThat(line.unitPrice()).isEqualTo(d("0.96"));
		assertThat(line.amount()).isEqualTo(d("96.00"));
	}

	@Test
	void aTypeWithoutCoefficientSellsAtCost() {
		LineResult line = EstimateCalculator.line(new Line(PriceMode.COMPOSED, d("1"), null, false,
				List.of(new Cost(ComponentType.RENTAL, d("2"), d("80"), null, null))), K);

		assertThat(line.unitPrice()).isEqualTo(d("160.00"));
	}

	@Test
	void leavesAnItemWithoutPriceWhenACostIsMissing() {
		LineResult missing = EstimateCalculator.line(new Line(PriceMode.COMPOSED, d("3"), null, false,
				List.of(SOCKET.get(0), new Cost(ComponentType.LABOR, d("1"), null, null, null))), K);
		LineResult empty = EstimateCalculator.line(new Line(PriceMode.COMPOSED, d("3"), null, false, List.of()), K);

		assertThat(missing.unitCost()).isNull();
		assertThat(missing.unitPrice()).isNull();
		assertThat(missing.amount()).isNull();
		assertThat(missing.complete()).isFalse();
		assertThat(empty.unitPrice()).isNull();
		assertThat(empty.complete()).isFalse();
	}

	@Test
	void aScheduleOfUnitPricesHasPricesWithoutAmounts() {
		LineResult line = EstimateCalculator.line(new Line(PriceMode.COMPOSED, null, null, false, SOCKET), K);

		assertThat(line.unitPrice()).isEqualTo(d("21.04"));
		assertThat(line.amount()).isNull();
		assertThat(line.complete()).isFalse();
	}

	@Test
	void keepsAFixedPriceAndItsCostForTheMargin() {
		LineResult line = EstimateCalculator.line(new Line(PriceMode.FIXED, d("2"), d("1500.00"), false, SOCKET), K);

		assertThat(line.unitPrice()).isEqualTo(d("1500.00"));
		assertThat(line.unitCost()).isEqualByComparingTo("14.45");
		assertThat(line.amount()).isEqualTo(d("3000.00"));
	}

	@Test
	void aPriceWrittenAsWordsIsWorthZero() {
		LineResult line = EstimateCalculator.line(new Line(PriceMode.TEXT, d("1"), null, false, List.of()), K);

		assertThat(line.unitPrice()).isNull();
		assertThat(line.amount()).isEqualTo(d("0.00"));
		assertThat(line.complete()).isTrue();
	}

	@Test
	void totalsWithVatOnTheTotalAndOptionsApart() {
		Result result = EstimateCalculator.compute(List.of(
				new Line(PriceMode.COMPOSED, d("42"), null, false, SOCKET),
				new Line(PriceMode.COMPOSED, d("1"), null, false,
						List.of(new Cost(ComponentType.SUPPLY, d("1"), d("0.10"), null, d("1.0417")))),
				new Line(PriceMode.TEXT, d("1"), null, false, List.of()),
				new Line(PriceMode.COMPOSED, d("10"), null, true, SOCKET)), K, VAT);

		// 883.68 + 0.10 (0.10417 rounded); options 210.40 kept apart
		assertThat(result.totalHt()).isEqualTo(d("883.78"));
		assertThat(result.optionsHt()).isEqualTo(d("210.40"));
		// 176.756 half up, on the total: not the sum of rounded line taxes
		assertThat(result.totalVat()).isEqualTo(d("176.76"));
		assertThat(result.totalTtc()).isEqualTo(d("1060.54"));
		// 14.45 x 42 + 0.10, options excluded
		assertThat(result.totalCost()).isEqualTo(d("607.00"));
		assertThat(result.incompleteItems()).isZero();
	}

	@Test
	void theTotalCostIsUnknownWhenAFixedPriceHasNoCost() {
		Result result = EstimateCalculator.compute(List.of(new Line(PriceMode.COMPOSED, d("42"), null, false, SOCKET),
				new Line(PriceMode.FIXED, d("1"), d("500"), false, List.of())), K, VAT);

		assertThat(result.totalHt()).isEqualTo(d("1383.68"));
		assertThat(result.totalCost()).isNull();
	}

	@Test
	void countsTheIncompleteItemsAndLeavesThemOutOfTheTotal() {
		Result result = EstimateCalculator.compute(List.of(new Line(PriceMode.COMPOSED, d("42"), null, false, SOCKET),
				new Line(PriceMode.COMPOSED, d("5"), null, false, List.of()),
				new Line(PriceMode.COMPOSED, null, null, true, SOCKET)), K, VAT);

		assertThat(result.totalHt()).isEqualTo(d("883.68"));
		assertThat(result.incompleteItems()).isEqualTo(2);
		assertThat(result.totalCost()).isNull();
	}

	@Test
	void anEmptyEstimateIsWorthZero() {
		Result result = EstimateCalculator.compute(List.of(), K, VAT);

		assertThat(result.totalHt()).isEqualTo(d("0.00"));
		assertThat(result.totalTtc()).isEqualTo(d("0.00"));
		assertThat(result.totalCost()).isEqualTo(d("0.00"));
	}

	private static BigDecimal d(String value) {
		return new BigDecimal(value);
	}

}
