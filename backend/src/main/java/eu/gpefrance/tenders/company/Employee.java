package eu.gpefrance.tenders.company;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A person working for GPE; their qualifications and habilitations are {@link EmployeeCredential}s. */
@Entity
@Table(name = "employee")
public class Employee extends CompanyRecord<Employee.Request> {

	public record Request(@NotBlank @Size(max = 100) String firstName, @NotBlank @Size(max = 100) String lastName,
			@Size(max = 200) String jobTitle, @Email @Size(max = 200) String email, @Size(max = 30) String phone,
			LocalDate hiredOn, Boolean active, @Size(max = 4000) String skills, @Size(max = 8000) String experience) {
	}

	@Column(name = "first_name", nullable = false)
	private String firstName;

	@Column(name = "last_name", nullable = false)
	private String lastName;

	@Column(name = "job_title")
	private String jobTitle;

	private String email;

	private String phone;

	@Column(name = "hired_on")
	private LocalDate hiredOn;

	@Column(nullable = false)
	private boolean active = true;

	private String skills;

	private String experience;

	@Override
	void apply(Request request) {
		firstName = request.firstName().strip();
		lastName = request.lastName().strip();
		jobTitle = Texts.clean(request.jobTitle());
		email = Texts.clean(request.email());
		phone = Texts.clean(request.phone());
		hiredOn = request.hiredOn();
		active = request.active() == null || request.active();
		skills = Texts.clean(request.skills());
		experience = Texts.clean(request.experience());
	}

	@Override
	String title() {
		return firstName + " " + lastName;
	}

	public boolean isActive() {
		return active;
	}

	public String getJobTitle() {
		return jobTitle;
	}

}
