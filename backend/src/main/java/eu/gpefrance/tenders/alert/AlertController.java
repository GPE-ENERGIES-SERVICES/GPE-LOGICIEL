package eu.gpefrance.tenders.alert;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import eu.gpefrance.tenders.user.AppUserPrincipal;
import jakarta.validation.Valid;

/**
 * The saved searches of the logged-in user (« Mes veilles »).
 */
@RestController
@RequestMapping("/api/alerts")
public class AlertController {

	private final AlertService service;

	public AlertController(AlertService service) {
		this.service = service;
	}

	@GetMapping
	public List<AlertResponse> list(@AuthenticationPrincipal AppUserPrincipal user) {
		return service.list(user.id());
	}

	@GetMapping("/{id}")
	public AlertResponse get(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal user) {
		return service.get(id, user.id());
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public AlertResponse create(@Valid @RequestBody AlertRequest request,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return service.create(request, user.id());
	}

	/** Changes the criteria and, when given, the activation. */
	@PutMapping("/{id}")
	public AlertResponse update(@PathVariable Long id, @Valid @RequestBody AlertRequest request,
			@AuthenticationPrincipal AppUserPrincipal user) {
		return service.update(id, request, user.id());
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal user) {
		service.delete(id, user.id());
	}

	@PostMapping("/{id}/viewed")
	public AlertResponse viewed(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal user) {
		return service.markViewed(id, user.id());
	}

}
