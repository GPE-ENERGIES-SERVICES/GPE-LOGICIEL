package eu.gpefrance.tenders.collect;

import java.time.Instant;
import java.time.LocalDate;

public record CollectRunResponse(Long id, String source, CollectTrigger trigger, CollectStatus status,
		LocalDate sinceDate, Instant startedAt, Instant finishedAt, int noticesReceived, int noticesNew,
		String errorMessage) {

	static CollectRunResponse from(CollectRun run) {
		return new CollectRunResponse(run.getId(), run.getSource(), run.getTrigger(), run.getStatus(),
				run.getSinceDate(), run.getStartedAt(), run.getFinishedAt(), run.getNoticesReceived(),
				run.getNoticesNew(), run.getErrorMessage());
	}

}
