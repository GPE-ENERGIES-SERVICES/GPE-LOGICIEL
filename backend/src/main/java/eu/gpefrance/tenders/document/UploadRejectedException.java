package eu.gpefrance.tenders.document;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * An upload refused; the message tells the user why.
 */
public class UploadRejectedException extends ErrorResponseException {

	public UploadRejectedException(String message) {
		super(HttpStatus.BAD_REQUEST, ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message), null);
	}

}
