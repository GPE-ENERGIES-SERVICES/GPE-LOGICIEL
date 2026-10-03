package eu.gpefrance.tenders.analysis;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import eu.gpefrance.tenders.ClockConfiguration;
import eu.gpefrance.tenders.analysis.AnswerReader.Proposal;

/**
 * Turns the answers of the model into checked values: an answer is verified
 * only when its quote is found in the document and contains the value (rule
 * RM9 of the specification: nothing is shown as reliable without a source).
 */
final class AnswerChecker {

	private AnswerChecker() {
	}

	/**
	 * @param pages text of each page of the documents sent, by document and
	 * page number
	 * @param noticeDeadline deadline published in the notice, compared with
	 * the one read in the documents (rule RM11)
	 */
	static List<Proposal> check(List<Proposal> proposals, Map<Long, Map<Integer, String>> pages,
			Instant noticeDeadline) {
		return proposals.stream().map(proposal -> checkDeadline(verify(proposal, pages), noticeDeadline)).toList();
	}

	private static Proposal verify(Proposal proposal, Map<Long, Map<Integer, String>> pages) {
		if (proposal.state() != FieldState.UNVERIFIED || proposal.value() == null || proposal.quote() == null
				|| proposal.documentId() == null || !pages.containsKey(proposal.documentId())) {
			return proposal;
		}
		FieldKind kind = ExtractionGrid.find(proposal.key()).map(GridField::kind).orElse(FieldKind.TEXT);
		Optional<Integer> page = CitationVerifier.locate(proposal.quote(), pages.get(proposal.documentId()),
				proposal.page());
		if (page.isPresent() && CitationVerifier.valueMatches(kind, proposal.value(), proposal.quote())) {
			return proposal.withCheck(FieldState.VERIFIED, page.get());
		}
		return proposal;
	}

	/**
	 * When the notice and the documents give different deadlines, both are
	 * shown and the deadline goes to the points to check.
	 */
	private static Proposal checkDeadline(Proposal proposal, Instant noticeDeadline) {
		if (!proposal.key().equals(ExtractionGrid.DEADLINE) || proposal.value() == null || noticeDeadline == null
				|| (proposal.state() != FieldState.VERIFIED && proposal.state() != FieldState.UNVERIFIED)) {
			return proposal;
		}
		LocalDateTime notice = LocalDateTime.ofInstant(noticeDeadline, ClockConfiguration.ZONE)
			.truncatedTo(ChronoUnit.MINUTES);
		String value = proposal.value();
		try {
			boolean same = value.length() > 10 ? LocalDateTime.parse(value).equals(notice)
					: LocalDate.parse(value).equals(notice.toLocalDate());
			return same ? proposal : proposal.withCheck(FieldState.CONTRADICTORY, proposal.page());
		}
		catch (DateTimeParseException ex) {
			return proposal;
		}
	}

}
