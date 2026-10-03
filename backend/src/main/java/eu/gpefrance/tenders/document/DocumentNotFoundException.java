package eu.gpefrance.tenders.document;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class DocumentNotFoundException extends RuntimeException {

	public DocumentNotFoundException(Long id) {
		super("Document or dossier " + id + " not found");
	}

}
