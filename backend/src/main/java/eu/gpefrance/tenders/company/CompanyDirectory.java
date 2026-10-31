package eu.gpefrance.tenders.company;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The company as the other modules read it: the reference data of GPE, never
 * copied by them. Read only: the data of the company is changed by an
 * administrator in its pages, never by another module nor by an AI.
 * <p>
 * The costs of labour and the sale coefficients used by the estimates are
 * the rates and settings of pricing (estimate package), shown in the
 * economic part of the company.
 */
@Service
public class CompanyDirectory {

	private final CompanyProfileRepository profiles;

	private final CompanyActivityRepository activities;

	private final EmployeeRepository employees;

	private final EmployeeCredentialRepository credentials;

	private final EquipmentRepository equipment;

	private final CompanyCertificationRepository certifications;

	private final CompanyReferenceRepository references;

	private final CompanyDocumentRepository documents;

	private final CompanyFiles files;

	private final CompanyProperties properties;

	private final Clock clock;

	public CompanyDirectory(CompanyProfileRepository profiles, CompanyActivityRepository activities,
			EmployeeRepository employees, EmployeeCredentialRepository credentials, EquipmentRepository equipment,
			CompanyCertificationRepository certifications, CompanyReferenceRepository references,
			CompanyDocumentRepository documents, CompanyFiles files, CompanyProperties properties, Clock clock) {
		this.profiles = profiles;
		this.activities = activities;
		this.employees = employees;
		this.credentials = credentials;
		this.equipment = equipment;
		this.certifications = certifications;
		this.references = references;
		this.documents = documents;
		this.files = files;
		this.properties = properties;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public CompanyProfile profile() {
		return profiles.current();
	}

	/** What GPE does, to compare with the types of works and CPV codes of a tender. */
	@Transactional(readOnly = true)
	public List<CompanyActivity> activities() {
		return activities.findAll();
	}

	/** The certifications and qualifications valid on a day, for a bid due then. */
	@Transactional(readOnly = true)
	public List<CompanyCertification> certificationsValidOn(LocalDate day) {
		return certifications.validOn(day);
	}

	/** The documents of a category valid on a day (attestations to join to a bid). */
	@Transactional(readOnly = true)
	public List<CompanyDocument> documentsValidOn(CompanyDocument.Category category, LocalDate day) {
		return documents.validOn(category, day);
	}

	/**
	 * Something of the company that ends.
	 * @param kind DOCUMENT, CERTIFICATION, CREDENTIAL (of an employee) or EQUIPMENT (its next check)
	 * @param detail who or what it belongs to, or null
	 */
	public record Expiry(String kind, Long id, String title, String detail, LocalDate endsOn, Validity validity) {
	}

	/**
	 * @param counts lines of each part of the company
	 * @param expiries what has expired or expires within the warning days,
	 * the soonest first
	 * @param documentsWithoutFile official documents not uploaded yet
	 */
	public record Overview(Map<String, Long> counts, List<Expiry> expiries, int warningDays,
			long documentsWithoutFile) {
	}

	@Transactional(readOnly = true)
	public Overview overview() {
		LocalDate today = LocalDate.now(clock);
		int days = properties.expiryWarningDays();
		Map<Long, String> names = employees.findAll()
			.stream()
			.collect(Collectors.toMap(Employee::getId, Employee::title));
		List<Expiry> expiries = new ArrayList<>();
		add(expiries, "DOCUMENT", documents.findAll(), document -> document.getCategory().name(), today, days);
		add(expiries, "CERTIFICATION", certifications.findAll(), certification -> null, today, days);
		add(expiries, "CREDENTIAL", credentials.findAll(), credential -> names.get(credential.getEmployeeId()), today,
				days);
		add(expiries, "EQUIPMENT", equipment.findAll(), item -> null, today, days);
		expiries.sort(Comparator.comparing(Expiry::endsOn));
		Map<Long, List<CompanyFiles.FileResponse>> documentFiles = files.byOwner(FileOwner.DOCUMENT);
		long withoutFile = documents.findAll()
			.stream()
			.filter(document -> !documentFiles.containsKey(document.getId()))
			.count();
		Map<String, Long> counts = Map.of("activities", activities.count(), "employees", employees.count(),
				"equipment", equipment.count(), "certifications", certifications.count(), "references",
				references.count(), "documents", documents.count());
		return new Overview(counts, List.copyOf(expiries), days, withoutFile);
	}

	private static <E extends CompanyRecord<?>> void add(List<Expiry> expiries, String kind, List<E> records,
			Function<E, String> detail, LocalDate today, int days) {
		for (E record : records) {
			LocalDate end = record.endsOn();
			Validity validity = Validity.of(end, today, days);
			if (validity == Validity.EXPIRED || validity == Validity.EXPIRING) {
				expiries.add(new Expiry(kind, record.getId(), record.title(), detail.apply(record), end, validity));
			}
		}
	}

}
