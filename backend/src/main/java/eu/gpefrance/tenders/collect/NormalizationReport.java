package eu.gpefrance.tenders.collect;

/**
 * What the normalization did with the collected notices.
 * @param created tenders created by a notice
 * @param updated notices applied to a tender already known: rectification,
 * award, cancellation
 * @param ignored notices that do not concern a tender we can know, with a
 * note on the notice: not a call for tenders, initial notice not collected,
 * no title
 * @param errors unreadable notices, with the error on the notice
 */
public record NormalizationReport(int created, int updated, int ignored, int errors) {

	public static final NormalizationReport NONE = new NormalizationReport(0, 0, 0, 0);

	public int total() {
		return created + updated + ignored + errors;
	}

	NormalizationReport plus(NormalizationReport other) {
		return new NormalizationReport(created + other.created, updated + other.updated, ignored + other.ignored,
				errors + other.errors);
	}

}
