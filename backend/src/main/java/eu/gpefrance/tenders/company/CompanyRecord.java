package eu.gpefrance.tenders.company;

import java.time.Instant;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

/**
 * A line of a list of the company (an employee, an equipment...), changed
 * from a request of the interface. It is answered as it is stored: its
 * fields are its JSON.
 * @param <Q> the request that sets its values
 */
@MappedSuperclass
@JsonAutoDetect(fieldVisibility = Visibility.ANY, getterVisibility = Visibility.NONE,
		isGetterVisibility = Visibility.NONE)
public abstract class CompanyRecord<Q> {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "updated_by")
	private String updatedBy;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public Long getId() {
		return id;
	}

	/**
	 * Takes the values of the request, after the checks the request alone
	 * cannot make.
	 * @throws CompanyRuleException when they do not hold together
	 */
	abstract void apply(Q request);

	/** The day it ends (expiry, next check), or null when it has none. */
	LocalDate endsOn() {
		return null;
	}

	/** A short name, for the list of what expires. */
	abstract String title();

	final void save(Q request, String user, Instant now) {
		apply(request);
		updatedBy = user;
		updatedAt = now;
	}

}
