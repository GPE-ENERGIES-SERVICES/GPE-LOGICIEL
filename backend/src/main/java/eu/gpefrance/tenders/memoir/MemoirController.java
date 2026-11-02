package eu.gpefrance.tenders.memoir;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import eu.gpefrance.tenders.memoir.MemoirService.Workspace;
import eu.gpefrance.tenders.memoir.MemoirStore.MemoirRow;
import eu.gpefrance.tenders.memoir.MemoirStore.SectionRow;
import eu.gpefrance.tenders.user.AppUserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The technical memoirs: the list, the workspace of one memoir (outline,
 * requirements, content, work of the AI), and their images.
 */
@RestController
public class MemoirController {

	private final MemoirService service;

	private final MemoirJobs jobs;

	private final MemoirFiles files;

	public MemoirController(MemoirService service, MemoirJobs jobs, MemoirFiles files) {
		this.service = service;
		this.jobs = jobs;
		this.files = files;
	}

	public record MemoirRequest(@Size(max = 300) String title, Long tenderId, @Size(max = 50) String lotNumber,
			MemoirStatus status) {
	}

	public record SectionRequest(Long parentId, @NotBlank @Size(max = 300) String title,
			@Size(max = 4000) String description) {
	}

	public record OutlineRequest(@NotNull @Size(max = 500) List<MemoirService.@NotNull @Valid Placement> sections) {
	}

	public record LinksRequest(@NotNull @Size(max = 200) List<@NotNull Long> requirementIds) {
	}

	public record RequirementRequest(@Size(max = 20) String category, @NotBlank @Size(max = 1000) String text) {
	}

	/** @param html the content of the section, cleaned by the server */
	public record ContentRequest(@Size(max = 2_000_000) String html) {
	}

	public record AiRequest(@NotNull MemoirAi.Action action, @Size(max = 1000) String instruction,
			Long requirementId) {
	}

	public record WritingRequest(@Size(max = 500) List<@NotNull Long> sectionIds) {
	}

	public record ImageResponse(Long id, String url) {
	}

	@GetMapping("/api/memoirs")
	public List<MemoirRow> list() {
		return service.list();
	}

	@PostMapping("/api/memoirs")
	@ResponseStatus(HttpStatus.CREATED)
	public MemoirRow create(@Valid @RequestBody MemoirRequest request, @AuthenticationPrincipal AppUserPrincipal user) {
		return service.create(request.title(), request.tenderId(), request.lotNumber(), user.displayName());
	}

	@GetMapping("/api/memoirs/{id}")
	public Workspace workspace(@PathVariable Long id) {
		return service.workspace(id);
	}

	@PutMapping("/api/memoirs/{id}")
	public MemoirRow update(@PathVariable Long id, @Valid @RequestBody MemoirRequest request,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return service.update(id, request.title(), request.lotNumber(), request.status(), user.displayName());
	}

	@DeleteMapping("/api/memoirs/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal user) {
		service.delete(id, user.displayName());
	}

	// Outline

	@PostMapping("/api/memoirs/{id}/outline/template")
	public Workspace template(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal user) {
		return service.template(id, user.displayName());
	}

	@PostMapping("/api/memoirs/{id}/sections")
	@ResponseStatus(HttpStatus.CREATED)
	public Workspace addSection(@PathVariable Long id, @Valid @RequestBody SectionRequest request,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return service.addSection(id, request.parentId(), request.title(), request.description(), user.displayName());
	}

	@PutMapping("/api/memoir-sections/{sectionId}")
	public Workspace renameSection(@PathVariable Long sectionId, @Valid @RequestBody SectionRequest request,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return service.renameSection(sectionId, request.title(), request.description(), user.displayName());
	}

	@DeleteMapping("/api/memoir-sections/{sectionId}")
	public Workspace deleteSection(@PathVariable Long sectionId, @AuthenticationPrincipal AppUserPrincipal user) {
		return service.deleteSection(sectionId, user.displayName());
	}

	@PostMapping("/api/memoir-sections/{sectionId}/acceptance")
	public Workspace acceptSection(@PathVariable Long sectionId, @AuthenticationPrincipal AppUserPrincipal user) {
		return service.acceptSection(sectionId, user.displayName());
	}

	@PostMapping("/api/memoirs/{id}/outline/acceptance")
	public Workspace acceptAllSections(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal user) {
		return service.acceptAllSections(id, user.displayName());
	}

	@PutMapping("/api/memoirs/{id}/outline")
	public Workspace reorder(@PathVariable Long id, @Valid @RequestBody OutlineRequest request,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return service.reorder(id, request.sections(), user.displayName());
	}

	@PostMapping("/api/memoirs/{id}/outline/validation")
	public Workspace validateOutline(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal user) {
		return service.validateOutline(id, user.displayName());
	}

	@PutMapping("/api/memoir-sections/{sectionId}/requirements")
	public Workspace linkRequirements(@PathVariable Long sectionId, @Valid @RequestBody LinksRequest request,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return service.linkRequirements(sectionId, request.requirementIds(), user.displayName());
	}

	@PostMapping("/api/memoirs/{id}/requirements")
	@ResponseStatus(HttpStatus.CREATED)
	public Workspace addRequirement(@PathVariable Long id, @Valid @RequestBody RequirementRequest request,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return service.addRequirement(id, request.category(), request.text(), user.displayName());
	}

	@DeleteMapping("/api/memoirs/{id}/requirements/{requirementId}")
	public Workspace deleteRequirement(@PathVariable Long id, @PathVariable Long requirementId,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return service.deleteRequirement(id, requirementId, user.displayName());
	}

	// Content

	@PutMapping("/api/memoir-sections/{sectionId}/content")
	public SectionRow saveContent(@PathVariable Long sectionId, @Valid @RequestBody ContentRequest request,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return service.saveContent(sectionId, request.html(), user.displayName());
	}

	/** The proposal replaces the content (or follows it with append=true); the former content is kept. */
	@PostMapping("/api/memoir-sections/{sectionId}/proposal/acceptance")
	public SectionRow acceptProposal(@PathVariable Long sectionId, @RequestBody(required = false) java.util.Map<String, Boolean> body,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return service.acceptProposal(sectionId, body != null && Boolean.TRUE.equals(body.get("append")),
				user.displayName());
	}

	@DeleteMapping("/api/memoir-sections/{sectionId}/proposal")
	public SectionRow rejectProposal(@PathVariable Long sectionId, @AuthenticationPrincipal AppUserPrincipal user) {
		return service.rejectProposal(sectionId, user.displayName());
	}

	@GetMapping("/api/memoir-sections/{sectionId}/revisions")
	public List<MemoirStore.RevisionRow> revisions(@PathVariable Long sectionId) {
		return service.revisions(sectionId);
	}

	@PostMapping("/api/memoir-sections/{sectionId}/revisions/{revisionId}/restoration")
	public SectionRow restore(@PathVariable Long sectionId, @PathVariable Long revisionId,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return service.restore(sectionId, revisionId, user.displayName());
	}

	@PostMapping("/api/memoir-sections/{sectionId}/company/{block}")
	public SectionRow insertCompany(@PathVariable Long sectionId, @PathVariable CompanyBlocks.Block block,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return service.insertCompany(sectionId, block, user.displayName());
	}

	// AI

	@PostMapping("/api/memoir-sections/{sectionId}/ai")
	public SectionRow askAi(@PathVariable Long sectionId, @Valid @RequestBody AiRequest request,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return service.askAi(sectionId, request.action(), request.instruction(), request.requirementId(),
				user.displayName());
	}

	/** Reads the consultation file in the background; the workspace shows its progress. */
	@PostMapping("/api/memoirs/{id}/analysis")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public Workspace analyse(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal user) {
		jobs.startAnalysis(service.found(id), user.displayName());
		return service.workspace(id);
	}

	/** Writes the empty sections of the validated outline in the background, one after the other. */
	@PostMapping("/api/memoirs/{id}/writing")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public Workspace write(@PathVariable Long id, @Valid @RequestBody(required = false) WritingRequest request,
			@AuthenticationPrincipal AppUserPrincipal user) {
		jobs.startWriting(service.found(id),
				request == null || request.sectionIds() == null ? List.of() : request.sectionIds(), user.displayName());
		return service.workspace(id);
	}

	@PostMapping("/api/memoirs/{id}/job/cancellation")
	public Workspace cancel(@PathVariable Long id) {
		jobs.cancel(id);
		return service.workspace(id);
	}

	// Images

	@PostMapping("/api/memoirs/{id}/files")
	@ResponseStatus(HttpStatus.CREATED)
	public ImageResponse uploadImage(@PathVariable Long id, @RequestPart("file") MultipartFile file,
			@AuthenticationPrincipal AppUserPrincipal user) {
		Long fileId = service.uploadImage(id, file, user.displayName());
		return new ImageResponse(fileId, "/api/memoirs/files/" + fileId);
	}

	@GetMapping("/api/memoirs/files/{fileId}")
	public ResponseEntity<Resource> image(@PathVariable Long fileId) {
		MemoirStore.FileRow file = service.file(fileId);
		MediaType type = MediaTypeFactory.getMediaType(file.fileName()).orElse(MediaType.APPLICATION_OCTET_STREAM);
		return ResponseEntity.ok()
			.contentType(type)
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.inline().filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
			.header("X-Content-Type-Options", "nosniff")
			.body(new FileSystemResource(files.resolve(file.storagePath())));
	}

}
