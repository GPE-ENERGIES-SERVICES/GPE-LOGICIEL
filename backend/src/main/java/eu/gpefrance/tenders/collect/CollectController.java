package eu.gpefrance.tenders.collect;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/collect-runs")
public class CollectController {

	private static final int MAX_LIMIT = 100;

	private final CollectService collectService;

	private final CollectRunRepository runs;

	public CollectController(CollectService collectService, CollectRunRepository runs) {
		this.collectService = collectService;
		this.runs = runs;
	}

	@GetMapping
	public CollectJournalResponse journal(@RequestParam(defaultValue = "20") int limit) {
		int size = Math.clamp(limit, 1, MAX_LIMIT);
		List<CollectRunResponse> recent = runs.findAllByOrderByStartedAtDesc(PageRequest.of(0, size))
			.stream()
			.map(CollectRunResponse::from)
			.toList();
		return new CollectJournalResponse(collectService.isRunning(), recent);
	}

	@GetMapping("/sources")
	public List<SourceResponse> sources() {
		return collectService.sources()
			.stream()
			.map(state -> new SourceResponse(state.source(), state.access(), state.active(), state.running(),
					CollectRunResponse.from(state.lastRun()), CollectRunResponse.from(state.lastSuccess())))
			.toList();
	}

	/**
	 * @param source the source to collect; by default every active source
	 * @param since first publication day to fetch, for an initial load; by
	 * default the collection resumes after the last successful one
	 */
	@PostMapping
	@ResponseStatus(HttpStatus.ACCEPTED)
	public void start(@RequestParam(required = false) String source,
			@RequestParam(required = false) LocalDate since) {
		collectService.startInBackground(source, CollectTrigger.MANUAL, since);
	}

	public record CollectJournalResponse(boolean running, List<CollectRunResponse> runs) {
	}

	/**
	 * @param active collected by the schedule
	 * @param lastRun most recent run, null if never collected
	 * @param lastSuccess most recent successful run, null if none
	 */
	public record SourceResponse(String source, SourceAccess access, boolean active, boolean running,
			CollectRunResponse lastRun, CollectRunResponse lastSuccess) {
	}

}
