package eu.gpefrance.tenders.company;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import eu.gpefrance.tenders.alert.AlertService;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * What GPE does: a field of activity, a trade, a skill, a service or a
 * specialty. Its type of works and its CPV prefixes are those the tenders
 * use, so that they can be compared with them.
 */
@Entity
@Table(name = "company_activity")
public class CompanyActivity extends CompanyRecord<CompanyActivity.Request> {

	public enum Kind {

		/** Domaine d'activité. */
		DOMAIN,
		/** Métier. */
		TRADE,
		/** Compétence technique. */
		SKILL,
		/** Type de prestation. */
		SERVICE,
		/** Spécialité, savoir-faire. */
		SPECIALTY

	}

	/**
	 * @param activityCode a type of works of the saved searches (ELECTRICITY...), or null
	 */
	public record Request(@NotNull Kind kind, @NotBlank @Size(max = 200) String label,
			@Size(max = 4000) String description, @Size(max = 40) String activityCode,
			@Size(max = 50) List<@Pattern(regexp = "\\d{2,8}") String> cpvPrefixes) {
	}

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Kind kind;

	@Column(nullable = false)
	private String label;

	private String description;

	@Column(name = "activity_code")
	private String activityCode;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "cpv_prefixes", nullable = false)
	private List<String> cpvPrefixes = new ArrayList<>();

	@Override
	void apply(Request request) {
		String code = Texts.clean(request.activityCode());
		if (code != null && !AlertService.ACTIVITIES.contains(code)) {
			throw CompanyRuleException.invalid("Type de travaux inconnu : " + code);
		}
		kind = request.kind();
		label = request.label().strip();
		description = Texts.clean(request.description());
		activityCode = code;
		cpvPrefixes = new ArrayList<>(Texts.list(request.cpvPrefixes()));
	}

	@Override
	String title() {
		return label;
	}

	public Kind getKind() {
		return kind;
	}

	public String getLabel() {
		return label;
	}

	public String getActivityCode() {
		return activityCode;
	}

	public List<String> getCpvPrefixes() {
		return List.copyOf(cpvPrefixes);
	}

}
