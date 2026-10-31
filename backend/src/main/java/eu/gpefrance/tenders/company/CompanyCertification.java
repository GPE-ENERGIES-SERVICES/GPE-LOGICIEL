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

/** A certification, qualification (Qualifelec, RGE, MASE...) or label of the company, with its proof. */
@Entity
@Table(name = "company_certification")
public class CompanyCertification extends CompanyRecord<CompanyCertification.Request> {

	public enum Kind {

		CERTIFICATION, QUALIFICATION, LABEL

	}

	public record Request(@NotNull Kind kind, @NotBlank @Size(max = 200) String name,
			@Size(max = 200) String organism, @Size(max = 100) String number, @Size(max = 4000) String scope,
			LocalDate obtainedOn, LocalDate expiresOn) {
	}

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Kind kind;

	@Column(nullable = false)
	private String name;

	private String organism;

	private String number;

	private String scope;

	@Column(name = "obtained_on")
	private LocalDate obtainedOn;

	@Column(name = "expires_on")
	private LocalDate expiresOn;

	@Override
	void apply(Request request) {
		CompanyRuleException.checkOrder(request.obtainedOn(), request.expiresOn(), request.name());
		kind = request.kind();
		name = request.name().strip();
		organism = Texts.clean(request.organism());
		number = Texts.clean(request.number());
		scope = Texts.clean(request.scope());
		obtainedOn = request.obtainedOn();
		expiresOn = request.expiresOn();
	}

	@Override
	LocalDate endsOn() {
		return expiresOn;
	}

	@Override
	String title() {
		return name;
	}

}
