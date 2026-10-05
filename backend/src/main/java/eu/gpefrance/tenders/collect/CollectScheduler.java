package eu.gpefrance.tenders.collect;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Collects every active source at the times of {@code collect.cron}.
 */
@Component
public class CollectScheduler {

	private final CollectService collectService;

	public CollectScheduler(CollectService collectService) {
		this.collectService = collectService;
	}

	@Scheduled(cron = "${collect.cron}", zone = "Europe/Paris")
	public void collect() {
		collectService.collectActive(CollectTrigger.SCHEDULED);
	}

}
