package eu.gpefrance.tenders.company;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import eu.gpefrance.tenders.user.AppUserPrincipal;
import jakarta.validation.Valid;

/**
 * The identity of the company, its logo, the files of the company and the
 * overview of what expires. Reading is for every user, changes for an
 * administrator (security configuration).
 */
@RestController
public class CompanyController {

	private final CompanyProfileRepository profiles;

	private final CompanyFiles files;

	private final CompanyDirectory directory;

	private final Clock clock;

	public CompanyController(CompanyProfileRepository profiles, CompanyFiles files, CompanyDirectory directory,
			Clock clock) {
		this.profiles = profiles;
		this.files = files;
		this.directory = directory;
		this.clock = clock;
	}

	/**
	 * @param logo the last logo uploaded, or null
	 */
	public record ProfileResponse(@JsonUnwrapped CompanyProfile profile, CompanyFiles.FileResponse logo) {
	}

	@GetMapping("/api/company/profile")
	@Transactional(readOnly = true)
	public ProfileResponse profile() {
		return response(profiles.current());
	}

	@PutMapping("/api/company/profile")
	@Transactional
	public ProfileResponse changeProfile(@Valid @RequestBody CompanyProfile.Request request,
			@AuthenticationPrincipal AppUserPrincipal user) {
		CompanyProfile profile = profiles.current();
		profile.change(request, user.displayName(), clock.instant());
		return response(profiles.save(profile));
	}

	/** A new logo, an image; the former one is removed. */
	@PostMapping("/api/company/profile/logo")
	@ResponseStatus(HttpStatus.CREATED)
	@Transactional
	public CompanyFiles.FileResponse uploadLogo(@RequestPart("file") MultipartFile file,
			@AuthenticationPrincipal AppUserPrincipal user) {
		files.deleteAll(FileOwner.LOGO, List.of((long) CompanyProfile.ID));
		return files.store(FileOwner.LOGO, (long) CompanyProfile.ID, file, user.displayName());
	}

	/** A file of the company: images shown in the page, the others downloaded. */
	@GetMapping("/api/company/files/{id}")
	public ResponseEntity<Resource> file(@PathVariable Long id) {
		CompanyFiles.Stored stored = files.find(id).orElseThrow(CompanyRuleException::notFound);
		MediaType type = MediaTypeFactory.getMediaType(stored.file().fileName())
			.orElse(MediaType.APPLICATION_OCTET_STREAM);
		boolean image = "image".equals(type.getType());
		ContentDisposition disposition = (image ? ContentDisposition.inline() : ContentDisposition.attachment())
			.filename(stored.file().fileName(), StandardCharsets.UTF_8)
			.build();
		return ResponseEntity.ok()
			.contentType(type)
			.header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
			.header("X-Content-Type-Options", "nosniff")
			.body(new FileSystemResource(stored.path()));
	}

	@DeleteMapping("/api/company/files/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Transactional
	public void deleteFile(@PathVariable Long id) {
		files.delete(id);
	}

	/** What expires or has expired, and how much each part of the company holds. */
	@GetMapping("/api/company/overview")
	@Transactional(readOnly = true)
	public CompanyDirectory.Overview overview() {
		return directory.overview();
	}

	private ProfileResponse response(CompanyProfile profile) {
		List<CompanyFiles.FileResponse> logos = files.of(FileOwner.LOGO, (long) CompanyProfile.ID);
		return new ProfileResponse(profile, logos.isEmpty() ? null : logos.getFirst());
	}

}
