package eu.gpefrance.tenders.estimate;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import eu.gpefrance.tenders.estimate.PriceLibraryService.Import;
import eu.gpefrance.tenders.estimate.PriceLibraryService.Item;
import eu.gpefrance.tenders.user.AppUserPrincipal;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The price library: read by every user, filled and emptied by an
 * administrator (security configuration).
 */
@RestController
@Validated
public class PriceLibraryController {

	private final PriceLibraryService service;

	public PriceLibraryController(PriceLibraryService service) {
		this.service = service;
	}

	@PostMapping("/api/price-library/imports")
	@ResponseStatus(HttpStatus.CREATED)
	public Import importFile(@RequestPart("file") MultipartFile file,
			@RequestParam @NotBlank @Size(max = 200) String label,
			@RequestParam(required = false) @Min(1990) @Max(2100) Integer priceYear,
			@RequestParam(required = false) @Pattern(regexp = "ELECTRICITY|PHOTOVOLTAIC") String domain,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return service.importFile(file, label, priceYear, domain, user.displayName());
	}

	@GetMapping("/api/price-library/imports")
	public List<Import> imports() {
		return service.imports();
	}

	@DeleteMapping("/api/price-library/imports/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteImport(@PathVariable Long id) {
		service.deleteImport(id);
	}

	@GetMapping("/api/price-library")
	public List<Item> search(@RequestParam(required = false) @Size(max = 200) String q,
			@RequestParam(required = false) @Size(max = 30) String unit) {
		return service.search(q, unit);
	}

}
