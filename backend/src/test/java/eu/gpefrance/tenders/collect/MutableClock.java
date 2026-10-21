package eu.gpefrance.tenders.collect;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import eu.gpefrance.tenders.ClockConfiguration;

/**
 * A clock of Paris that a test sets.
 */
class MutableClock extends Clock {

	private volatile Instant now;

	MutableClock(Instant now) {
		this.now = now;
	}

	void set(Instant instant) {
		this.now = instant;
	}

	@Override
	public ZoneId getZone() {
		return ClockConfiguration.ZONE;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Instant instant() {
		return now;
	}

}
