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

		/** Cost of one unit of the item: quantity x unit cost x (1 + waste); null when the unit cost is missing. */
		BigDecimal value() {
			if (unitCost == null) {
				return null;
			}
			BigDecimal waste = wasteRate == null ? BigDecimal.ZERO : wasteRate;
			return quantity.multiply(unitCost).multiply(BigDecimal.ONE.add(waste));
		}

		/** Its own coefficient, or that of the estimate for its type. */
		BigDecimal coefficient(Map<ComponentType, BigDecimal> coefficients) {
			return coefficient != null ? coefficient : coefficients.getOrDefault(type, BigDecimal.ONE);
		}

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
			BigDecimal value = component.value();
			if (value == null) {
				costsKnown = false;
				continue;
			}
			cost = cost.add(value);
			sale = sale.add(value.multiply(component.coefficient(coefficients)));
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

	/**
	 * An item as a costing reader reads it: the supplies (every cost but labour)
	 * and the labour, for one unit, then for the quantity. A value is null when
	 * the item has no such cost or one of them is missing.
	 */
	record Split(BigDecimal supplyUnitCost, BigDecimal supplyCoefficient, BigDecimal supplyAmount,
			BigDecimal laborHours, BigDecimal laborRate, BigDecimal laborCoefficient, BigDecimal laborAmount) {
	}

	static Split split(Line line, Map<ComponentType, BigDecimal> coefficients) {
		Family supply = new Family();
		Family labor = new Family();
		for (Cost cost : line.costs()) {
			Family family = cost.type() == ComponentType.LABOR ? labor : supply;
			family.add(cost, coefficients);
		}
		BigDecimal hours = labor.present ? labor.hours.setScale(COST_SCALE, RoundingMode.HALF_UP) : null;
		BigDecimal rate = labor.known() && labor.hours.signum() > 0
				? labor.cost.divide(labor.hours, COST_SCALE, RoundingMode.HALF_UP) : null;
		return new Split(supply.known() ? supply.cost.setScale(COST_SCALE, RoundingMode.HALF_UP) : null,
				supply.coefficient(), supply.amount(line.quantity()), hours, rate, labor.coefficient(),
				labor.amount(line.quantity()));
	}

	/** The supplies or the labour of one unit of an item. */
	private static final class Family {

		boolean present;

		boolean missing;

		BigDecimal cost = BigDecimal.ZERO;

		BigDecimal sale = BigDecimal.ZERO;

		BigDecimal hours = BigDecimal.ZERO;

		void add(Cost component, Map<ComponentType, BigDecimal> coefficients) {
			present = true;
			BigDecimal waste = component.wasteRate() == null ? BigDecimal.ZERO : component.wasteRate();
			hours = hours.add(component.quantity().multiply(BigDecimal.ONE.add(waste)));
			BigDecimal value = component.value();
			if (value == null) {
				missing = true;
				return;
			}
			cost = cost.add(value);
			sale = sale.add(value.multiply(component.coefficient(coefficients)));
		}

		boolean known() {
			return present && !missing;
		}

		/** The sale over the cost: the coefficient of the family, whatever those of its costs. */
		BigDecimal coefficient() {
			return known() && cost.signum() > 0 ? sale.divide(cost, 5, RoundingMode.HALF_UP) : null;
		}

		BigDecimal amount(BigDecimal quantity) {
			return known() && quantity != null ? cents(sale.multiply(quantity)) : null;
		}

	}

	/**
	 * The coefficient that brings the gross margin of the detailed items to a
	 * target, set on the costs of the families chosen; the others keep theirs.
	 * The gross margin is (sale - cost) / sale. Only the items priced from
	 * their costs (COMPOSED, not options, every cost known) are counted.
	 * @param labor whether the labour gets the coefficient
	 * @param supplies whether the supplies (every cost but labour) get it
	 * @return the coefficient, 5 decimals
	 * @throws IllegalArgumentException when no cost of the families chosen is
	 * known, or the target cannot be reached with a positive coefficient
	 */
	static BigDecimal coefficientForMargin(List<Line> lines, Map<ComponentType, BigDecimal> coefficients,
			BigDecimal target, boolean supplies, boolean labor) {
		if (target.signum() < 0 || target.compareTo(BigDecimal.ONE) >= 0) {
			throw new IllegalArgumentException("La marge doit être comprise entre 0 et 100 %.");
		}
		BigDecimal totalCost = BigDecimal.ZERO;
		BigDecimal chosenCost = BigDecimal.ZERO;
		BigDecimal otherSale = BigDecimal.ZERO;
		for (Line line : lines) {
			if (line.option() || line.mode() != PriceMode.COMPOSED || line.quantity() == null
					|| line.costs().isEmpty() || line.costs().stream().anyMatch(cost -> cost.value() == null)) {
				continue;
			}
			for (Cost cost : line.costs()) {
				BigDecimal value = cost.value().multiply(line.quantity());
				totalCost = totalCost.add(value);
				boolean chosen = cost.type() == ComponentType.LABOR ? labor : supplies;
				if (chosen) {
					chosenCost = chosenCost.add(value);
				}
				else {
					otherSale = otherSale.add(value.multiply(cost.coefficient(coefficients)));
				}
			}
		}
		if (chosenCost.signum() <= 0) {
			throw new IllegalArgumentException("Aucun coût détaillé à ajuster : renseignez le détail des postes.");
		}
		BigDecimal sale = totalCost.divide(BigDecimal.ONE.subtract(target), 10, RoundingMode.HALF_UP);
		BigDecimal coefficient = sale.subtract(otherSale).divide(chosenCost, 5, RoundingMode.HALF_UP);
		if (coefficient.signum() <= 0) {
			throw new IllegalArgumentException("Marge impossible à atteindre en ne changeant que ce coefficient.");
		}
		return coefficient;
	}

	static BigDecimal cents(BigDecimal value) {
		return value.setScale(2, RoundingMode.HALF_UP);
	}

}
