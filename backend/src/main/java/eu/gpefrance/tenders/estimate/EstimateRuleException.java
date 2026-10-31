package eu.gpefrance.tenders.estimate;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * An estimate that cannot be read or changed as asked.
 */
public class EstimateRuleException extends ErrorResponseException {

	private EstimateRuleException(HttpStatus status, String detail) {
		super(status, ProblemDetail.forStatusAndDetail(status, detail), null);
	}

	static EstimateRuleException invalid(String detail) {
		return new EstimateRuleException(HttpStatus.BAD_REQUEST, detail);
	}

	static EstimateRuleException notFound(String detail) {
		return new EstimateRuleException(HttpStatus.NOT_FOUND, detail);
	}

	static EstimateRuleException conflict(String detail) {
		return new EstimateRuleException(HttpStatus.CONFLICT, detail);
	}

}
