package eu.gpefrance.tenders.document;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * An action on the retrieval of a consultation file that is not allowed.
 */
public class DceRuleException extends ErrorResponseException {

	private DceRuleException(HttpStatus status, String detail) {
		super(status, ProblemDetail.forStatusAndDetail(status, detail), null);
	}

	static DceRuleException refused(String detail) {
		return new DceRuleException(HttpStatus.CONFLICT, detail);
	}

	static DceRuleException notFound(Long linkId) {
		return new DceRuleException(HttpStatus.NOT_FOUND, "Lien de DCE introuvable : " + linkId);
	}

}
