package eu.gpefrance.tenders.document;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import eu.gpefrance.tenders.document.DocumentService.StoredDocument;
import eu.gpefrance.tenders.user.AppUserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RestController
public class DocumentController {

	private final DossierService dossierService;

	private final DocumentService documentService;

	private final DocumentProcessor processor;

	private final DceLinkService dceLinks;

	public DocumentController(DossierService dossierService, DocumentService documentService,
			DocumentProcessor processor, DceLinkService dceLinks) {
		this.dossierService = dossierService;
		this.documentService = documentService;
		this.processor = processor;
		this.dceLinks = dceLinks;
	}

	/**
	 * Where the consultation file can be obtained, as the notices of each
	 * source give it: links only, the files obtained are the dossiers.
	 */
	@GetMapping("/api/tenders/{tenderId}/dce-links")
	public List<DceLinkResponse> dceLinks(@PathVariable Long tenderId) {
		return dceLinks.linksOf(tenderId).stream().map(DceLinkResponse::from).toList();
	}

	@GetMapping("/api/tenders/{tenderId}/dossiers")
	public List<DossierResponse> dossiers(@PathVariable Long tenderId) {
		return documentService.dossiersOf(tenderId);
	}

	/**
	 * Uploads a new version of the consultation file (files or ZIP archives).
	 * The documents are read in the background.
	 */
	@PostMapping("/api/tenders/{tenderId}/dossiers")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public DossierResponse upload(@PathVariable Long tenderId, @RequestParam("files") List<MultipartFile> files,
			@AuthenticationPrincipal AppUserPrincipal user) {
		Long dossierId = dossierService.upload(tenderId, files, user.displayName());
		processor.startInBackground(dossierId);
		return documentService.dossier(dossierId);
	}

	@PostMapping("/api/dossiers/{dossierId}/retry")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public void retry(@PathVariable Long dossierId) {
		if (documentService.retryFailed(dossierId)) {
			processor.startInBackground(dossierId);
		}
	}

	@PutMapping("/api/documents/{documentId}/piece-type")
	public DossierResponse.DocumentResponse correctPieceType(@PathVariable Long documentId,
			@Valid @RequestBody PieceTypeRequest request) {
		return documentService.correctPieceType(documentId, request.pieceType());
	}

	/**
	 * The file as uploaded, always downloaded: an uploaded file is never
	 * displayed by the browser as a page of the application.
	 */
	@GetMapping("/api/documents/{documentId}/file")
	public ResponseEntity<Resource> original(@PathVariable Long documentId) {
		StoredDocument document = documentService.original(documentId);
		return ResponseEntity.ok()
			.contentType(MediaType.APPLICATION_OCTET_STREAM)
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.attachment().filename(fileName(document), StandardCharsets.UTF_8).build().toString())
			.body(new FileSystemResource(document.path()));
	}

	/**
	 * The readable PDF, displayed in the browser so it opens at a given page.
	 */
	@GetMapping("/api/documents/{documentId}/pdf")
	public ResponseEntity<Resource> pdf(@PathVariable Long documentId) {
		StoredDocument document = documentService.readablePdf(documentId);
		return ResponseEntity.ok()
			.contentType(MediaType.APPLICATION_PDF)
			.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
				.filename(baseName(fileName(document)) + ".pdf", StandardCharsets.UTF_8)
				.build()
				.toString())
			.body(new FileSystemResource(document.path()));
	}

	@GetMapping("/api/documents/{documentId}/pages/{pageNumber}")
	public PageResponse page(@PathVariable Long documentId, @PathVariable int pageNumber) {
		DocumentPage page = documentService.page(documentId, pageNumber);
		return new PageResponse(page.getPageNumber(), page.getText(), page.getMethod());
	}

	private static String fileName(StoredDocument document) {
		String name = document.fileName();
		return name.substring(name.lastIndexOf('/') + 1);
	}

	private static String baseName(String fileName) {
		int dot = fileName.lastIndexOf('.');
		return dot > 0 ? fileName.substring(0, dot) : fileName;
	}

	public record PieceTypeRequest(@NotNull PieceType pieceType) {
	}

	public record PageResponse(int pageNumber, String text, PageMethod method) {
	}

}
