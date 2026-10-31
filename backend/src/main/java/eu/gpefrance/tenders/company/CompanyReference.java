package eu.gpefrance.tenders.company;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** A work done by GPE, given as a reference in a bid, with its photos and proofs. */
@Entity
@Table(name = "company_reference")
public class CompanyReference extends CompanyRecord<CompanyReference.Request> {

	/**
	 * @param skills what the work shows GPE can do
	 */
	public record Request(@NotBlank @Size(max = 300) String projectName, @Size(max = 200) String client,
			@Size(max = 200) String location, @Size(max = 300) String nature,
			@PositiveOrZero @Digits(integer = 13, fraction = 2) BigDecimal amount, LocalDate startedOn,
			LocalDate finishedOn, @Size(max = 8000) String description, @Size(max = 4000) String skills) {
	}

	@Column(name = "project_name", nullable = false)
	private String projectName;

	private String client;

	private String location;

	private String nature;

	private BigDecimal amount;

	@Column(name = "started_on")
	private LocalDate startedOn;

	@Column(name = "finished_on")
	private LocalDate finishedOn;

	private String description;

	private String skills;

	@Override
	void apply(Request request) {
		CompanyRuleException.checkOrder(request.startedOn(), request.finishedOn(), request.projectName());
		projectName = request.projectName().strip();
		client = Texts.clean(request.client());
		location = Texts.clean(request.location());
		nature = Texts.clean(request.nature());
		amount = request.amount();
		startedOn = request.startedOn();
		finishedOn = request.finishedOn();
		description = Texts.clean(request.description());
		skills = Texts.clean(request.skills());
	}

	@Override
	String title() {
		return projectName;
	}

}
