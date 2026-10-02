package eu.gpefrance.tenders.collect;

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

	@PostMapping
	@ResponseStatus(HttpStatus.ACCEPTED)
	public void start() {
		collectService.startInBackground(CollectTrigger.MANUAL);
	}

	public record CollectJournalResponse(boolean running, List<CollectRunResponse> runs) {
	}

}
