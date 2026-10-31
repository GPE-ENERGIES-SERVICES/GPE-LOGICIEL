package eu.gpefrance.tenders.company;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import eu.gpefrance.tenders.user.AppUserPrincipal;
import jakarta.validation.Valid;

/**
 * A list of the company (employees, equipment, documents...): read by every
 * user, changed by an administrator (security configuration). Its lines are
 * answered as stored, with their validity when they end and their files when
 * they have some. Each list is a subclass naming its path.
 * @param <E> a line of the list
 * @param <Q> the request that sets a line
 */
public abstract class RecordController<E extends CompanyRecord<Q>, Q> {

	private final JpaRepository<E, Long> repository;

	private final Supplier<E> factory;

	private final Sort order;

	/** Kind of the files of its lines, or null when they have none. */
	private final FileOwner owner;

	private final CompanyFiles files;

	private final CompanyProperties properties;

	private final Clock clock;

	protected RecordController(JpaRepository<E, Long> repository, Supplier<E> factory, Sort order, FileOwner owner,
			CompanyContext context) {
		this.repository = repository;
		this.factory = factory;
		this.order = order;
		this.owner = owner;
		this.files = context.files();
		this.properties = context.properties();
		this.clock = context.clock();
	}

	/**
	 * A line with what is computed for it.
	 * @param validity null when it never ends
	 * @param files the last uploaded first; null when its kind has no file
	 */
	public record Line<E>(@JsonUnwrapped E record, Validity validity, List<CompanyFiles.FileResponse> files) {
	}

	@GetMapping
	@Transactional(readOnly = true)
	public List<Line<E>> list() {
		Map<Long, List<CompanyFiles.FileResponse>> attached = owner == null ? Map.of() : files.byOwner(owner);
		LocalDate today = LocalDate.now(clock);
		return repository.findAll(order).stream().map(record -> line(record, attached, today)).toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Transactional
	public Line<E> create(@Valid @RequestBody Q request, @AuthenticationPrincipal AppUserPrincipal user) {
		check(request);
		E record = factory.get();
		record.save(request, user.displayName(), clock.instant());
		return line(repository.save(record), Map.of(), LocalDate.now(clock));
	}

	@PutMapping("/{id}")
	@Transactional
	public Line<E> update(@PathVariable Long id, @Valid @RequestBody Q request,
			@AuthenticationPrincipal AppUserPrincipal user) {
		check(request);
		E record = found(id);
		record.save(request, user.displayName(), clock.instant());
		return line(repository.save(record), owner == null ? Map.of() : Map.of(id, files.of(owner, id)),
				LocalDate.now(clock));
	}

	/** Deletes the line and its files. */
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Transactional
	public void delete(@PathVariable Long id) {
		repository.delete(found(id));
		if (owner != null) {
			files.deleteAll(owner, List.of(id));
		}
		deleted(id);
	}

	/**
	 * Adds a file to the line; for a document, it becomes its current file
	 * and the former ones stay as its history.
	 */
	@PostMapping("/{id}/files")
	@ResponseStatus(HttpStatus.CREATED)
	@Transactional
	public CompanyFiles.FileResponse upload(@PathVariable Long id, @RequestPart("file") MultipartFile file,
			@AuthenticationPrincipal AppUserPrincipal user) {
		if (owner == null) {
			throw CompanyRuleException.notFound();
		}
		found(id);
		return files.store(owner, id, file, user.displayName());
	}

	/** Checks needing the database (the employee of a credential exists...). */
	protected void check(Q request) {
	}

	/** What else goes with a deleted line (the files of its own lines...). */
	protected void deleted(Long id) {
	}

	private E found(Long id) {
		return repository.findById(id).orElseThrow(CompanyRuleException::notFound);
	}

	private Line<E> line(E record, Map<Long, List<CompanyFiles.FileResponse>> attached, LocalDate today) {
		LocalDate end = record.endsOn();
		return new Line<>(record, end == null ? null : Validity.of(end, today, properties.expiryWarningDays()),
				owner == null ? null : attached.getOrDefault(record.getId(), List.of()));
	}

}
