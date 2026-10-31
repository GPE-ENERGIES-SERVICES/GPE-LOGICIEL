package eu.gpefrance.tenders.company;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A person to contact at GPE (direction, études, conduite de travaux...). */
@Entity
@Table(name = "company_contact")
public class CompanyContact extends CompanyRecord<CompanyContact.Request> {

	public record Request(@NotBlank @Size(max = 200) String name, @Size(max = 200) String role,
			@Size(max = 30) String phone, @Email @Size(max = 200) String email, @Size(max = 4000) String notes) {
	}

	@Column(nullable = false)
	private String name;

	private String role;

	private String phone;

	private String email;

	private String notes;

	@Override
	void apply(Request request) {
		name = request.name().strip();
		role = Texts.clean(request.role());
		phone = Texts.clean(request.phone());
		email = Texts.clean(request.email());
		notes = Texts.clean(request.notes());
	}

	@Override
	String title() {
		return name;
	}

}
