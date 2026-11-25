package eu.gpefrance.tenders.tender;

import static eu.gpefrance.tenders.tender.FollowUpStatus.AWARDED;
import static eu.gpefrance.tenders.tender.FollowUpStatus.DISMISSED;
import static eu.gpefrance.tenders.tender.FollowUpStatus.NEW;
import static eu.gpefrance.tenders.tender.FollowUpStatus.REJECTED;
import static eu.gpefrance.tenders.tender.FollowUpStatus.RESPONDING;
import static eu.gpefrance.tenders.tender.FollowUpStatus.SIGNED;
import static eu.gpefrance.tenders.tender.FollowUpStatus.SUBMITTED;
import static eu.gpefrance.tenders.tender.FollowUpStatus.TO_STUDY;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FollowUpStatusTests {

	@Test
	void movesFreelyBeforeTheAnswerAsBefore() {
		for (FollowUpStatus from : new FollowUpStatus[] { NEW, TO_STUDY, RESPONDING, SUBMITTED, DISMISSED }) {
			for (FollowUpStatus to : new FollowUpStatus[] { NEW, TO_STUDY, RESPONDING, SUBMITTED, DISMISSED }) {
				assertThat(from.canBecome(to)).as("%s -> %s", from, to).isTrue();
			}
		}
	}

	@Test
	void reachesTheResultOnlyOnceTheAnswerIsPrepared() {
		assertThat(SUBMITTED.canBecome(AWARDED)).isTrue();
		assertThat(SUBMITTED.canBecome(REJECTED)).isTrue();
		assertThat(RESPONDING.canBecome(AWARDED)).isTrue();
		assertThat(RESPONDING.canBecome(REJECTED)).isTrue();
		assertThat(RESPONDING.canBecome(SIGNED)).isFalse();
		for (FollowUpStatus from : new FollowUpStatus[] { NEW, TO_STUDY, DISMISSED }) {
			assertThat(from.canBecome(AWARDED)).as(from.name()).isFalse();
			assertThat(from.canBecome(REJECTED)).as(from.name()).isFalse();
			assertThat(from.canBecome(SIGNED)).as(from.name()).isFalse();
		}
	}

	@Test
	void signsOnlyAContractWon() {
		assertThat(AWARDED.canBecome(SIGNED)).isTrue();
		assertThat(SUBMITTED.canBecome(SIGNED)).isFalse();
		assertThat(REJECTED.canBecome(SIGNED)).isFalse();
		assertThat(SIGNED.canBecome(NEW)).isFalse();
		assertThat(SIGNED.canBecome(DISMISSED)).isFalse();
	}

	@Test
	void correctsAResult() {
		assertThat(AWARDED.canBecome(REJECTED)).isTrue();
		assertThat(REJECTED.canBecome(AWARDED)).isTrue();
		assertThat(REJECTED.canBecome(SUBMITTED)).isTrue();
		assertThat(SIGNED.canBecome(AWARDED)).isTrue();
		assertThat(REJECTED.canBecome(NEW)).isFalse();
	}

}
