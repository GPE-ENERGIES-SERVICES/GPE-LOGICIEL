package eu.gpefrance.tenders.equipment;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import eu.gpefrance.tenders.equipment.EquipmentService.Sheet;
import eu.gpefrance.tenders.tender.TenderRepository;
import eu.gpefrance.tenders.user.AppUserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The library of technical sheets, read by every user and filled by an
 * administrator (security configuration), and the equipment of a tender.
 */
@RestController
public class EquipmentSheetController {

	private final EquipmentService service;

	private final TenderRepository tenders;

	public EquipmentSheetController(EquipmentService service, TenderRepository tenders) {
		this.service = service;
		this.tenders = tenders;
	}

	public record SheetRequest(@Size(max = 30) String category, @Size(max = 100) String brand,
			@Size(max = 200) String reference) {
	}

	public record ChoiceRequest(@NotNull @Size(max = 200) List<@NotNull Long> sheetIds) {
	}

	@GetMapping("/api/equipment-sheets")
	public List<Sheet> list() {
		return service.list();
	}

	/** What is not given is guessed from the file name. */
	@PostMapping("/api/equipment-sheets")
	@ResponseStatus(HttpStatus.CREATED)
	public Sheet add(@RequestPart("file") MultipartFile file, @RequestParam(required = false) @Size(max = 30) String category,
			@RequestParam(required = false) @Size(max = 100) String brand,
			@RequestParam(required = false) @Size(max = 200) String reference,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return service.add(file, category, brand, reference, user.displayName());
	}

	@PutMapping("/api/equipment-sheets/{id}")
	public Sheet update(@PathVariable Long id, @Valid @RequestBody SheetRequest request) {
		return service.update(id, request.category(), request.brand(), request.reference());
	}

	@DeleteMapping("/api/equipment-sheets/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		service.delete(id);
	}

	@GetMapping("/api/equipment-sheets/{id}/file")
	public ResponseEntity<Resource> file(@PathVariable Long id) {
		EquipmentService.SheetFile file = service.file(id);
		return ResponseEntity.ok()
			.contentType(MediaType.APPLICATION_PDF)
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.inline().filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
			.body(new FileSystemResource(file.path()));
	}

	@GetMapping("/api/tenders/{tenderId}/equipment")
	public List<Sheet> ofTender(@PathVariable Long tenderId) {
		return service.ofTender(tenderId);
	}

	@PutMapping("/api/tenders/{tenderId}/equipment")
	public List<Sheet> choose(@PathVariable Long tenderId, @Valid @RequestBody ChoiceRequest request,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return service.choose(tenderId, request.sheetIds(), user.displayName());
	}

	/** The equipment table of GPE for the tender, as Excel. */
	@GetMapping("/api/tenders/{tenderId}/equipment/table")
	public ResponseEntity<byte[]> table(@PathVariable Long tenderId) {
		String title = tenders.findById(tenderId)
			.orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND,
					"Appel d'offres introuvable."))
			.getTitle();
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.attachment().filename("Tableau matériel.xlsx", StandardCharsets.UTF_8).build().toString())
			.body(EquipmentTable.write(title, service.ofTender(tenderId)));
	}

}
