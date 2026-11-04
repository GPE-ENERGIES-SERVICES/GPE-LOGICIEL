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
 * The offers are held by GPE ({@link ImportedSupplierCatalog}): they come from
 * the files of the suppliers, and {@link SupplierPricingService} updates their
 * prices from the service of a supplier when it has one.
 */
public interface SupplierCatalog {

	/**
	 * @param query a reference (supplier, manufacturer, EAN), or words of the
	 * designation
	 * @param supplierId only this supplier, or null for all
	 */
	List<Offer> search(String query, Long supplierId, int limit);

	Optional<Offer> find(Long offerId);

	/** The offers of a supplier, by designation. */
	List<Offer> list(Long supplierId, int limit);

	/**
	 * A price of a supplier for one of its references.
	 * @param packQuantity units sold for the price (100 for a reel of 100 m)
	 * @param netPrice price paid, discount deducted, for the pack
	 * @param validFrom the date of the price given at the import, or null
	 * @param manufacturerCode the code of the manufacturer at the supplier, or null
	 * @param ecoTax the eco-tax given by the supplier, not part of the cost, or null
	 * @param priceSource IMPORT, or the service of the supplier the price was read from
	 */
	record Offer(Long id, Long supplierId, String supplierName, String supplierRef, String manufacturerRef,
			String ean, String designation, String purchaseUnit, BigDecimal packQuantity, BigDecimal listPrice,
			BigDecimal discountRate, BigDecimal netPrice, LocalDate validFrom, LocalDate validUntil,
			Instant importedAt, String manufacturerCode, BigDecimal ecoTax, String priceSource) {

		/** The cost of one unit: net price of the pack divided by its quantity. */
		public BigDecimal unitCost() {
			return netPrice.divide(packQuantity, 4, RoundingMode.HALF_UP);
		}

	}

}
