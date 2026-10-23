package eu.gpefrance.tenders.alert;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * A saved search that cannot be read or saved as asked.
 */
public class AlertRuleException extends ErrorResponseException {

	private AlertRuleException(HttpStatus status, String detail) {
		super(status, ProblemDetail.forStatusAndDetail(status, detail), null);
	}

	static AlertRuleException invalid(String detail) {
		return new AlertRuleException(HttpStatus.BAD_REQUEST, detail);
	}

	/** Also for the saved search of another user: its existence is not disclosed. */
	static AlertRuleException notFound() {
		return new AlertRuleException(HttpStatus.NOT_FOUND, "Veille introuvable.");
	}

}
