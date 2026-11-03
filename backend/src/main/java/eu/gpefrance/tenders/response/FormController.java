package eu.gpefrance.tenders.response;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import eu.gpefrance.tenders.response.FormService.FieldResponse;
import eu.gpefrance.tenders.response.FormService.FormResponse;
import eu.gpefrance.tenders.response.ResponseService.ResponseView;
import eu.gpefrance.tenders.user.AppUserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/**
 * The forms of the consultation file filled for an answer.
 */
@RestController
public class FormController {

	private final FormService forms;

	private final ResponseService responses;

	public FormController(FormService forms, ResponseService responses) {
		this.forms = forms;
		this.responses = responses;
	}

	/**
	 * @param useAi let the AI propose the values; otherwise, only the empty places
	 */
	public record StartRequest(@Size(max = 50) String lotNumber, boolean useAi) {
	}

	public record FieldRequest(@Size(max = 4000) String value, boolean checked) {
	}

	@GetMapping("/api/response-pieces/{pieceId}/form")
	public FormResponse view(@PathVariable Long pieceId) {
		return forms.view(pieceId);
	}

	@PostMapping("/api/response-pieces/{pieceId}/form")
	public FormResponse start(@PathVariable Long pieceId, @Valid @RequestBody StartRequest request,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return forms.start(pieceId, request.lotNumber(), request.useAi(), user.displayName());
	}

	@PutMapping("/api/response-form-fields/{fieldId}")
	public FieldResponse change(@PathVariable Long fieldId, @Valid @RequestBody FieldRequest request) {
		return forms.change(fieldId, request.value(), request.checked());
	}

	/** Writes the Word document and joins it to the piece. */
	@PostMapping("/api/response-pieces/{pieceId}/form/document")
	public ResponseView generate(@PathVariable Long pieceId, @AuthenticationPrincipal AppUserPrincipal user) {
		forms.generate(pieceId, user.displayName());
		return responses.viewOfPiece(pieceId);
	}

}
