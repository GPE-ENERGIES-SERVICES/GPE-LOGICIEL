package eu.gpefrance.tenders.memoir;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import eu.gpefrance.tenders.alert.AlertService;
import eu.gpefrance.tenders.memoir.MemoirStore.LibraryRow;
import eu.gpefrance.tenders.user.AppUserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The former memoirs of GPE: imported, described, chosen or not as
 * references for the AI. Their text is read at the import (PDF with text,
 * Word); a scanned memoir is kept but gives no text.
 */
@RestController
public class MemoirLibraryController {

	private static final Logger log = LoggerFactory.getLogger(MemoirLibraryController.class);

	private final MemoirStore store;

	private final MemoirFiles files;

	private final Clock clock;

	public MemoirLibraryController(MemoirStore store, MemoirFiles files, Clock clock) {
		this.store = store;
		this.files = files;
		this.clock = clock;
	}

	public record LibraryRequest(@NotBlank @Size(max = 300) String title, LocalDate writtenOn,
			@Size(max = 4000) String context, @Size(max = 100) String marketType, @Size(max = 40) String activityCode,
			Boolean useForAi) {
	}

	@GetMapping("/api/memoir-library")
	public List<LibraryRow> list() {
		return store.library();
	}

	@PostMapping("/api/memoir-library")
	@ResponseStatus(HttpStatus.CREATED)
	@Transactional
	public LibraryRow upload(@RequestPart("file") MultipartFile file, @RequestParam @Size(max = 300) String title,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate writtenOn,
			@RequestParam(required = false) String context, @RequestParam(required = false) String marketType,
			@RequestParam(required = false) String activityCode,
			@RequestParam(required = false, defaultValue = "true") boolean useForAi,
			@AuthenticationPrincipal AppUserPrincipal user) {
		if (title == null || title.isBlank()) {
			throw MemoirRuleException.invalid("Donnez un titre au mémoire.");
		}
		String code = checkActivity(activityCode);
		MemoirFiles.Stored stored = files.storeFormerMemoir(file);
		Long id = store.insertLibrary(title.strip(), writtenOn, blank(context), blank(marketType), code, useForAi,
				stored.fileName(), stored.relativePath(), stored.size(), files.text(stored), user.displayName(),
				clock.instant());
		log.info("Former memoir {} imported by {}", id, user.displayName());
		return store.libraryEntry(id).orElseThrow();
	}

	@PutMapping("/api/memoir-library/{id}")
	public LibraryRow update(@PathVariable Long id, @Valid @RequestBody LibraryRequest request) {
		found(id);
		store.updateLibrary(id, request.title().strip(), request.writtenOn(), blank(request.context()),
				blank(request.marketType()), checkActivity(request.activityCode()),
				request.useForAi() == null || request.useForAi());
		return found(id);
	}

	@DeleteMapping("/api/memoir-library/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Transactional
	public void delete(@PathVariable Long id) {
		String path = store.libraryPath(id).orElseThrow(() -> MemoirRuleException.notFound("Mémoire introuvable."));
		store.deleteLibrary(id);
		files.deleteAfterCommit(List.of(path));
	}

	@GetMapping("/api/memoir-library/{id}/file")
	public ResponseEntity<Resource> file(@PathVariable Long id) {
		LibraryRow entry = found(id);
		String path = store.libraryPath(id).orElseThrow();
		return ResponseEntity.ok()
			.contentType(MediaTypeFactory.getMediaType(entry.fileName()).orElse(MediaType.APPLICATION_OCTET_STREAM))
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.attachment().filename(entry.fileName(), StandardCharsets.UTF_8).build().toString())
			.body(new FileSystemResource(files.resolve(path)));
	}

	private LibraryRow found(Long id) {
		return store.libraryEntry(id).orElseThrow(() -> MemoirRuleException.notFound("Mémoire introuvable."));
	}

	private static String checkActivity(String code) {
		String value = blank(code);
		if (value != null && !AlertService.ACTIVITIES.contains(value)) {
			throw MemoirRuleException.invalid("Type de travaux inconnu : " + value);
		}
		return value;
	}

	private static String blank(String text) {
		return text == null || text.isBlank() ? null : text.strip();
	}

}
