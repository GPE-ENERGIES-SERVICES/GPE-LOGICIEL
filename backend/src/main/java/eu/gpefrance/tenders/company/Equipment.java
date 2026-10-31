package eu.gpefrance.tenders.company;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * A vehicle, a tool, a technical equipment or a means of site, with its
 * quantity, its state and the date of its next check (contrôle technique,
 * vérification périodique), followed as an expiry.
 */
@Entity
@Table(name = "equipment")
public class Equipment extends CompanyRecord<Equipment.Request> {

	public enum Category {

		VEHICLE, TOOL, EQUIPMENT, SITE

	}

	public enum Status {

		AVAILABLE, IN_USE, MAINTENANCE, OUT_OF_SERVICE

	}

	/**
	 * @param identifier registration, serial number...
	 */
	public record Request(@NotNull Category category, @NotBlank @Size(max = 200) String label,
			@Size(max = 200) String model, @Size(max = 100) String identifier,
			@PositiveOrZero @Max(1_000_000) Integer quantity, Status status, LocalDate nextCheckOn,
			@Size(max = 4000) String notes) {
	}

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Category category;

	@Column(nullable = false)
	private String label;

	private String model;

	private String identifier;

	@Column(nullable = false)
	private int quantity = 1;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Status status = Status.AVAILABLE;

	@Column(name = "next_check_on")
	private LocalDate nextCheckOn;

	private String notes;

	@Override
	void apply(Request request) {
		category = request.category();
		label = request.label().strip();
		model = Texts.clean(request.model());
		identifier = Texts.clean(request.identifier());
		quantity = request.quantity() == null ? 1 : request.quantity();
		status = request.status() == null ? Status.AVAILABLE : request.status();
		nextCheckOn = request.nextCheckOn();
		notes = Texts.clean(request.notes());
	}

	@Override
	LocalDate endsOn() {
		return nextCheckOn;
	}

	@Override
	String title() {
		return identifier == null ? label : label + " (" + identifier + ")";
	}

}
