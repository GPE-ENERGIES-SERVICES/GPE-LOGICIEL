package eu.gpefrance.tenders.supplier;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Where the prices of the suppliers are read. The estimates know this
 * contract only, never a supplier: they copy the price of the offer picked.
 * Today the prices are those imported from the files of the suppliers
 * ({@link ImportedSupplierCatalog}); a connector to the service of a supplier,
 * once its terms allow it, would be another implementation.
 */
public interface SupplierCatalog {

	/**
	 * @param query a reference (supplier, manufacturer, EAN), or words of the
	 * designation
	 * @param supplierId only this supplier, or null for all
	 */
	List<Offer> search(String query, Long supplierId, int limit);

	Optional<Offer> find(Long offerId);

	/**
	 * A price of a supplier for one of its references.
	 * @param packQuantity units sold for the price (100 for a reel of 100 m)
	 * @param netPrice price paid, discount deducted, for the pack
	 * @param validFrom the date of the price given at the import, or null
	 */
	record Offer(Long id, Long supplierId, String supplierName, String supplierRef, String manufacturerRef,
			String ean, String designation, String purchaseUnit, BigDecimal packQuantity, BigDecimal listPrice,
			BigDecimal discountRate, BigDecimal netPrice, LocalDate validFrom, LocalDate validUntil,
			Instant importedAt) {

		/** The cost of one unit: net price of the pack divided by its quantity. */
		public BigDecimal unitCost() {
			return netPrice.divide(packQuantity, 4, RoundingMode.HALF_UP);
		}

	}

}
