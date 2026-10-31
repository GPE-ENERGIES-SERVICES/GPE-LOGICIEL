package eu.gpefrance.tenders.estimate;

import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import eu.gpefrance.tenders.user.AppUserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The estimates (« chiffrages ») of the tenders. Every change answers the
 * whole estimate, computed again.
 */
@RestController
public class EstimateController {

	private final EstimateService service;

	private final EstimateImportService imports;

	private final EstimateExportService exports;

	public EstimateController(EstimateService service, EstimateImportService imports, EstimateExportService exports) {
		this.service = service;
		this.imports = imports;
		this.exports = exports;
	}

	@GetMapping("/api/estimates")
	public List<EstimateService.Listed> recent(
			@RequestParam(defaultValue = "100") @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(500) int limit) {
		return service.recent(limit);
	}

	@GetMapping("/api/tenders/{tenderId}/estimates")
	public List<EstimateResponse.Summary> ofTender(@PathVariable Long tenderId) {
		return service.ofTender(tenderId);
	}

	@PostMapping("/api/tenders/{tenderId}/estimates")
	@ResponseStatus(HttpStatus.CREATED)
	public EstimateResponse create(@PathVariable Long tenderId, @Valid @RequestBody EstimateRequest request,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return service.create(tenderId, request, user.displayName());
	}

	@GetMapping("/api/estimates/{id}")
	public EstimateResponse get(@PathVariable Long id) {
		return service.get(id);
	}

	@PutMapping("/api/estimates/{id}")
	public EstimateResponse update(@PathVariable Long id, @Valid @RequestBody EstimateRequest request,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return service.update(id, request, user.displayName());
	}

	@DeleteMapping("/api/estimates/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal user) {
		service.delete(id, user.displayName());
	}

	@PostMapping("/api/estimates/{id}/items")
	@ResponseStatus(HttpStatus.CREATED)
	public EstimateResponse addItem(@PathVariable Long id, @Valid @RequestBody ItemRequest request,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return service.addItem(id, request, user.displayName());
	}

	@PutMapping("/api/estimates/{id}/items/order")
	public EstimateResponse reorder(@PathVariable Long id, @Valid @RequestBody ItemOrder request,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return service.reorder(id, request.itemIds(), user.displayName());
	}

	@PutMapping("/api/estimate-items/{itemId}")
	public EstimateResponse updateItem(@PathVariable Long itemId, @Valid @RequestBody ItemRequest request,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return service.updateItem(itemId, request, user.displayName());
	}

	@DeleteMapping("/api/estimate-items/{itemId}")
	public EstimateResponse deleteItem(@PathVariable Long itemId, @AuthenticationPrincipal AppUserPrincipal user) {
		return service.deleteItem(itemId, user.displayName());
	}

	@PostMapping("/api/estimate-items/{itemId}/components")
	@ResponseStatus(HttpStatus.CREATED)
	public EstimateResponse addComponent(@PathVariable Long itemId, @Valid @RequestBody ComponentRequest request,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return service.addComponent(itemId, request, user.displayName());
	}

	@PutMapping("/api/estimate-components/{componentId}")
	public EstimateResponse updateComponent(@PathVariable Long componentId,
			@Valid @RequestBody ComponentRequest request, @AuthenticationPrincipal AppUserPrincipal user) {
		return service.updateComponent(componentId, request, user.displayName());
	}

	@DeleteMapping("/api/estimate-components/{componentId}")
	public EstimateResponse deleteComponent(@PathVariable Long componentId,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return service.deleteComponent(componentId, user.displayName());
	}

	/** Freezes the draft: nothing may be missing. */
	@PostMapping("/api/estimates/{id}/validation")
	public EstimateResponse validate(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal user) {
		return service.validate(id, user.displayName());
	}

	/** A new draft copied from this version. */
	@PostMapping("/api/estimates/{id}/versions")
	@ResponseStatus(HttpStatus.CREATED)
	public EstimateResponse newVersion(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal user) {
		return service.newVersion(id, user.displayName());
	}

	/** Copies again the current cost of the rates of GPE in a draft. */
	@PostMapping("/api/estimates/{id}/rates-copy")
	public EstimateResponse copyRatesAgain(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal user) {
		return service.copyRatesAgain(id, user.displayName());
	}

	@GetMapping("/api/estimates/{id}/events")
	public List<EstimateEvent.Response> events(@PathVariable Long id) {
		return service.events(id);
	}

	/** Adds the lines of work of a price schedule of the tender. */
	@PostMapping("/api/estimates/{id}/imports")
	public EstimateResponse importDocument(@PathVariable Long id, @Valid @RequestBody ImportRequest request,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return imports.importDocument(id, request.documentId(), user.displayName());
	}

	@GetMapping("/api/estimates/{id}/export")
	public ResponseEntity<byte[]> export(@PathVariable Long id) {
		EstimateExportService.Export export = exports.export(id);
		return ResponseEntity.ok()
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.attachment().filename(export.fileName()).build().toString())
			.contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
			.body(export.content());
	}

	public record ImportRequest(@NotNull Long documentId) {
	}

	/** Lines pasted from a spreadsheet: changed when they have an id, added otherwise. */
	@PostMapping("/api/estimates/{id}/items/batch")
	public EstimateResponse saveItems(@PathVariable Long id, @Valid @RequestBody ItemBatch request,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return service.saveItems(id, request.rows(), user.displayName());
	}

	public record ItemRow(Long id, @NotNull @Valid ItemRequest item) {
	}

	public record ItemBatch(@NotNull @Size(min = 1, max = 5000) List<@NotNull @Valid ItemRow> rows) {
	}

	public record ItemOrder(@NotNull @Size(max = 10000) List<@NotNull Long> itemIds) {
	}

}
