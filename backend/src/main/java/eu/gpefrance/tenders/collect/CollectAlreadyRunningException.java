package eu.gpefrance.tenders.collect;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class CollectAlreadyRunningException extends ErrorResponseException {

	public CollectAlreadyRunningException() {
		super(HttpStatus.CONFLICT,
				ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Une collecte est déjà en cours"), null);
	}

}
