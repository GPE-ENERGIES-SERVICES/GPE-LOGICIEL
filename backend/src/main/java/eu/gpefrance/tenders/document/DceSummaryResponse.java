package eu.gpefrance.tenders.document;

import java.util.List;
import java.util.Map;

import eu.gpefrance.tenders.eligibility.EligibilityState;

/**
 * The retrieval of the consultation files for the whole application.
 * @param eligibility tenders by eligibility
 * @param links links by status
 * @param outOfDate tenders whose eligibility is missing or out of date
 * @param evaluating an evaluation of the eligibility is running
 * @param usedBytes bytes written by the retrieved dossiers
 * @param reservedBytes bytes reserved by the retrievals in progress
 * @param quotaBytes the quota of the retrieved files
 * @param allowedPlatforms platforms whose automated retrieval is allowed
 * @param workerEnabled whether queued links are retrieved
 */
public record DceSummaryResponse(Map<EligibilityState, Long> eligibility, Map<DceLinkStatus, Long> links,
		long outOfDate, boolean evaluating, long usedBytes, long reservedBytes, long quotaBytes,
		List<String> allowedPlatforms, boolean workerEnabled, int concurrency, int maxAttempts, long maxFileSizeBytes) {
}
