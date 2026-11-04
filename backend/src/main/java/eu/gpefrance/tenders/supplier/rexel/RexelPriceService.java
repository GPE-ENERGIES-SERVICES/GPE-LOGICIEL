package eu.gpefrance.tenders.supplier.rexel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.stereotype.Component;

import eu.gpefrance.tenders.supplier.SupplierConnector.Place;
import eu.gpefrance.tenders.supplier.SupplierConnector.Price;
import eu.gpefrance.tenders.supplier.SupplierConnector.Product;

import tools.jackson.databind.JsonNode;

/** The API ProductPrice: the prices of the account of GPE. */
@Component
class RexelPriceService {

	private final RexelHttpClient client;

	RexelPriceService(RexelHttpClient client) {
		this.client = client;
	}

	List<Price> prices(List<Product> products, Place place) {
		return client.query("/external/productprices/productSalePrices", "getProductSalePricesExt",
				"productSalePricesExt", products, place, RexelPriceService::read);
	}

	/** A missing, zero or unreadable net price leaves the line out: never a cost of 0 €. */
	static Price read(JsonNode line) {
		String code = text(line, "supplierCode");
		String ref = text(line, "supplierComRef");
		BigDecimal net = number(line, "clientNetPrice");
		if (code == null || ref == null || net == null || net.signum() <= 0) {
			return null;
		}
		BigDecimal base = number(line, "clientBasePrice");
		// shortcut: D3E kept apart, not added to the cost, until Rexel says if the net price holds it.
		BigDecimal ecoTax = "true".equalsIgnoreCase(text(line, "hasD3E")) ? number(line, "D3ECost") : null;
		String label = text(line, "itemLabel");
		return new Price(code, ref, text(line, "rexelRef"), label == null ? null : label.replaceAll("\\s+", " "), base,
				discount(base, net), net,
				ecoTax, text(line, "conditionLabel"), text(line, "priceLabel"), text(line, "salesAgreement"));
	}

	/** From the two prices: the answer has no "referenceRebate", only a label such as "GP 39,73". */
	private static BigDecimal discount(BigDecimal base, BigDecimal net) {
		if (base == null || base.signum() <= 0 || net.compareTo(base) > 0) {
			return null;
		}
		return BigDecimal.ONE.subtract(net.divide(base, 4, RoundingMode.HALF_UP));
	}

	static String text(JsonNode line, String field) {
		String text = line.path(field).asString(null);
		return text == null || text.isBlank() ? null : text.strip();
	}

	/** Every value is a string in the documentation: "1 234,56" and "1234.56" are both read. */
	private static BigDecimal number(JsonNode line, String field) {
		String text = text(line, field);
		if (text == null) {
			return null;
		}
		String digits = text.replaceAll("[\\s\\u00A0]", "");
		if (digits.contains(",")) {
			digits = digits.replace(".", "").replace(',', '.');
		}
		try {
			BigDecimal number = new BigDecimal(digits);
			return number.signum() < 0 ? null : number.setScale(4, RoundingMode.HALF_UP);
		}
		catch (NumberFormatException ex) {
			return null;
		}
	}

}
