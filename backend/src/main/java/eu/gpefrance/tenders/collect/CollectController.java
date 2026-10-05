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

	/**
	 * @param since first publication day to fetch, for an initial load; by
	 * default the collection resumes after the last successful one
	 */
	@PostMapping
	@ResponseStatus(HttpStatus.ACCEPTED)
	public void start(@RequestParam(required = false) LocalDate since) {
		collectService.startInBackground(CollectTrigger.MANUAL, since);
	}

	public record CollectJournalResponse(boolean running, List<CollectRunResponse> runs) {
	}

}
