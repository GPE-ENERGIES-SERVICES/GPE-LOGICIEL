package eu.gpefrance.tenders.collect;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class CollectPeriodException extends ErrorResponseException {

	public CollectPeriodException(LocalDate earliest) {
		super(HttpStatus.BAD_REQUEST, ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
				"La collecte peut commencer au plus tôt le " + earliest.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
						+ " et au plus tard aujourd'hui"),
				null);
	}

}
