package eu.gpefrance.tenders.company;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A value of GPE for its prices (frais généraux, marge visée...), kept here
 * as entered. No computation uses it until its rule is decided with GPE; the
 * costs of labour and the sale coefficients used by the estimates are the
 * rates and settings of pricing.
 */
@Entity
@Table(name = "company_economic_parameter")
public class EconomicParameter extends CompanyRecord<EconomicParameter.Request> {

	public record Request(@NotBlank @Size(max = 200) String label,
			@Digits(integer = 11, fraction = 4) BigDecimal value, @Size(max = 30) String unit,
			@Size(max = 4000) String note) {
	}

	@Column(nullable = false)
	private String label;

	private BigDecimal value;

	private String unit;

	private String note;

	@Override
	void apply(Request request) {
		label = request.label().strip();
		value = request.value();
		unit = Texts.clean(request.unit());
		note = Texts.clean(request.note());
	}

	@Override
	String title() {
		return label;
	}

}
