package eu.gpefrance.tenders.company;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * Data of the company that cannot be read or saved as asked.
 */
public class CompanyRuleException extends ErrorResponseException {

	private CompanyRuleException(HttpStatus status, String detail) {
		super(status, ProblemDetail.forStatusAndDetail(status, detail), null);
	}

	static CompanyRuleException invalid(String detail) {
		return new CompanyRuleException(HttpStatus.BAD_REQUEST, detail);
	}

	static CompanyRuleException notFound() {
		return new CompanyRuleException(HttpStatus.NOT_FOUND, "Élément introuvable.");
	}

	/** Dates in order: the end is never before the start. */
	static void checkOrder(java.time.LocalDate start, java.time.LocalDate end, String what) {
		if (start != null && end != null && end.isBefore(start)) {
			throw invalid(what + " : la date de fin est antérieure à la date de début.");
		}
	}

}
