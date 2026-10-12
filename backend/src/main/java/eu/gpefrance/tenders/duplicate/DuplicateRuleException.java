package eu.gpefrance.tenders.duplicate;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * A decision on duplicates that the rules refuse, with the reason shown to
 * the person.
 */
public class DuplicateRuleException extends ErrorResponseException {

	DuplicateRuleException(HttpStatus status, String detail) {
		super(status, ProblemDetail.forStatusAndDetail(status, detail), null);
	}

	static DuplicateRuleException refused(String detail) {
		return new DuplicateRuleException(HttpStatus.CONFLICT, detail);
	}

	static DuplicateRuleException notFound(String detail) {
		return new DuplicateRuleException(HttpStatus.NOT_FOUND, detail);
	}

}
