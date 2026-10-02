package eu.gpefrance.tenders.tender;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class TenderNotFoundException extends RuntimeException {

	public TenderNotFoundException(Long id) {
		super("Tender " + id + " not found");
	}

}
