package eu.gpefrance.tenders.estimate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * The figures of an estimate, by plain code: no AI, nothing estimated. The
 * costs of an item are those of one of its units.
 * <ul>
 * <li>cost of a component = quantity x unit cost x (1 + waste), 4 decimals</li>
 * <li>sale of a component = its cost x its coefficient, or the coefficient of
 * the estimate for its type</li>
 * <li>unit price of an item = sum of the sales rounded to the cent, or the
 * price entered (FIXED), or none (TEXT: PM, inclus...)</li>
 * <li>amount = unit price x quantity, rounded to the cent</li>
 * <li>VAT computed on the total excluding tax, not line by line; options are
 * totalled apart</li>
 * </ul>
 * Rounding is half up. A value missing is never replaced: the item stays
 * without price and is counted as incomplete.
 */
final class EstimateCalculator {

	private static final int COST_SCALE = 4;

	private EstimateCalculator() {
	}

	record Cost(ComponentType type, BigDecimal quantity, BigDecimal unitCost, BigDecimal wasteRate,
			BigDecimal coefficient) {
	}

	record Line(PriceMode mode, BigDecimal quantity, BigDecimal fixedUnitPrice, boolean option, List<Cost> costs) {
	}

	/**
	 * @param unitCost cost of one unit, null when a cost is missing or the item
	 * has none
	 * @param complete whether its amount is known (TEXT items always are: 0)
	 */
	record LineResult(BigDecimal unitCost, BigDecimal unitPrice, BigDecimal amount, boolean complete) {
	}

	/**
	 * @param totalCost cost of the items excluding options, null when one is
	 * unknown
	 */
	record Result(List<LineResult> lines, BigDecimal totalCost, BigDecimal totalHt, BigDecimal totalVat,
			BigDecimal totalTtc, BigDecimal optionsHt, int incompleteItems) {
	}

	static Result compute(List<Line> lines, Map<ComponentType, BigDecimal> coefficients, BigDecimal vatRate) {
		BigDecimal totalHt = BigDecimal.ZERO;
		BigDecimal optionsHt = BigDecimal.ZERO;
		BigDecimal totalCost = BigDecimal.ZERO;
		boolean costKnown = true;
		int incomplete = 0;
		List<LineResult> results = lines.stream().map(line -> line(line, coefficients)).toList();
		for (int i = 0; i < lines.size(); i++) {
			Line line = lines.get(i);
			LineResult result = results.get(i);
			if (!result.complete()) {
				incomplete++;
			}
			BigDecimal amount = result.amount() == null ? BigDecimal.ZERO : result.amount();
			if (line.option()) {
				optionsHt = optionsHt.add(amount);
				continue;
			}
			totalHt = totalHt.add(amount);
			if (line.mode() == PriceMode.FIXED && line.costs().isEmpty()) {
				costKnown = false;
			}
			else if (!line.costs().isEmpty() || line.mode() == PriceMode.COMPOSED) {
				if (result.unitCost() == null || line.quantity() == null) {
					costKnown = false;
				}
				else {
					totalCost = totalCost.add(result.unitCost().multiply(line.quantity()));
				}
			}
		}
		totalHt = cents(totalHt);
		BigDecimal vat = cents(totalHt.multiply(vatRate));
		return new Result(results, costKnown ? cents(totalCost) : null, totalHt, vat, totalHt.add(vat),
				cents(optionsHt), incomplete);
	}

	static LineResult line(Line line, Map<ComponentType, BigDecimal> coefficients) {
		BigDecimal cost = BigDecimal.ZERO;
		BigDecimal sale = BigDecimal.ZERO;
		boolean costsKnown = !line.costs().isEmpty();
		for (Cost component : line.costs()) {
			if (component.unitCost() == null) {
				costsKnown = false;
				continue;
			}
			BigDecimal waste = component.wasteRate() == null ? BigDecimal.ZERO : component.wasteRate();
			BigDecimal value = component.quantity().multiply(component.unitCost()).multiply(BigDecimal.ONE.add(waste));
			BigDecimal coefficient = component.coefficient() != null ? component.coefficient()
					: coefficients.getOrDefault(component.type(), BigDecimal.ONE);
			cost = cost.add(value);
			sale = sale.add(value.multiply(coefficient));
		}
		BigDecimal unitCost = costsKnown ? cost.setScale(COST_SCALE, RoundingMode.HALF_UP) : null;
		BigDecimal unitPrice = switch (line.mode()) {
			case COMPOSED -> costsKnown ? cents(sale) : null;
			case FIXED -> line.fixedUnitPrice();
			case TEXT -> null;
		};
		if (line.mode() == PriceMode.TEXT) {
			return new LineResult(unitCost, null, BigDecimal.ZERO.setScale(2), true);
		}
		BigDecimal amount = unitPrice == null || line.quantity() == null ? null
				: cents(unitPrice.multiply(line.quantity()));
		return new LineResult(unitCost, unitPrice, amount, amount != null);
	}

	static BigDecimal cents(BigDecimal value) {
		return value.setScale(2, RoundingMode.HALF_UP);
	}

}
