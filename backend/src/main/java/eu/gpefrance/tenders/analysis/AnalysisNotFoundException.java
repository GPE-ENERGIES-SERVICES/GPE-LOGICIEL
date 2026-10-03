package eu.gpefrance.tenders.analysis;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class AnalysisNotFoundException extends RuntimeException {

	public AnalysisNotFoundException(Long id) {
		super("Analysis or extracted field " + id + " not found");
	}

}
