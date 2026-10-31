package eu.gpefrance.tenders.company;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * The identity of GPE: a single row, changed by an administrator. Everything
 * may be empty until GPE fills it.
 */
@Entity
@Table(name = "company_profile")
@JsonAutoDetect(fieldVisibility = Visibility.ANY, getterVisibility = Visibility.NONE,
		isGetterVisibility = Visibility.NONE)
public class CompanyProfile {

	static final short ID = 1;

	/**
	 * @param interventionDepartments departments where GPE works (« 13 », « 2A »)
	 */
	public record Request(@Size(max = 200) String legalName, @Size(max = 200) String tradeName,
			@Size(max = 100) String legalForm, @Pattern(regexp = "\\d{9}") String siren,
			@Pattern(regexp = "\\d{14}") String siret, @Pattern(regexp = "[A-Z]{2}[0-9A-Z]{2,13}") String vatNumber,
			@Pattern(regexp = "\\d{2}\\.?\\d{2}[A-Z]") String apeCode, @Size(max = 100) String rcs,
			@PositiveOrZero @Digits(integer = 13, fraction = 2) BigDecimal shareCapital,
			@Size(max = 300) String addressLine, @Size(max = 10) String postalCode, @Size(max = 100) String city,
			@Size(max = 100) String country, @Size(max = 30) String phone, @Email @Size(max = 200) String email,
			@Size(max = 200) String website,
			@Size(max = 120) List<@Pattern(regexp = "\\d{2}|2A|2B|97[1-6]") String> interventionDepartments,
			@Size(max = 4000) String interventionNote) {
	}

	@Id
	@JsonIgnore
	private Short id;

	@Column(name = "legal_name")
	private String legalName;

	@Column(name = "trade_name")
	private String tradeName;

	@Column(name = "legal_form")
	private String legalForm;

	private String siren;

	private String siret;

	@Column(name = "vat_number")
	private String vatNumber;

	@Column(name = "ape_code")
	private String apeCode;

	private String rcs;

	@Column(name = "share_capital")
	private BigDecimal shareCapital;

	@Column(name = "address_line")
	private String addressLine;

	@Column(name = "postal_code")
	private String postalCode;

	private String city;

	private String country;

	private String phone;

	private String email;

	private String website;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "intervention_departments", nullable = false)
	private List<String> interventionDepartments = new ArrayList<>();

	@Column(name = "intervention_note")
	private String interventionNote;

	@Column(name = "updated_by")
	private String updatedBy;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected CompanyProfile() {
	}

	void change(Request request, String user, Instant now) {
		String siren = Texts.clean(request.siren());
		String siret = Texts.clean(request.siret());
		if (siren != null && siret != null && !siret.startsWith(siren)) {
			throw CompanyRuleException.invalid("Le SIRET doit commencer par le SIREN.");
		}
		this.legalName = Texts.clean(request.legalName());
		this.tradeName = Texts.clean(request.tradeName());
		this.legalForm = Texts.clean(request.legalForm());
		this.siren = siren;
		this.siret = siret;
		this.vatNumber = Texts.clean(request.vatNumber());
		this.apeCode = Texts.clean(request.apeCode());
		this.rcs = Texts.clean(request.rcs());
		this.shareCapital = request.shareCapital();
		this.addressLine = Texts.clean(request.addressLine());
		this.postalCode = Texts.clean(request.postalCode());
		this.city = Texts.clean(request.city());
		this.country = Texts.clean(request.country());
		this.phone = Texts.clean(request.phone());
		this.email = Texts.clean(request.email());
		this.website = Texts.clean(request.website());
		this.interventionDepartments = new ArrayList<>(Texts.list(request.interventionDepartments()));
		this.interventionNote = Texts.clean(request.interventionNote());
		this.updatedBy = user;
		this.updatedAt = now;
	}

	public String getLegalName() {
		return legalName;
	}

	public String getSiret() {
		return siret;
	}

	public List<String> getInterventionDepartments() {
		return List.copyOf(interventionDepartments);
	}

}
