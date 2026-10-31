package eu.gpefrance.tenders.supplier;

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
import eu.gpefrance.tenders.supplier.SupplierService.ImportResult;
import eu.gpefrance.tenders.supplier.SupplierService.SupplierResponse;
import eu.gpefrance.tenders.user.AppUserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The suppliers and their prices. Creating a supplier and importing its
 * prices is for administrators (security configuration); every user searches
 * the prices.
 */
@RestController
public class SupplierController {

	private final SupplierService service;

	private final SupplierCatalog catalog;

	public SupplierController(SupplierService service, SupplierCatalog catalog) {
		this.service = service;
		this.catalog = catalog;
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

	@GetMapping("/api/supplier-offers")
	public List<Offer> search(@RequestParam @Size(max = 200) String q, @RequestParam(required = false) Long supplierId,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
		return catalog.search(q, supplierId, limit);
	}

	public record SupplierRequest(@NotBlank @Size(max = 30) @Pattern(regexp = "[A-Za-z0-9_-]+") String code,
			@NotBlank @Size(max = 100) String name) {
	}

}
