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

	private final org.springframework.jdbc.core.JdbcTemplate jdbc;

	public CompanyDirectory(CompanyProfileRepository profiles, CompanyActivityRepository activities,
			EmployeeRepository employees, EmployeeCredentialRepository credentials, EquipmentRepository equipment,
			CompanyCertificationRepository certifications, CompanyReferenceRepository references,
			CompanyDocumentRepository documents, CompanyFiles files, CompanyProperties properties, Clock clock,
			org.springframework.jdbc.core.JdbcTemplate jdbc) {
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
		this.jdbc = jdbc;
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
	 * An official document and its current file (the last uploaded).
	 * @param fileName the name of its file, null when none was uploaded
	 * @param path where its file is, null when none was uploaded
	 */
	public record DocumentFile(Long id, CompanyDocument.Category category, String name, LocalDate expiresOn,
			Validity validity, String fileName, java.nio.file.Path path) {
	}

	/** Every official document, to join them to an answer. */
	@Transactional(readOnly = true)
	public List<DocumentFile> documentFiles() {
		LocalDate today = LocalDate.now(clock);
		Map<Long, List<CompanyFiles.FileResponse>> byDocument = files.byOwner(FileOwner.DOCUMENT);
		return documents.findAll().stream().map(document -> {
			List<CompanyFiles.FileResponse> found = byDocument.getOrDefault(document.getId(), List.of());
			CompanyFiles.Stored stored = found.isEmpty() ? null : files.find(found.getFirst().id()).orElse(null);
			return new DocumentFile(document.getId(), document.getCategory(), document.title(), document.endsOn(),
					Validity.of(document.endsOn(), today, properties.expiryWarningDays()),
					stored == null ? null : stored.file().fileName(), stored == null ? null : stored.path());
		}).toList();
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

	/** The company as a bid presents it, on a day: what is valid then, read from its sources. */
	public record Snapshot(Identity identity, List<Activity> activities, List<Person> staff, List<Item> equipment,
			List<Certification> certifications, List<Work> references) {

		/** Nothing entered yet in the company. */
		public boolean isEmpty() {
			return identity.legalName() == null && activities.isEmpty() && staff.isEmpty() && equipment.isEmpty()
					&& certifications.isEmpty() && references.isEmpty();
		}

	}

	public record Identity(String legalName, String tradeName, String legalForm, String siret, String city,
			List<String> interventionDepartments, String interventionNote) {
	}

	public record Activity(String kind, String label, String description) {
	}

	/** @param credentials the qualifications and habilitations valid on the day */
	public record Person(String name, String jobTitle, String skills, List<String> credentials) {
	}

	public record Item(String category, String label, String model, int quantity) {
	}

	public record Certification(String kind, String name, String organism, String scope, LocalDate expiresOn) {
	}

	public record Work(String projectName, String client, String location, String nature, java.math.BigDecimal amount,
			LocalDate finishedOn, String description, String skills) {
	}

	@Transactional(readOnly = true)
	public Snapshot snapshot(LocalDate day) {
		Identity identity = jdbc.query("SELECT * FROM company_profile WHERE id = 1", (row, n) -> new Identity(
				row.getString("legal_name"), row.getString("trade_name"), row.getString("legal_form"),
				row.getString("siret"), row.getString("city"),
				List.of((String[]) row.getArray("intervention_departments").getArray()),
				row.getString("intervention_note"))).stream().findFirst()
			.orElse(new Identity(null, null, null, null, null, List.of(), null));
		List<Activity> activities = jdbc.query("SELECT kind, label, description FROM company_activity ORDER BY kind, label",
				(row, n) -> new Activity(row.getString(1), row.getString(2), row.getString(3)));
		Map<Long, List<String>> credentials = new java.util.HashMap<>();
		jdbc.query("""
				SELECT employee_id, label FROM employee_credential WHERE expires_on IS NULL OR expires_on >= ?
				ORDER BY label""", row -> {
			credentials.computeIfAbsent(row.getLong(1), id -> new ArrayList<>()).add(row.getString(2));
		}, day);
		List<Person> staff = jdbc.query("""
				SELECT id, first_name || ' ' || last_name, job_title, skills FROM employee WHERE active
				ORDER BY last_name, first_name""", (row, n) -> new Person(row.getString(2), row.getString(3),
				row.getString(4), credentials.getOrDefault(row.getLong(1), List.of())));
		List<Item> equipment = jdbc.query("""
				SELECT category, label, model, quantity FROM equipment WHERE status <> 'OUT_OF_SERVICE'
				ORDER BY category, label""", (row, n) -> new Item(row.getString(1), row.getString(2), row.getString(3),
				row.getInt(4)));
		List<Certification> certifications = jdbc.query("""
				SELECT kind, name, organism, scope, expires_on FROM company_certification
				WHERE expires_on IS NULL OR expires_on >= ? ORDER BY name""", (row, n) -> new Certification(
				row.getString(1), row.getString(2), row.getString(3), row.getString(4),
				row.getObject(5, LocalDate.class)), day);
		List<Work> references = jdbc.query("""
				SELECT project_name, client, location, nature, amount, finished_on, description, skills
				FROM company_reference ORDER BY finished_on DESC NULLS LAST, project_name""", (row, n) -> new Work(
				row.getString(1), row.getString(2), row.getString(3), row.getString(4), row.getBigDecimal(5),
				row.getObject(6, LocalDate.class), row.getString(7), row.getString(8)));
		return new Snapshot(identity, activities, staff, equipment, certifications, references);
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
