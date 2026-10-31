package eu.gpefrance.tenders.company;

import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The lists of the company, each at its path under /api/company. */
final class CompanyListControllers {

	private CompanyListControllers() {
	}

}

@RestController
@RequestMapping("/api/company/contacts")
class CompanyContactController extends RecordController<CompanyContact, CompanyContact.Request> {

	CompanyContactController(CompanyContactRepository repository, CompanyContext context) {
		super(repository, CompanyContact::new, Sort.by("name"), null, context);
	}

}

@RestController
@RequestMapping("/api/company/activities")
class CompanyActivityController extends RecordController<CompanyActivity, CompanyActivity.Request> {

	CompanyActivityController(CompanyActivityRepository repository, CompanyContext context) {
		super(repository, CompanyActivity::new, Sort.by("kind", "label"), null, context);
	}

}

@RestController
@RequestMapping("/api/company/employees")
class EmployeeController extends RecordController<Employee, Employee.Request> {

	private final EmployeeCredentialRepository credentials;

	private final CompanyFiles files;

	EmployeeController(EmployeeRepository repository, EmployeeCredentialRepository credentials,
			CompanyContext context) {
		super(repository, Employee::new, Sort.by("lastName", "firstName"), null, context);
		this.credentials = credentials;
		this.files = context.files();
	}

	/** The credentials go with the employee (database), their files too. */
	@Override
	protected void deleted(Long id) {
		files.deleteAll(FileOwner.CREDENTIAL, credentials.idsOfEmployee(id));
	}

}

@RestController
@RequestMapping("/api/company/credentials")
class EmployeeCredentialController extends RecordController<EmployeeCredential, EmployeeCredential.Request> {

	private final EmployeeRepository employees;

	EmployeeCredentialController(EmployeeCredentialRepository repository, EmployeeRepository employees,
			CompanyContext context) {
		super(repository, EmployeeCredential::new, Sort.by("expiresOn", "label"), FileOwner.CREDENTIAL, context);
		this.employees = employees;
	}

	@Override
	protected void check(EmployeeCredential.Request request) {
		if (!employees.existsById(request.employeeId())) {
			throw CompanyRuleException.invalid("Salarié introuvable.");
		}
	}

}

@RestController
@RequestMapping("/api/company/equipment")
class EquipmentController extends RecordController<Equipment, Equipment.Request> {

	EquipmentController(EquipmentRepository repository, CompanyContext context) {
		super(repository, Equipment::new, Sort.by("category", "label"), FileOwner.EQUIPMENT, context);
	}

}

@RestController
@RequestMapping("/api/company/certifications")
class CompanyCertificationController extends RecordController<CompanyCertification, CompanyCertification.Request> {

	CompanyCertificationController(CompanyCertificationRepository repository, CompanyContext context) {
		super(repository, CompanyCertification::new, Sort.by("name"), FileOwner.CERTIFICATION, context);
	}

}

@RestController
@RequestMapping("/api/company/references")
class CompanyReferenceController extends RecordController<CompanyReference, CompanyReference.Request> {

	CompanyReferenceController(CompanyReferenceRepository repository, CompanyContext context) {
		super(repository, CompanyReference::new, Sort.by(Sort.Order.desc("finishedOn").nullsFirst(), Sort.Order.asc("projectName")),
				FileOwner.REFERENCE, context);
	}

}

@RestController
@RequestMapping("/api/company/documents")
class CompanyDocumentController extends RecordController<CompanyDocument, CompanyDocument.Request> {

	CompanyDocumentController(CompanyDocumentRepository repository, CompanyContext context) {
		super(repository, CompanyDocument::new, Sort.by("category", "name"), FileOwner.DOCUMENT, context);
	}

}

@RestController
@RequestMapping("/api/company/economic-parameters")
class EconomicParameterController extends RecordController<EconomicParameter, EconomicParameter.Request> {

	EconomicParameterController(EconomicParameterRepository repository, CompanyContext context) {
		super(repository, EconomicParameter::new, Sort.by("label"), null, context);
	}

}
