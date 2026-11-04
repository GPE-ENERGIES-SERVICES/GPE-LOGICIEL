package eu.gpefrance.tenders.supplier;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * The service of a supplier, which gives the price and the stock of references
 * already known: finding a reference stays the work of {@link SupplierCatalog}.
 * A connector is tied to a supplier by its code; another supplier is another
 * implementation, nothing else changes.
 */
public interface SupplierConnector {

	/** The code of the supplier in GPE ({@code supplier.code}). */
	String supplierCode();

	String supplierName();

	/** False while it is disabled or incomplete: nothing is sent then. */
	boolean available();

	/** Shown to the users, TEST above all; null when there is none. */
	String environment();

	/** Written with each price read: where it comes from, test or production. */
	String source();

	/** A product not returned or without a usable price is left out of the answer. */
	List<Price> prices(List<Product> products, Place place);

	/** A product not returned is left out of the answer. */
	List<Availability> availability(List<Product> products, Place place);

	/**
	 * The products GPE already bought from this supplier, when it says so: they
	 * fill the catalogue. Each is checked by asking its price before it is kept.
	 */
	default Purchases purchases() {
		return new Purchases(0, 0, List.of());
	}

	/**
	 * @param ordersRead orders read in full
	 * @param ordersFailed orders the supplier did not give
	 * @param products without duplicates, each for a quantity of one
	 */
	record Purchases(int ordersRead, int ordersFailed, List<Product> products) {
	}

	/**
	 * What identifies a product at the supplier, whatever the case of its letters.
	 * Only letters and digits of the code count: Rexel is asked "SCH" and answers "SCH.".
	 */
	static String key(String manufacturerCode, String manufacturerRef) {
		return (manufacturerCode.replaceAll("[^\\p{Alnum}]", "") + "|" + manufacturerRef.strip()).toUpperCase(Locale.ROOT);
	}

	record Product(String manufacturerCode, String manufacturerRef, int quantity) {

		public String key() {
			return SupplierConnector.key(manufacturerCode, manufacturerRef);
		}

	}

	/** Where to deliver or to collect; each value may be null. */
	record Place(String agencyCode, String zipCode, String city) {

		public boolean isEmpty() {
			return agencyCode == null && zipCode == null && city == null;
		}

	}

	/**
	 * @param supplierRef the reference of the product at the supplier, or null
	 * @param discountRate share deducted, 0.35 for 35 %, or null
	 * @param netPrice always above zero
	 */
	record Price(String manufacturerCode, String manufacturerRef, String supplierRef, String designation,
			BigDecimal listPrice, BigDecimal discountRate, BigDecimal netPrice, BigDecimal ecoTax,
			String conditionLabel, String priceLabel, String salesAgreement) {

		public String key() {
			return SupplierConnector.key(manufacturerCode, manufacturerRef);
		}

	}

	/** The values of the supplier as it gives them: their format is not documented. */
	record Availability(String manufacturerCode, String manufacturerRef, String designation, String branchStock,
			String branchDelay, String branchMinimum, String branchMultiple, String warehouseStock,
			String warehouseDelay, String warehouseInfo, String warehouseMinimum, String warehouseMultiple,
			String serviceCenterStock, String serviceCenterInfo, String deliveryDate, String backOrderDeliveryDate,
			String cutOff) {

		public String key() {
			return SupplierConnector.key(manufacturerCode, manufacturerRef);
		}

	}

}
