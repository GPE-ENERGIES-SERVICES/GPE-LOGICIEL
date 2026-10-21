package eu.gpefrance.tenders.collect;

import java.time.Instant;
import java.time.LocalDate;

/**
 * @param untilDate last publication day asked, null for the runs made before it was recorded
 * @param noticesKnown notices received that were already stored
 * @param noticesRejected notices received without identifier
 * @param tendersCreated tenders created by the notices of the source
 * @param tendersUpdated notices applied to a known tender
 * @param noticesIgnored notices that make no tender (not a call for
 * tenders, initial notice not collected...)
 * @param noticeErrors notices that could not be read, tried again at the next run
 */
public record CollectRunResponse(Long id, String source, CollectTrigger trigger, CollectStatus status,
		LocalDate sinceDate, LocalDate untilDate, Instant startedAt, Instant finishedAt, int noticesReceived,
		int noticesNew,
		int noticesKnown, int noticesRejected, int tendersCreated, int tendersUpdated, int noticesIgnored,
		int noticeErrors, String errorMessage) {

	static CollectRunResponse from(CollectRun run) {
		if (run == null) {
			return null;
		}
		return new CollectRunResponse(run.getId(), run.getSource(), run.getTrigger(), run.getStatus(),
				run.getSinceDate(), run.getUntilDate(), run.getStartedAt(), run.getFinishedAt(), run.getNoticesReceived(),
				run.getNoticesNew(), run.getNoticesKnown(), run.getNoticesRejected(), run.getTendersCreated(),
				run.getTendersUpdated(), run.getNoticesIgnored(), run.getNoticeErrors(), run.getErrorMessage());
	}

}
