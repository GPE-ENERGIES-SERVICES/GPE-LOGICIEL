package eu.gpefrance.tenders.estimate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import eu.gpefrance.tenders.supplier.SupplierCatalog;
import eu.gpefrance.tenders.supplier.SupplierConnector.Availability;
import eu.gpefrance.tenders.supplier.SupplierConnector.Place;
import eu.gpefrance.tenders.supplier.SupplierPricingService;
import eu.gpefrance.tenders.supplier.SupplierPricingService.OfferAvailability;
import eu.gpefrance.tenders.supplier.SupplierPricingService.RefreshStatus;
import eu.gpefrance.tenders.supplier.SupplierPricingService.RefreshedOffer;

/**
 * The prices of the suppliers for the costs of a draft: first asked and shown
 * beside the costs copied, which stay as they are, then copied at the request
 * of a user. No call to a supplier is made while the estimate is locked.
 */
@Service
public class EstimateSupplierPriceService {

	private static final int MAX_QUANTITY = 9_999_999;

	private static final Place ANYWHERE = new Place(null, null, null);

	private final EstimateService estimates;

	private final SupplierPricingService pricing;

	private final SupplierCatalog catalog;

	private final TransactionTemplate transaction;

	public EstimateSupplierPriceService(EstimateService estimates, SupplierPricingService pricing,
			SupplierCatalog catalog, TransactionTemplate transaction) {
		this.estimates = estimates;
		this.pricing = pricing;
		this.catalog = catalog;
		this.transaction = transaction;
	}

	/**
	 * A cost of the draft against the price of its offer.
	 * @param quantityToBuy for the whole estimate, in units sold, costs on the same offer added up
	 * @param newCost the price read from the supplier for one unit, null when it gave none
	 */
	public record Line(Long componentId, String itemReference, String itemDesignation, String label, Long offerId,
			int quantityToBuy, BigDecimal currentCost, BigDecimal newCost, RefreshStatus status, String priceLabel,
			String priceSource, Availability availability) {
	}

	private record Cost(Long componentId, String itemReference, String itemDesignation, String label, Long offerId,
			BigDecimal currentCost, BigDecimal needed) {
	}

	public List<Line> check(Long estimateId, String user) {
		List<Cost> costs = transaction.execute(status -> costs(estimateId));
		if (costs.isEmpty()) {
			return List.of();
		}
		Map<Long, BigDecimal> needed = costs.stream()
			.collect(Collectors.toMap(Cost::offerId, Cost::needed, BigDecimal::add));
		Map<Long, Integer> quantities = new HashMap<>();
		needed.forEach((offerId, quantity) -> catalog.find(offerId)
			.ifPresent(offer -> quantities.put(offerId, quantityToBuy(quantity, offer.packQuantity()))));
		Map<Long, RefreshedOffer> refreshed = pricing.refresh(quantities, ANYWHERE, user)
			.stream()
			.collect(Collectors.toMap(RefreshedOffer::offerId, Function.identity()));
		Map<Long, Availability> stocks = new HashMap<>();
		for (OfferAvailability stock : pricing.availability(quantities, ANYWHERE)) {
			stocks.put(stock.offerId(), stock.availability());
		}
		List<Line> lines = new ArrayList<>();
		for (Cost cost : costs) {
			RefreshedOffer offer = refreshed.get(cost.offerId());
			boolean read = offer != null && offer.offer() != null
					&& (offer.status() == RefreshStatus.UPDATED || offer.status() == RefreshStatus.FRESH);
			lines.add(new Line(cost.componentId(), cost.itemReference(), cost.itemDesignation(), cost.label(),
					cost.offerId(), quantities.getOrDefault(cost.offerId(), 0), cost.currentCost(),
					read ? offer.offer().unitCost() : null, offer == null ? RefreshStatus.NOT_CONNECTED : offer.status(),
					offer == null ? null : offer.priceLabel(), read ? offer.offer().priceSource() : null,
					stocks.get(cost.offerId())));
		}
		return lines;
	}

	/** Rounded up: a supplier sells whole units. */
	static int quantityToBuy(BigDecimal needed, BigDecimal packQuantity) {
		BigDecimal units = needed.divide(packQuantity, 0, RoundingMode.CEILING);
		return units.compareTo(BigDecimal.valueOf(MAX_QUANTITY)) > 0 ? MAX_QUANTITY : Math.max(1, units.intValue());
	}

	private List<Cost> costs(Long estimateId) {
		Estimate estimate = estimates.found(estimateId);
		if (!estimate.isDraft()) {
			throw EstimateRuleException.conflict("Ce chiffrage est figé : créez-en une nouvelle version pour le modifier.");
		}
		List<Cost> costs = new ArrayList<>();
		for (EstimateItem item : estimate.getItems()) {
			// An item without quantity yet: at least one of its unit is asked for.
			BigDecimal itemQuantity = item.getQuantity() == null ? BigDecimal.ONE : item.getQuantity();
			for (EstimateComponent component : item.getComponents()) {
				if (component.getOfferId() != null) {
					costs.add(new Cost(component.getId(), item.getReference(), item.getDesignation(), component.getLabel(),
							component.getOfferId(), component.getUnitCost(),
							itemQuantity.multiply(component.getQuantity())
								.multiply(BigDecimal.ONE.add(component.getWasteRate()))));
				}
			}
		}
		return costs;
	}

}
