package eu.gpefrance.tenders.collect;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * A collection asked for a source that does not exist or is not collected.
 */
public class CollectSourceException extends ErrorResponseException {

	private CollectSourceException(String detail) {
		super(HttpStatus.BAD_REQUEST, ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail), null);
	}

	static CollectSourceException unknown(String source) {
		return new CollectSourceException("Source inconnue : " + source);
	}

	static CollectSourceException notCollected(String source) {
		return new CollectSourceException("La source " + source + " n'est pas collectée automatiquement");
	}

}
