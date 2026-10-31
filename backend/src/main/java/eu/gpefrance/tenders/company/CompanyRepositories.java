package eu.gpefrance.tenders.company;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** The repositories of the company, one per list. */
final class CompanyRepositories {

	private CompanyRepositories() {
	}

}

interface CompanyContactRepository extends JpaRepository<CompanyContact, Long> {
}

interface CompanyActivityRepository extends JpaRepository<CompanyActivity, Long> {
}

interface EmployeeRepository extends JpaRepository<Employee, Long> {
}

interface EmployeeCredentialRepository extends JpaRepository<EmployeeCredential, Long> {

	@Query("select c.id from EmployeeCredential c where c.employeeId = :employeeId")
	List<Long> idsOfEmployee(Long employeeId);

}

interface EquipmentRepository extends JpaRepository<Equipment, Long> {
}

interface CompanyCertificationRepository extends JpaRepository<CompanyCertification, Long> {

	@Query("select c from CompanyCertification c where c.expiresOn is null or c.expiresOn >= :day order by c.name")
	List<CompanyCertification> validOn(LocalDate day);

}

interface CompanyReferenceRepository extends JpaRepository<CompanyReference, Long> {
}

interface CompanyDocumentRepository extends JpaRepository<CompanyDocument, Long> {

	@Query("""
			select d from CompanyDocument d where d.category = :category
			  and (d.expiresOn is null or d.expiresOn >= :day) order by d.issuedOn desc nulls last""")
	List<CompanyDocument> validOn(CompanyDocument.Category category, LocalDate day);

}

interface EconomicParameterRepository extends JpaRepository<EconomicParameter, Long> {
}

interface CompanyProfileRepository extends JpaRepository<CompanyProfile, Short> {

	default CompanyProfile current() {
		return findById(CompanyProfile.ID).orElseThrow();
	}

}
