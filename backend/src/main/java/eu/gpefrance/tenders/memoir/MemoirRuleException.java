package eu.gpefrance.tenders.memoir;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * A memoir that cannot be read or changed as asked.
 */
public class MemoirRuleException extends ErrorResponseException {

	private MemoirRuleException(HttpStatus status, String detail) {
		super(status, ProblemDetail.forStatusAndDetail(status, detail), null);
	}

	static MemoirRuleException invalid(String detail) {
		return new MemoirRuleException(HttpStatus.BAD_REQUEST, detail);
	}

	static MemoirRuleException notFound(String detail) {
		return new MemoirRuleException(HttpStatus.NOT_FOUND, detail);
	}

	static MemoirRuleException conflict(String detail) {
		return new MemoirRuleException(HttpStatus.CONFLICT, detail);
	}

}
