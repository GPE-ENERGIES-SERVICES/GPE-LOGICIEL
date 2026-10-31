package eu.gpefrance.tenders.company;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A qualification, habilitation (B2V, BR, H0...), certificate or training of an employee, with its end. */
@Entity
@Table(name = "employee_credential")
public class EmployeeCredential extends CompanyRecord<EmployeeCredential.Request> {

	public enum Kind {

		QUALIFICATION, HABILITATION, CERTIFICATION, TRAINING

	}

	public record Request(@NotNull Long employeeId, @NotNull Kind kind, @NotBlank @Size(max = 200) String label,
			@Size(max = 100) String number, @Size(max = 200) String issuer, LocalDate obtainedOn, LocalDate expiresOn) {
	}

	@Column(name = "employee_id", nullable = false)
	private Long employeeId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Kind kind;

	@Column(nullable = false)
	private String label;

	private String number;

	private String issuer;

	@Column(name = "obtained_on")
	private LocalDate obtainedOn;

	@Column(name = "expires_on")
	private LocalDate expiresOn;

	@Override
	void apply(Request request) {
		CompanyRuleException.checkOrder(request.obtainedOn(), request.expiresOn(), request.label());
		employeeId = request.employeeId();
		kind = request.kind();
		label = request.label().strip();
		number = Texts.clean(request.number());
		issuer = Texts.clean(request.issuer());
		obtainedOn = request.obtainedOn();
		expiresOn = request.expiresOn();
	}

	@Override
	LocalDate endsOn() {
		return expiresOn;
	}

	@Override
	String title() {
		return label;
	}

	public Long getEmployeeId() {
		return employeeId;
	}

	public Kind getKind() {
		return kind;
	}

}
