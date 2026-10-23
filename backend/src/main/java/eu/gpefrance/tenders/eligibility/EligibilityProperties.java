package eu.gpefrance.tenders.eligibility;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param batchSize tenders evaluated per transaction
 * @param sweepInterval wait between two looks for out-of-date eligibilities,
 * which also resumes an evaluation stopped by a restart
 * @param autoEvaluation whether the evaluations start on their own (after a
 * collection, a change of a saved search, and every sweep interval); only
 * the tests turn it off, to start them themselves
 */
@ConfigurationProperties("dce.eligibility")
public record EligibilityProperties(@DefaultValue("200") int batchSize, @DefaultValue("10m") Duration sweepInterval,
		@DefaultValue("true") boolean autoEvaluation) {

	public EligibilityProperties {
		if (batchSize < 1) {
			throw new IllegalArgumentException("dce.eligibility.batch-size must be at least 1");
		}
	}

}
