package eu.gpefrance.tenders.response;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import eu.gpefrance.tenders.response.ResponsePiece.Category;
import eu.gpefrance.tenders.response.ResponseService.ResponseSummary;
import eu.gpefrance.tenders.response.ResponseService.ResponseView;
import eu.gpefrance.tenders.user.AppUserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The answer to a tender: its pieces, what is joined to them and the archive.
 */
@RestController
public class ResponseController {

	private final ResponseService service;

	public ResponseController(ResponseService service) {
		this.service = service;
	}

	public record NewPiece(@NotNull Category category, @NotBlank @Size(max = 500) String label) {
	}

	/**
	 * @param companyDocumentId the official document joined, null for none
	 */
	public record PieceChange(@NotBlank @Size(max = 500) String label, boolean notNeeded, Long companyDocumentId) {
	}

	public record Checked(boolean checked) {
	}

	@GetMapping("/api/responses")
	public List<ResponseSummary> list() {
		return service.list();
	}

	@GetMapping("/api/tenders/{tenderId}/response")
	public ResponseView view(@PathVariable Long tenderId) {
		return service.view(tenderId);
	}

	/**
	 * Adds the pieces read by the latest analysis that are not listed yet.
	 */
	@PostMapping("/api/tenders/{tenderId}/response/preparation")
	public ResponseView prepare(@PathVariable Long tenderId, @AuthenticationPrincipal AppUserPrincipal user) {
		return service.prepare(tenderId, user.displayName());
	}

	@PostMapping("/api/tenders/{tenderId}/response/pieces")
	public ResponseView add(@PathVariable Long tenderId, @Valid @RequestBody NewPiece piece,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return service.add(tenderId, piece.category(), piece.label(), user.displayName());
	}

	@PutMapping("/api/response-pieces/{id}")
	public ResponseView change(@PathVariable Long id, @Valid @RequestBody PieceChange change,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return service.change(id, change.label(), change.notNeeded(), change.companyDocumentId(), user.displayName());
	}

	@PutMapping("/api/response-pieces/{id}/check")
	public ResponseView check(@PathVariable Long id, @RequestBody Checked checked,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return service.check(id, checked.checked(), user.displayName());
	}

	@DeleteMapping("/api/response-pieces/{id}")
	public ResponseView delete(@PathVariable Long id) {
		return service.delete(id);
	}

	@PostMapping("/api/response-pieces/{id}/file")
	public ResponseView upload(@PathVariable Long id, @RequestParam("file") MultipartFile file,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return service.upload(id, file, user.displayName());
	}

	@DeleteMapping("/api/response-pieces/{id}/file")
	public ResponseView removeFile(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal user) {
		return service.removeFile(id, user.displayName());
	}

	/** Always downloaded: an uploaded file is never displayed as a page of the application. */
	@GetMapping("/api/response-pieces/{id}/file")
	public ResponseEntity<Resource> file(@PathVariable Long id) {
		ResponseService.PieceFile file = service.file(id);
		return ResponseEntity.ok()
			.contentType(MediaType.APPLICATION_OCTET_STREAM)
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.attachment().filename(file.name(), StandardCharsets.UTF_8).build().toString())
			.body(new FileSystemResource(file.path()));
	}

	@GetMapping("/api/tenders/{tenderId}/response/archive")
	public ResponseEntity<StreamingResponseBody> archive(@PathVariable Long tenderId) {
		List<ResponseService.PieceFile> entries = service.archiveEntries(tenderId);
		StreamingResponseBody body = out -> ResponseService.writeArchive(entries, out);
		return ResponseEntity.ok()
			.contentType(MediaType.APPLICATION_OCTET_STREAM)
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.attachment().filename("dossier-reponse-" + tenderId + ".zip").build().toString())
			.body(body);
	}

}
