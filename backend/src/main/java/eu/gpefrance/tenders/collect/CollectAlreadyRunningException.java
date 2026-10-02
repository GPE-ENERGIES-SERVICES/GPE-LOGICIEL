package eu.gpefrance.tenders.collect;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class CollectAlreadyRunningException extends RuntimeException {

	public CollectAlreadyRunningException() {
		super("A collection is already running");
	}

}
