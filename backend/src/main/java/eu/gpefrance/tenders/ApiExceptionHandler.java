package eu.gpefrance.tenders;

import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Errors are returned as RFC 9457 problem details. Validation errors list
 * each invalid field so the interface can show them.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		String detail = ex.getBindingResult()
			.getAllErrors()
			.stream()
			.map(error -> error.getDefaultMessage())
			.distinct()
			.collect(Collectors.joining(" ; "));
		return handleExceptionInternal(ex, ProblemDetail.forStatusAndDetail(status, detail), headers, status,
				request);
	}

	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		String detail = ex.getAllErrors()
			.stream()
			.map(error -> error.getDefaultMessage())
			.distinct()
			.collect(Collectors.joining(" ; "));
		return handleExceptionInternal(ex, ProblemDetail.forStatusAndDetail(status, detail), headers, status,
				request);
	}

}
