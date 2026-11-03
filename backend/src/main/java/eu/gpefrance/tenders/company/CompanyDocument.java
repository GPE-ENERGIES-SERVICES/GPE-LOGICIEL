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

/**
 * An official document of the company, asked in the bids: its current file
 * is the last uploaded, the former ones stay as its history. Replacing it is
 * uploading the new file and changing its dates.
 */
@Entity
@Table(name = "company_document")
public class CompanyDocument extends CompanyRecord<CompanyDocument.Request> {

	public enum Category {

		/** Extrait Kbis. */
		KBIS,
		/** Attestation d'assurance (décennale, responsabilité civile). */
		INSURANCE,
		/** Attestation de régularité fiscale. */
		TAX,
		/** Attestation de régularité sociale. */
		SOCIAL,
		/** Attestation de vigilance URSSAF. */
		VIGILANCE,
		/** Certificat ou qualification. */
		CERTIFICATE,
		/** Bilan, chiffre d'affaires. */
		FINANCIAL,
		/** Charte de sécurité, prévention des risques. */
		SAFETY,
		/** Déclaration des moyens humains, effectifs. */
		STAFF,
		/** Déclaration des moyens matériels. */
		EQUIPMENT,
		/** Présentation de l'entreprise, réalisations, références. */
		REFERENCES,
		/** Autre justificatif administratif. */
		OTHER

	}

	public record Request(@NotNull Category category, @NotBlank @Size(max = 200) String name, LocalDate issuedOn,
			LocalDate expiresOn, @Size(max = 4000) String notes) {
	}

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Category category;

	@Column(nullable = false)
	private String name;

	@Column(name = "issued_on")
	private LocalDate issuedOn;

	@Column(name = "expires_on")
	private LocalDate expiresOn;

	private String notes;

	@Override
	void apply(Request request) {
		CompanyRuleException.checkOrder(request.issuedOn(), request.expiresOn(), request.name());
		category = request.category();
		name = request.name().strip();
		issuedOn = request.issuedOn();
		expiresOn = request.expiresOn();
		notes = Texts.clean(request.notes());
	}

	@Override
	LocalDate endsOn() {
		return expiresOn;
	}

	@Override
	String title() {
		return name;
	}

	public Category getCategory() {
		return category;
	}

}
