package eu.gpefrance.tenders.user;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * A request refused by an account rule; the message is shown to the user.
 */
public class UserRuleException extends ErrorResponseException {

	public UserRuleException(String message) {
		super(HttpStatus.CONFLICT, ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, message), null);
	}

}
