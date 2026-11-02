package eu.gpefrance.tenders.memoir;

import static eu.gpefrance.tenders.memoir.MemoirHtml.escape;

import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import eu.gpefrance.tenders.company.CompanyDirectory.Snapshot;

/**
 * Parts of a memoir written from the data of the company, by plain code: the
 * same facts as in the company, never invented. Each block says what is
 * missing instead of filling it.
 */
final class CompanyBlocks {

	enum Block {

		PRESENTATION, STAFF, EQUIPMENT, CERTIFICATIONS, REFERENCES

	}

	private static final Map<String, String> EQUIPMENT_CATEGORIES = Map.of("VEHICLE", "Véhicules", "TOOL",
			"Matériel et outillage", "EQUIPMENT", "Équipements techniques", "SITE", "Moyens de chantier");

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private CompanyBlocks() {
	}

	/** @throws MemoirRuleException when the company holds nothing for this block */
	static String html(Block block, Snapshot company) {
		return switch (block) {
			case PRESENTATION -> presentation(company);
			case STAFF -> staff(company);
			case EQUIPMENT -> equipment(company);
			case CERTIFICATIONS -> certifications(company);
			case REFERENCES -> references(company);
		};
	}

	private static String presentation(Snapshot company) {
		var identity = company.identity();
		if (identity.legalName() == null && company.activities().isEmpty()) {
			throw missing("l'identité et les activités");
		}
		StringBuilder html = new StringBuilder("<p>");
		html.append("<strong>").append(escape(identity.tradeName() != null ? identity.tradeName() : identity.legalName()))
			.append("</strong>");
		if (identity.legalForm() != null) {
			html.append(", ").append(escape(identity.legalForm()));
		}
		if (identity.city() != null) {
			html.append(" établie à ").append(escape(identity.city()));
		}
		if (identity.siret() != null) {
			html.append(" (SIRET ").append(escape(identity.siret())).append(')');
		}
		html.append(".</p>");
		if (!company.activities().isEmpty()) {
			html.append("<p>Domaines d'intervention :</p><ul>");
			company.activities().forEach(activity -> html.append("<li><strong>").append(escape(activity.label()))
				.append("</strong>")
				.append(activity.description() == null ? "" : " : " + escape(activity.description()))
				.append("</li>"));
			html.append("</ul>");
		}
		if (!identity.interventionDepartments().isEmpty()) {
			html.append("<p>Zone d'intervention : départements ")
				.append(escape(String.join(", ", identity.interventionDepartments())))
				.append(".</p>");
		}
		return html.toString();
	}

	private static String staff(Snapshot company) {
		if (company.staff().isEmpty()) {
			throw missing("le personnel");
		}
		StringBuilder html = new StringBuilder("<p>Effectif mobilisable : ").append(company.staff().size())
			.append(" personne").append(company.staff().size() > 1 ? "s" : "").append(".</p>");
		html.append("<table><thead><tr><th>Nom</th><th>Fonction</th><th>Qualifications et habilitations en cours de validité</th></tr></thead><tbody>");
		company.staff()
			.forEach(person -> html.append("<tr><td>").append(escape(person.name())).append("</td><td>")
				.append(escape(person.jobTitle()))
				.append("</td><td>")
				.append(escape(String.join(", ", person.credentials())))
				.append("</td></tr>"));
		return html.append("</tbody></table>").toString();
	}

	private static String equipment(Snapshot company) {
		if (company.equipment().isEmpty()) {
			throw missing("le matériel");
		}
		StringBuilder html = new StringBuilder();
		company.equipment()
			.stream()
			.collect(java.util.stream.Collectors.groupingBy(item -> item.category(), java.util.LinkedHashMap::new,
					java.util.stream.Collectors.toList()))
			.forEach((category, items) -> {
				html.append("<h4>").append(escape(EQUIPMENT_CATEGORIES.getOrDefault(category, category))).append("</h4><ul>");
				items.forEach(item -> html.append("<li>").append(item.quantity()).append(" × ").append(escape(item.label()))
					.append(item.model() == null ? "" : " (" + escape(item.model()) + ")")
					.append("</li>"));
				html.append("</ul>");
			});
		return html.toString();
	}

	private static String certifications(Snapshot company) {
		if (company.certifications().isEmpty()) {
			throw missing("les certifications");
		}
		StringBuilder html = new StringBuilder("<ul>");
		company.certifications()
			.forEach(certification -> html.append("<li><strong>").append(escape(certification.name()))
				.append("</strong>")
				.append(certification.organism() == null ? "" : ", délivrée par " + escape(certification.organism()))
				.append(certification.expiresOn() == null ? "" : ", valable jusqu'au " + DATE.format(certification.expiresOn()))
				.append(certification.scope() == null ? "" : " : " + escape(certification.scope()))
				.append("</li>"));
		return html.append("</ul>").toString();
	}

	private static String references(Snapshot company) {
		if (company.references().isEmpty()) {
			throw missing("les références");
		}
		NumberFormat money = NumberFormat.getCurrencyInstance(Locale.FRANCE);
		StringBuilder html = new StringBuilder(
				"<table><thead><tr><th>Projet</th><th>Client</th><th>Nature des travaux</th><th>Montant HT</th><th>Achèvement</th></tr></thead><tbody>");
		company.references()
			.forEach(work -> html.append("<tr><td>").append(escape(work.projectName()))
				.append(work.location() == null ? "" : " — " + escape(work.location()))
				.append("</td><td>")
				.append(escape(work.client()))
				.append("</td><td>")
				.append(escape(work.nature()))
				.append("</td><td>")
				.append(work.amount() == null ? "" : escape(money.format(work.amount())))
				.append("</td><td>")
				.append(work.finishedOn() == null ? "" : DATE.format(work.finishedOn()))
				.append("</td></tr>"));
		return html.append("</tbody></table>").toString();
	}

	private static MemoirRuleException missing(String what) {
		return MemoirRuleException.invalid("Rien n'est encore renseigné pour " + what
				+ " dans la section Entreprise : complétez-la d'abord.");
	}

	static List<Block> all() {
		return List.of(Block.values());
	}

}
