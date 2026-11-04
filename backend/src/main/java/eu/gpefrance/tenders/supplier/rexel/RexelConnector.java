package eu.gpefrance.tenders.supplier.rexel;

import java.util.List;

import org.springframework.stereotype.Component;

import eu.gpefrance.tenders.supplier.SupplierConnector;
import eu.gpefrance.tenders.supplier.SupplierConnectorException;
import eu.gpefrance.tenders.supplier.SupplierConnectorException.Kind;

/**
 * Rexel, read only: past orders are read, but no call placing an order is part
 * of the application, so that none can ever be placed by accident. Nothing is sent until
 * {@link RexelProperties#configured()}.
 */
@Component
class RexelConnector implements SupplierConnector {

	private final RexelProperties properties;

	private final RexelPriceService prices;

	private final RexelStockService stocks;

	private final RexelOrderService orders;

	RexelConnector(RexelProperties properties, RexelPriceService prices, RexelStockService stocks,
			RexelOrderService orders) {
		this.properties = properties;
		this.prices = prices;
		this.stocks = stocks;
		this.orders = orders;
	}

	@Override
	public Purchases purchases() {
		requireConfigured();
		return orders.purchases();
	}

	@Override
	public String supplierCode() {
		return "REXEL";
	}

	@Override
	public String supplierName() {
		return "Rexel";
	}

	@Override
	public boolean available() {
		return properties.configured();
	}

	@Override
	public String environment() {
		return properties.environment() == null ? null : properties.environment().name();
	}

	@Override
	public String source() {
		return properties.environment() == null ? null : properties.environment().source();
	}

	@Override
	public List<Price> prices(List<Product> products, Place place) {
		requireConfigured();
		return prices.prices(products, place);
	}

	@Override
	public List<Availability> availability(List<Product> products, Place place) {
		requireConfigured();
		return stocks.availability(products, place);
	}

	private void requireConfigured() {
		if (!available()) {
			throw new SupplierConnectorException(Kind.NOT_CONFIGURED);
		}
	}

}
