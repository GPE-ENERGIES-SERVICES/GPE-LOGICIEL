package eu.gpefrance.tenders.supplier.rexel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import eu.gpefrance.tenders.supplier.SupplierConnector.Product;
import eu.gpefrance.tenders.supplier.SupplierConnector.Purchases;
import eu.gpefrance.tenders.supplier.SupplierConnectorException;
import eu.gpefrance.tenders.supplier.SupplierConnectorException.Kind;

import tools.jackson.databind.JsonNode;

/**
 * The products of the past orders of GPE, read only: Rexel offers no search in
 * its catalogue, and these orders are the only list of products it gives.
 * Written "orderDetailsById" here, unlike its documentation ("ordersDetailsById").
 */
@Component
class RexelOrderService {

	private static final int CODE_LENGTH = 3;

	private final RexelHttpClient client;

	private final RexelProperties properties;

	RexelOrderService(RexelHttpClient client, RexelProperties properties) {
		this.client = client;
		this.properties = properties;
	}

	Purchases purchases() {
		String customer = properties.customerId();
		Map<String, Product> products = new LinkedHashMap<>();
		int read = 0;
		int failed = 0;
		for (JsonNode order : elements(client.get("/orders/ordersListByCustomerId/" + customer))) {
			String agency = RexelPriceService.text(order, "codeAgenceCommande");
			String number = RexelPriceService.text(order, "numeroCommande");
			if (agency == null || number == null) {
				failed++;
				continue;
			}
			String code = agency + "-" + "0".repeat(Math.max(0, 9 - number.length())) + number;
			try {
				for (JsonNode holder : elements(client.get("/orders/orderDetailsById/" + customer + "/" + code))) {
					for (JsonNode line : holder.path("lines")) {
						Product product = product(RexelPriceService.text(line, "referenceCommerciale"));
						if (product != null) {
							products.putIfAbsent(product.key(), product);
						}
					}
				}
				read++;
			}
			catch (SupplierConnectorException ex) {
				// An order it does not give is left out; anything else would fail for the next ones too.
				if (ex.getKind() != Kind.REJECTED) {
					throw ex;
				}
				failed++;
			}
		}
		return new Purchases(read, failed, new ArrayList<>(products.values()));
	}

	/**
	 * "FILR2V5G10TGL" is FIL and R2V5G10TGL: seen on every product tried, not
	 * documented. A wrong split gets no price from Rexel, and is then not kept.
	 */
	static Product product(String commercialReference) {
		if (commercialReference == null || commercialReference.length() <= CODE_LENGTH) {
			return null;
		}
		return new Product(commercialReference.substring(0, CODE_LENGTH), commercialReference.substring(CODE_LENGTH), 1);
	}

	/** The answer is {"name": ..., "elements": [...]}, or the order itself. */
	private static List<JsonNode> elements(JsonNode answer) {
		List<JsonNode> found = new ArrayList<>();
		if (answer.path("elements").isArray()) {
			answer.path("elements").forEach(found::add);
		}
		else if (answer.isArray()) {
			answer.forEach(found::add);
		}
		else {
			found.add(answer);
		}
		return found;
	}

}
