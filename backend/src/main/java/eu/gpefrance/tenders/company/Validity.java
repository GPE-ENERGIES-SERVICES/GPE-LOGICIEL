package eu.gpefrance.tenders.company;

import java.time.LocalDate;

/**
 * Where a dated item stands (document, certification, habilitation, check
 * of an equipment), on a given day.
 */
public enum Validity {

	/** No end date. */
	NONE,

	VALID,

	/** Ends within the warning days. */
	EXPIRING,

	EXPIRED;

	static Validity of(LocalDate expiresOn, LocalDate today, int warningDays) {
		if (expiresOn == null) {
			return NONE;
		}
		if (expiresOn.isBefore(today)) {
			return EXPIRED;
		}
		return expiresOn.isAfter(today.plusDays(warningDays)) ? VALID : EXPIRING;
	}

}
