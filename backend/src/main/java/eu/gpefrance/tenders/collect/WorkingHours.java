package eu.gpefrance.tenders.collect;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Time counted on working days only: every hour of Monday to Friday, in the
 * given zone, and none of Saturday and Sunday. Public holidays count as
 * working days. Collections run on working days only: a source last collected
 * on Friday at 13:00 has 18 working hours without collection on Monday at
 * 7:00, and 24 at 13:00 after two missed collections.
 */
final class WorkingHours {

	private WorkingHours() {
	}

	static Duration between(Instant from, Instant to, ZoneId zone) {
		if (!to.isAfter(from)) {
			return Duration.ZERO;
		}
		Duration total = Duration.ZERO;
		ZonedDateTime start = from.atZone(zone);
		ZonedDateTime end = to.atZone(zone);
		for (LocalDate day = start.toLocalDate(); !day.isAfter(end.toLocalDate()); day = day.plusDays(1)) {
			if (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY) {
				continue;
			}
			ZonedDateTime dayStart = day.atStartOfDay(zone);
			ZonedDateTime dayEnd = day.plusDays(1).atStartOfDay(zone);
			ZonedDateTime counted = start.isAfter(dayStart) ? start : dayStart;
			ZonedDateTime until = end.isBefore(dayEnd) ? end : dayEnd;
			if (until.isAfter(counted)) {
				total = total.plus(Duration.between(counted, until));
			}
		}
		return total;
	}

}
