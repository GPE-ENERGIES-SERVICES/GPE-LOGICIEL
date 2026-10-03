package eu.gpefrance.tenders.analysis;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * An analysis that cannot be started; the message tells the user why.
 */
public class AnalysisRejectedException extends ErrorResponseException {

	public AnalysisRejectedException(String message) {
		super(HttpStatus.CONFLICT, ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, message), null);
	}

}
