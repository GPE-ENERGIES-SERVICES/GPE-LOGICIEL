package eu.gpefrance.tenders.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import eu.gpefrance.tenders.analysis.AnswerReader.Proposal;

class AnswerCheckerTests {

	private static final Map<Long, Map<Integer, String>> PAGES = Map.of(7L,
			Map.of(1, "Objet : rénovation de l'école", 4, "Les offres devront parvenir avant le 15/11/2026 à 12h00."));

	/** 15/11/2026 at 12:00, Paris time. */
	private static final Instant NOTICE_DEADLINE = Instant.parse("2026-11-15T11:00:00Z");

	@Test
	void verifiesAnAnswerWhoseQuoteAndValueAreInTheDocument() {
		Proposal checked = check(deadline("2026-11-15T12:00", "avant le 15/11/2026 à 12h00", 4), NOTICE_DEADLINE);

		assertThat(checked.state()).isEqualTo(FieldState.VERIFIED);
		assertThat(checked.page()).isEqualTo(4);
	}

	@Test
	void correctsTheCitedPage() {
		Proposal checked = check(new Proposal("object", FieldState.UNVERIFIED, "Rénovation de l'école",
				"Objet : rénovation de l'école", 7L, 4), null);

		assertThat(checked.state()).isEqualTo(FieldState.VERIFIED);
		assertThat(checked.page()).isEqualTo(1);
	}

	@Test
	void leavesUnverifiedAQuoteNotFoundOrAValueNotInTheQuote() {
		assertThat(check(deadline("2026-11-15T12:00", "avant le 15/11/2026 à 14h00", 4), null).state())
			.isEqualTo(FieldState.UNVERIFIED);
		assertThat(check(deadline("2026-11-16T12:00", "avant le 15/11/2026 à 12h00", 4), null).state())
			.isEqualTo(FieldState.UNVERIFIED);
		assertThat(check(new Proposal("object", FieldState.UNVERIFIED, "École", "Objet : école", null, null), null)
			.state()).isEqualTo(FieldState.UNVERIFIED);
	}

	@Test
	void marksADeadlineDifferentFromTheNoticeAsContradictory() {
		Proposal checked = check(deadline("2026-11-15T12:00", "avant le 15/11/2026 à 12h00", 4),
				NOTICE_DEADLINE.plusSeconds(86_400));

		assertThat(checked.state()).isEqualTo(FieldState.CONTRADICTORY);
		assertThat(checked.page()).isEqualTo(4);
	}

	@Test
	void comparesOnlyTheDayWhenTheDocumentGivesNoTime() {
		assertThat(check(deadline("2026-11-15", "15/11/2026", 4), NOTICE_DEADLINE).state())
			.isEqualTo(FieldState.VERIFIED);
	}

	@Test
	void keepsTheStateGivenByTheModelForUncertainAnswers() {
		Proposal ambiguous = new Proposal("variants", FieldState.AMBIGUOUS, "autorisées", "Objet", 7L, 1);

		assertThat(check(ambiguous, null).state()).isEqualTo(FieldState.AMBIGUOUS);
	}

	private static Proposal deadline(String value, String quote, Integer page) {
		return new Proposal(ExtractionGrid.DEADLINE, FieldState.UNVERIFIED, value, quote, 7L, page);
	}

	private static Proposal check(Proposal proposal, Instant noticeDeadline) {
		return AnswerChecker.check(List.of(proposal), PAGES, noticeDeadline).getFirst();
	}

}
