package eu.gpefrance.tenders.estimate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * An estimate with its items, their costs and every figure computed by the
 * application. The interface shows these figures and computes none.
 * @param totalCost cost of the items excluding options, null when a cost is
 * missing
 * @param margin total excluding tax minus total cost, null when the cost is
 * unknown
 * @param incompleteItems items without amount: a cost, a price or a quantity
 * is missing
 * @param itemsToCheck items a user still has to check (read from a PDF)
 */
public record EstimateResponse(Long id, Long tenderId, String lotNumber, int version, EstimateStatus status, String label,
		BigDecimal vatRate, Map<ComponentType, BigDecimal> coefficients, BigDecimal totalCost, BigDecimal totalHt,
		BigDecimal totalVat, BigDecimal totalTtc, BigDecimal optionsHt, BigDecimal margin, int incompleteItems,
		long itemsToCheck,
		String createdBy, Instant createdAt, String updatedBy, Instant updatedAt, String validatedBy,
		Instant validatedAt, Breakdown breakdown, List<Item> items) {

	/** How sure the price of an item is: GREEN from a rate or a supplier, ORANGE estimated, old or to check, RED none. */
	public enum PriceLevel {

		GREEN, ORANGE, RED

	}

	/** A cost older than this is to check again. */
	static final int FRESH_MONTHS = 6;

	/**
	 * The estimate by family, items excluding options: the items priced from
	 * their costs are split into supplies and labour, the others (price entered)
	 * only have a sale.
	 */
	public record Breakdown(BigDecimal supplyCost, BigDecimal supplySale, BigDecimal laborCost, BigDecimal laborSale,
			BigDecimal laborHours, BigDecimal undetailedSale) {

		static Breakdown of(Estimate estimate) {
			BigDecimal[] sums = new BigDecimal[6];
			java.util.Arrays.fill(sums, BigDecimal.ZERO);
			for (EstimateItem item : estimate.getItems()) {
				if (item.isOption() || item.getAmount() == null) {
					continue;
				}
				EstimateCalculator.Split split = EstimateCalculator.split(item.line(), estimate.getCoefficients());
				BigDecimal quantity = item.getQuantity();
				boolean detailed = item.getPriceMode() == PriceMode.COMPOSED && quantity != null;
				if (!detailed) {
					sums[5] = sums[5].add(item.getAmount());
					continue;
				}
				if (split.supplyAmount() != null) {
					sums[0] = sums[0].add(split.supplyUnitCost().multiply(quantity));
					sums[1] = sums[1].add(split.supplyAmount());
				}
				if (split.laborAmount() != null) {
					sums[2] = sums[2].add(split.laborRate().multiply(split.laborHours()).multiply(quantity));
					sums[3] = sums[3].add(split.laborAmount());
					sums[4] = sums[4].add(split.laborHours().multiply(quantity));
				}
			}
			return new Breakdown(EstimateCalculator.cents(sums[0]), sums[1], EstimateCalculator.cents(sums[2]),
					sums[3], EstimateCalculator.cents(sums[4]), sums[5]);
		}

	}

	public record Item(Long id, int position, String chapter, String reference, String designation, String unit,
			BigDecimal quantity, PriceMode priceMode, BigDecimal fixedUnitPrice, String textPrice, boolean option,
			BigDecimal unitCost, BigDecimal unitPrice, BigDecimal amount, Long sourceDocumentId, String sourceSheet,
			Integer sourceRow, boolean toCheck, List<Component> components, TechnicalSolution technical,
			BigDecimal supplyUnitCost, BigDecimal supplyCoefficient, BigDecimal supplyAmount, BigDecimal laborHours,
			BigDecimal laborRate, BigDecimal laborCoefficient, BigDecimal laborAmount, PriceLevel priceLevel) {

		static Item from(EstimateItem item) {
			EstimateCalculator.Split split = EstimateCalculator.split(item.line(),
					item.getEstimate().getCoefficients());
			return new Item(item.getId(), item.getPosition(), item.getChapter(), item.getReference(),
					item.getDesignation(), item.getUnit(), item.getQuantity(), item.getPriceMode(),
					item.getFixedUnitPrice(), item.getTextPrice(), item.isOption(), item.getUnitCost(),
					item.getUnitPrice(), item.getAmount(), item.getSourceDocumentId(), item.getSourceSheet(),
					item.getSourceRow(), item.isToCheck(),
					item.getComponents().stream().map(Component::from).toList(), item.technical(),
					split.supplyUnitCost(), split.supplyCoefficient(), split.supplyAmount(), split.laborHours(),
					split.laborRate(), split.laborCoefficient(), split.laborAmount(), level(item));
		}

		static PriceLevel level(EstimateItem item) {
			if (item.getPriceMode() != PriceMode.TEXT && item.getAmount() == null) {
				return PriceLevel.RED;
			}
			// shortcut: the date of the server, not the Clock of the application; enough for months.
			LocalDate stale = LocalDate.now().minusMonths(FRESH_MONTHS);
			boolean old = item.getComponents()
				.stream()
				.anyMatch(component -> component.getCostDate() != null && component.getCostDate().isBefore(stale));
			return item.isToCheck() || old ? PriceLevel.ORANGE : PriceLevel.GREEN;
		}

	}

	public record Component(Long id, int position, ComponentType type, String label, BigDecimal quantity,
			String unit, BigDecimal unitCost, BigDecimal wasteRate, BigDecimal coefficient, String supplierName,
			String supplierRef, String manufacturerRef, LocalDate costDate, String rateCode, Long offerId) {

		static Component from(EstimateComponent component) {
			return new Component(component.getId(), component.getPosition(), component.getType(),
					component.getLabel(), component.getQuantity(), component.getUnit(), component.getUnitCost(),
					component.getWasteRate(), component.getCoefficient(), component.getSupplierName(),
					component.getSupplierRef(), component.getManufacturerRef(), component.getCostDate(),
					component.getRateCode(), component.getOfferId());
		}

	}

	/** The estimates of a tender, without their items. */
	public record Summary(Long id, String lotNumber, int version, EstimateStatus status, String label, BigDecimal totalHt,
			BigDecimal totalTtc, int incompleteItems, String updatedBy, Instant updatedAt) {

		static Summary from(Estimate estimate) {
			return new Summary(estimate.getId(), estimate.getLotNumber(), estimate.getVersion(), estimate.getStatus(),
					estimate.getLabel(), estimate.getTotalHt(), estimate.getTotalTtc(), estimate.getIncompleteItems(),
					estimate.getUpdatedBy(), estimate.getUpdatedAt());
		}

	}

	static EstimateResponse from(Estimate estimate) {
		BigDecimal margin = estimate.getTotalCost() == null ? null
				: estimate.getTotalHt().subtract(estimate.getTotalCost());
		return new EstimateResponse(estimate.getId(), estimate.getTenderId(), estimate.getLotNumber(),
				estimate.getVersion(), estimate.getStatus(), estimate.getLabel(), estimate.getVatRate(),
				estimate.getCoefficients(), estimate.getTotalCost(), estimate.getTotalHt(), estimate.getTotalVat(),
				estimate.getTotalTtc(), estimate.getOptionsHt(), margin, estimate.getIncompleteItems(),
				estimate.getItems().stream().filter(EstimateItem::isToCheck).count(),
				estimate.getCreatedBy(), estimate.getCreatedAt(), estimate.getUpdatedBy(), estimate.getUpdatedAt(),
				estimate.getValidatedBy(), estimate.getValidatedAt(), Breakdown.of(estimate),
				estimate.getItems().stream().map(Item::from).toList());
	}

}
