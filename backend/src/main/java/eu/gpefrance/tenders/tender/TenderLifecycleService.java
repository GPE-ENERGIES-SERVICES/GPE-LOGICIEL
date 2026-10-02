package eu.gpefrance.tenders.tender;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenderLifecycleService {

	private static final Logger log = LoggerFactory.getLogger(TenderLifecycleService.class);

	private final TenderRepository tenders;

	private final Clock clock;

	public TenderLifecycleService(TenderRepository tenders, Clock clock) {
		this.tenders = tenders;
		this.clock = clock;
	}

	/**
	 * An active tender whose deadline has passed is closed.
	 * @return the number of tenders closed
	 */
	@Transactional
	@Scheduled(cron = "${tenders.close-cron}", zone = "Europe/Paris")
	public int closeExpired() {
		int closed = tenders.closeExpired(clock.instant());
		if (closed > 0) {
			log.info("{} tenders closed after their deadline", closed);
		}
		return closed;
	}

}
