package eu.gpefrance.tenders.supplier.rexel;

import static eu.gpefrance.tenders.supplier.rexel.RexelPriceService.text;

import java.util.List;

import org.springframework.stereotype.Component;

import eu.gpefrance.tenders.supplier.SupplierConnector.Availability;
import eu.gpefrance.tenders.supplier.SupplierConnector.Place;
import eu.gpefrance.tenders.supplier.SupplierConnector.Product;

import tools.jackson.databind.JsonNode;

/** The API Stocks: the stocks of the branch, the warehouse and the service center, and the delays. */
@Component
class RexelStockService {

	private final RexelHttpClient client;

	RexelStockService(RexelHttpClient client) {
		this.client = client;
	}

	List<Availability> availability(List<Product> products, Place place) {
		return client.query("/external/stocks/positions", "getPositionsExtRequest", "getPositionsExt", products, place,
				RexelStockService::read);
	}

	/** Reads one line; the label is only shown here, as Rexel writes it. */
	static Availability read(JsonNode line) {
		String code = text(line, "supplierCode");
		String ref = text(line, "supplierComRef");
		if (code == null || ref == null) {
			return null;
		}
		return new Availability(code, ref, text(line, "itemLabel"), text(line, "availableBranchStock"),
				text(line, "branchAvailabilityDelay"), text(line, "branchMinimumQuantity"),
				text(line, "branchMultipleQuantity"), text(line, "availableCLRStock"),
				text(line, "DCAvailabilityDelay"), text(line, "DCDeliveryInfos"), text(line, "DCMinimumQuantity"),
				text(line, "DCMultipleQuantity"), text(line, "availableServiceCenterStock"),
				text(line, "serviceCenterPickUpInfos"), text(line, "deliveryDate"),
				text(line, "backOrderDeliveryDate"), text(line, "cutOffDelivery"));
	}

}
