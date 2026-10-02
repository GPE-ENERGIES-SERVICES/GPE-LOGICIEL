package eu.gpefrance.tenders.collect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CollectScheduler {

	private static final Logger log = LoggerFactory.getLogger(CollectScheduler.class);

	private final CollectService collectService;

	public CollectScheduler(CollectService collectService) {
		this.collectService = collectService;
	}

	@Scheduled(cron = "${collect.cron}", zone = "Europe/Paris")
	public void collect() {
		try {
			collectService.collect(CollectTrigger.SCHEDULED);
		}
		catch (CollectAlreadyRunningException ex) {
			log.warn("Scheduled collection skipped: a collection is already running");
		}
	}

}
