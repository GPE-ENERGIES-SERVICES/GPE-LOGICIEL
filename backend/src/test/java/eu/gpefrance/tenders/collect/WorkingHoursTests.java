package eu.gpefrance.tenders.collect;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import eu.gpefrance.tenders.ClockConfiguration;

class WorkingHoursTests {

	@Test
	void countsOnlyMondayToFriday() {
		// Friday 13:00 to Monday 7:00: 11 hours on Friday, 7 on Monday.
		assertThat(between("2026-10-16T13:00", "2026-10-19T07:00")).isEqualTo(Duration.ofHours(18));
		// Two missed collections on Monday: 24 hours at 13:00.
		assertThat(between("2026-10-16T13:00", "2026-10-19T13:00")).isEqualTo(Duration.ofHours(24));
	}

	@Test
	void countsAWholeWeekendAsNothing() {
		assertThat(between("2026-10-17T08:00", "2026-10-18T20:00")).isZero();
	}

	@Test
	void countsTheRealLengthOfTheDayOfTheChangeOfTime() {
		// Sunday 25/10/2026 has 25 hours but is not counted; Monday 26/10 is.
		assertThat(between("2026-10-23T00:00", "2026-10-27T00:00")).isEqualTo(Duration.ofHours(48));
		// Monday 30/03/2026 after the change of Sunday: plain 24 hours.
		assertThat(between("2026-03-27T12:00", "2026-03-30T12:00")).isEqualTo(Duration.ofHours(24));
	}

	@Test
	void theStopOfTheRealDatabaseCounts165WorkingHours() {
		// Last success on Monday 12/10 at 16:24, Wednesday 21/10 at 13:31: 7 working days and some.
		assertThat(between("2026-10-12T16:24", "2026-10-21T13:31").toHours()).isEqualTo(165);
	}

	@Test
	void isZeroWhenTheEndIsNotAfterTheStart() {
		assertThat(between("2026-10-19T13:00", "2026-10-19T07:00")).isZero();
	}

	private static Duration between(String from, String to) {
		return WorkingHours.between(paris(from), paris(to), ClockConfiguration.ZONE);
	}

	private static Instant paris(String local) {
		return LocalDateTime.parse(local).atZone(ClockConfiguration.ZONE).toInstant();
	}

}
