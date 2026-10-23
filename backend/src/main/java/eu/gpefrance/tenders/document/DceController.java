package eu.gpefrance.tenders.document;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import eu.gpefrance.tenders.user.AppUserPrincipal;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Follow-up of the retrieval of the consultation files: the history of a
 * link for every user; the summary, the journal and the new attempts for the
 * administrators (see the security configuration).
 */
@RestController
@Validated
public class DceController {

	private final DceStatusService status;

	public DceController(DceStatusService status) {
		this.status = status;
	}

	@GetMapping("/api/dce-links/{id}/events")
	public List<DceEventResponse> history(@PathVariable Long id) {
		return status.history(id);
	}

	@GetMapping("/api/dce/summary")
	public DceSummaryResponse summary() {
		return status.summary();
	}

	@GetMapping("/api/dce/events")
	public List<DceEventResponse> events(@RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
		return status.recentEvents(limit);
	}

	/** A new attempt after a failure, with the controls of the automatic retrieval. */
	@PostMapping("/api/dce-links/{id}/retry")
	public DceLinkResponse retry(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal user) {
		return status.retry(id, user.id(), user.displayName());
	}

}
