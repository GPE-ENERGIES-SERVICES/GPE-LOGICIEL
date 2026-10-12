package eu.gpefrance.tenders.duplicate;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import eu.gpefrance.tenders.duplicate.DuplicateResponses.BatchPreview;
import eu.gpefrance.tenders.duplicate.DuplicateResponses.Decision;
import eu.gpefrance.tenders.duplicate.DuplicateResponses.Group;
import eu.gpefrance.tenders.user.AppUserPrincipal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Duplicates to check and the decisions taken on them, for administrators.
 */
@RestController
@RequestMapping("/api/duplicates")
public class DuplicateController {

	private final DuplicateService service;

	public DuplicateController(DuplicateService service) {
		this.service = service;
	}

	@GetMapping
	public List<Group> groups() {
		return service.groups();
	}

	@GetMapping("/decisions")
	public List<Decision> decisions() {
		return service.history().stream().map(Decision::of).toList();
	}

	/**
	 * @param acknowledgeTeamData required when both tenders have data of the team
	 */
	@PostMapping("/merge")
	public Decision merge(@RequestBody MergeRequest request, @AuthenticationPrincipal AppUserPrincipal user) {
		return Decision.of(service.merge(request.keptTenderId(), request.mergedTenderId(),
				Boolean.TRUE.equals(request.acknowledgeTeamData()), user.displayName()));
	}

	@PostMapping("/not-duplicates")
	public Decision notDuplicates(@RequestBody PairRequest request, @AuthenticationPrincipal AppUserPrincipal user) {
		return Decision.of(service.notDuplicates(request.firstTenderId(), request.secondTenderId(),
				user.displayName()));
	}

	@PostMapping("/decisions/{id}/undo")
	public Decision undo(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal user) {
		return Decision.of(service.undo(id, user.displayName()));
	}

	/** The merges a batch would make; nothing is merged. */
	@GetMapping("/batch")
	public BatchPreview previewBatch() {
		return service.previewBatch();
	}

	/** Makes the merges of the preview whose token is given, if unchanged. */
	@PostMapping("/batch")
	public List<Decision> mergeBatch(@RequestBody BatchRequest request,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return service.mergeBatch(request.token(), user.displayName()).stream().map(Decision::of).toList();
	}

	public record MergeRequest(@NotNull Long keptTenderId, @NotNull Long mergedTenderId, Boolean acknowledgeTeamData) {
	}

	public record PairRequest(@NotNull Long firstTenderId, @NotNull Long secondTenderId) {
	}

	public record BatchRequest(@NotBlank String token) {
	}

}
