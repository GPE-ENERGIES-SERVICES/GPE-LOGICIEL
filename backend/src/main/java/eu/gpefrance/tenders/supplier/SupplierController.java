package eu.gpefrance.tenders.supplier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import eu.gpefrance.tenders.supplier.SupplierCatalog.Offer;
import eu.gpefrance.tenders.supplier.SupplierConnector.Place;
import eu.gpefrance.tenders.supplier.SupplierPricingService.ConnectorResponse;
import eu.gpefrance.tenders.supplier.SupplierPricingService.OfferAvailability;
import eu.gpefrance.tenders.supplier.SupplierPricingService.PriceHistoryLine;
import eu.gpefrance.tenders.supplier.SupplierPricingService.RefreshedOffer;
import eu.gpefrance.tenders.supplier.SupplierService.ImportResult;
import eu.gpefrance.tenders.supplier.SupplierService.SupplierResponse;
import eu.gpefrance.tenders.user.AppUserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * The suppliers and their prices. Creating a supplier and importing its
 * prices is for administrators (security configuration); every user searches
 * the prices.
 */
@RestController
public class SupplierController {

	private static final int MAX_LISTED_OFFERS = 2000;

	private final SupplierService service;

	private final SupplierCatalog catalog;

	private final SupplierPricingService pricing;

	public SupplierController(SupplierService service, SupplierCatalog catalog, SupplierPricingService pricing) {
		this.service = service;
		this.catalog = catalog;
		this.pricing = pricing;
	}

	@GetMapping("/api/suppliers")
	public List<SupplierResponse> list() {
		return service.list();
	}

	@PostMapping("/api/suppliers")
	@ResponseStatus(HttpStatus.CREATED)
	public SupplierResponse create(@Valid @RequestBody SupplierRequest request,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return service.create(request.code(), request.name(), user.displayName());
	}

	@PostMapping("/api/suppliers/{id}/offers")
	public ImportResult importOffers(@PathVariable Long id, @RequestPart("file") MultipartFile file,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validFrom,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return service.importOffers(id, file, validFrom, user.displayName());
	}

	/** The prices of a supplier, to be read on the page of the suppliers; for administrators. */
	@GetMapping("/api/suppliers/{id}/offers")
	public List<Offer> offers(@PathVariable Long id) {
		return catalog.list(id, MAX_LISTED_OFFERS);
	}

	@GetMapping("/api/supplier-offers")
	public List<Offer> search(@RequestParam @Size(max = 200) String q, @RequestParam(required = false) Long supplierId,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
		return catalog.search(q, supplierId, limit);
	}

	/** The services of the suppliers known to the application, and which ones may be asked. */
	@GetMapping("/api/supplier-connectors")
	public List<ConnectorResponse> connectors() {
		return pricing.connectors();
	}

	@PostMapping("/api/supplier-offers/refresh")
	public List<RefreshedOffer> refresh(@Valid @RequestBody OffersQuery query,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return pricing.refresh(query.offerIds(), query.orderingQuantity(), query.place(), user.displayName());
	}

	@PostMapping("/api/supplier-offers/availability")
	public List<OfferAvailability> availability(@Valid @RequestBody OffersQuery query) {
		return pricing.availability(query.offerIds(), query.orderingQuantity(), query.place());
	}

	/** A reference given by hand, checked by the supplier: the offer exists only if it answers with a price. */
	@PostMapping("/api/supplier-offers/lookup")
	public Offer lookup(@Valid @RequestBody LookupRequest request, @AuthenticationPrincipal AppUserPrincipal user) {
		return pricing.lookup(request.supplierCode(), request.manufacturerCode(), request.manufacturerRef(),
				request.quantity() == null ? 1 : request.quantity(), request.packQuantity(), request.unit(),
				new Place(null, null, null), user.displayName());
	}

	/** Fills the catalogue from the past orders, in the background; for administrators (security configuration). */
	@PostMapping("/api/suppliers/connectors/{code}/catalogue")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public SupplierPricingService.CatalogueFilling fillCatalogue(@PathVariable String code,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return pricing.startFilling(code, user.displayName());
	}

	@GetMapping("/api/suppliers/connectors/{code}/catalogue")
	public SupplierPricingService.CatalogueFilling catalogueFilling(@PathVariable String code) {
		return pricing.filling(code);
	}

	@GetMapping("/api/supplier-offers/{id}/prices")
	public List<PriceHistoryLine> history(@PathVariable Long id) {
		return pricing.history(id);
	}

	public record SupplierRequest(@NotBlank @Size(max = 30) @Pattern(regexp = "[A-Za-z0-9_-]+") String code,
			@NotBlank @Size(max = 100) String name) {
	}

	/**
	 * @param quantity the quantity to buy, a whole number at the suppliers; null for 1
	 */
	public record OffersQuery(@NotEmpty @Size(max = 100) List<@NotNull Long> offerIds,
			@Min(1) @Max(9_999_999) Integer quantity, @Size(max = 4) String agencyCode, @Size(max = 10) String zipCode,
			@Size(max = 100) String city) {

		int orderingQuantity() {
			return quantity == null ? 1 : quantity;
		}

		Place place() {
			return new Place(blankToNull(agencyCode), blankToNull(zipCode), blankToNull(city));
		}

		private static String blankToNull(String text) {
			return text == null || text.isBlank() ? null : text.strip();
		}

	}

	/**
	 * @param manufacturerCode the code of the manufacturer at the supplier (three letters at Rexel)
	 * @param packQuantity units sold for the price of a new offer; null for 1
	 */
	public record LookupRequest(@NotBlank @Size(max = 30) String supplierCode,
			@NotBlank @Size(max = 10) @Pattern(regexp = "[A-Za-z0-9]+") String manufacturerCode,
			@NotBlank @Size(max = 100) String manufacturerRef, @Min(1) @Max(9_999_999) Integer quantity,
			@Positive BigDecimal packQuantity, @Size(max = 20) String unit) {
	}

}
