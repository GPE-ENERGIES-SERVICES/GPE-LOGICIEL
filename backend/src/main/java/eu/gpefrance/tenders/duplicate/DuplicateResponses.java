package eu.gpefrance.tenders.duplicate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import eu.gpefrance.tenders.tender.FollowUpStatus;
import eu.gpefrance.tenders.tender.NoticeKind;
import eu.gpefrance.tenders.tender.TenderStatus;

/**
 * What the duplicates screen shows.
 */
public final class DuplicateResponses {

	private DuplicateResponses() {
	}

	/**
	 * Tenders not merged that share one eForms procedure identifier.
	 * @param certain exactly two tenders of one source, one of them made by a
	 * rectification that explicitly cites a notice of the other
	 * @param explanation why they are considered duplicates
	 * @param suggestedKeptId the tender to keep: the one with the data of the
	 * team, otherwise the one cited, otherwise the oldest
	 */
	public record Group(String procedureId, boolean certain, List<String> explanation, Long suggestedKeptId,
			List<Candidate> tenders) {
	}

	/**
	 * @param teamData the team has a status, a comment, a follow or a dossier on it
	 */
	public record Candidate(Long id, String source, String externalId, String title, String buyer,
			LocalDate publishedAt, Instant deadline, TenderStatus status, FollowUpStatus followUpStatus,
			String followUpComment, boolean teamData, List<Notice> notices) {
	}

	public record Notice(String source, String externalId, NoticeKind kind, LocalDate publishedAt, Instant deadline) {
	}

	public record Decision(Long id, DuplicateDecision.Kind kind, Long keptTenderId, Long otherTenderId, String reason,
			String decidedBy, Instant decidedAt, String undoneBy, Instant undoneAt) {

		static Decision of(DuplicateDecision decision) {
			return new Decision(decision.getId(), decision.getKind(), decision.getKeptTenderId(),
					decision.getOtherTenderId(), decision.getReason(), decision.getDecidedBy(),
					decision.getDecidedAt(), decision.getUndoneBy(), decision.getUndoneAt());
		}

	}

	/**
	 * The merges a batch would make, to be read before confirming it.
	 * @param token to send back to confirm exactly these merges
	 * @param leftForReview groups not in the batch, to be decided one by one
	 */
	public record BatchPreview(String token, List<BatchMerge> merges, int leftForReview) {
	}

	public record BatchMerge(String procedureId, Long keptTenderId, Long mergedTenderId, String reason) {
	}

}
