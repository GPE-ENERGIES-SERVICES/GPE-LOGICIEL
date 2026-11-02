package eu.gpefrance.tenders.document;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import eu.gpefrance.tenders.user.AppUserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Consultation files analysed without any tender. Once uploaded, a dossier
 * goes through the routes of every dossier: /api/dossiers, /api/documents and
 * /api/analyses.
 */
@RestController
public class DceReviewController {

	private final DceReviewRepository reviews;

	private final DceReviewService service;

	private final DossierService dossierService;

	private final DocumentService documentService;

	private final DocumentProcessor processor;

	private final Clock clock;

	public DceReviewController(DceReviewRepository reviews, DceReviewService service, DossierService dossierService,
			DocumentService documentService, DocumentProcessor processor, Clock clock) {
		this.reviews = reviews;
		this.service = service;
		this.dossierService = dossierService;
		this.documentService = documentService;
		this.processor = processor;
		this.clock = clock;
	}

	@GetMapping("/api/dce-reviews")
	public List<ReviewResponse> list() {
		return reviews.findAllByOrderByIdDesc().stream().map(ReviewResponse::from).toList();
	}

	@PostMapping("/api/dce-reviews")
	@ResponseStatus(HttpStatus.CREATED)
	public ReviewResponse create(@Valid @RequestBody ReviewRequest request,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return ReviewResponse
			.from(reviews.save(new DceReview(request.name().strip(), user.displayName(), clock.instant())));
	}

	@GetMapping("/api/dce-reviews/{reviewId}")
	public ReviewResponse get(@PathVariable Long reviewId) {
		return reviews.findById(reviewId)
			.map(ReviewResponse::from)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Analyse de DCE introuvable."));
	}

	@PutMapping("/api/dce-reviews/{reviewId}")
	public ReviewResponse rename(@PathVariable Long reviewId, @Valid @RequestBody ReviewRequest request) {
		return ReviewResponse.from(service.rename(reviewId, request.name().strip()));
	}

	/**
	 * Deletes the analysis, its dossiers and their files.
	 */
	@DeleteMapping("/api/dce-reviews/{reviewId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long reviewId) {
		service.delete(reviewId);
	}

	/**
	 * Gives the dossiers and analyses to a tender: the review no longer exists.
	 */
	@PostMapping("/api/dce-reviews/{reviewId}/attachment")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void attach(@PathVariable Long reviewId, @Valid @RequestBody AttachmentRequest request) {
		service.attach(reviewId, request.tenderId());
	}

	public record AttachmentRequest(@NotNull Long tenderId) {
	}

	@GetMapping("/api/dce-reviews/{reviewId}/dossiers")
	public List<DossierResponse> dossiers(@PathVariable Long reviewId) {
		return documentService.dossiersOfReview(reviewId);
	}

	/**
	 * Uploads a new version of the file (files or ZIP archives), read in the
	 * background.
	 */
	@PostMapping("/api/dce-reviews/{reviewId}/dossiers")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public DossierResponse upload(@PathVariable Long reviewId, @RequestParam("files") List<MultipartFile> files,
			@AuthenticationPrincipal AppUserPrincipal user) {
		Long dossierId = dossierService.uploadForReview(reviewId, files, user.displayName());
		processor.startInBackground(dossierId);
		return documentService.dossier(dossierId);
	}

	public record ReviewRequest(@NotBlank(message = "Donnez un nom à cette analyse.") @Size(max = 300,
			message = "Le nom est trop long.") String name) {
	}

	public record ReviewResponse(Long id, String name, String createdBy, Instant createdAt) {

		static ReviewResponse from(DceReview review) {
			return new ReviewResponse(review.getId(), review.getName(), review.getCreatedBy(), review.getCreatedAt());
		}

	}

}
