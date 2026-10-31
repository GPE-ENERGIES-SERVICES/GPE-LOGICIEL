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
		Instant validatedAt, List<Item> items) {

	public record Item(Long id, int position, String chapter, String reference, String designation, String unit,
			BigDecimal quantity, PriceMode priceMode, BigDecimal fixedUnitPrice, String textPrice, boolean option,
			BigDecimal unitCost, BigDecimal unitPrice, BigDecimal amount, Long sourceDocumentId, String sourceSheet,
			Integer sourceRow, boolean toCheck, List<Component> components) {

		static Item from(EstimateItem item) {
			return new Item(item.getId(), item.getPosition(), item.getChapter(), item.getReference(),
					item.getDesignation(), item.getUnit(), item.getQuantity(), item.getPriceMode(),
					item.getFixedUnitPrice(), item.getTextPrice(), item.isOption(), item.getUnitCost(),
					item.getUnitPrice(), item.getAmount(), item.getSourceDocumentId(), item.getSourceSheet(),
					item.getSourceRow(), item.isToCheck(),
					item.getComponents().stream().map(Component::from).toList());
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
				estimate.getValidatedBy(), estimate.getValidatedAt(),
				estimate.getItems().stream().map(Item::from).toList());
	}

}
