package eu.gpefrance.tenders.analysis;

import static eu.gpefrance.tenders.analysis.GridField.choice;
import static eu.gpefrance.tenders.analysis.GridField.list;
import static eu.gpefrance.tenders.analysis.GridField.single;
import static eu.gpefrance.tenders.analysis.SummarySection.AT_A_GLANCE;
import static eu.gpefrance.tenders.analysis.SummarySection.ATTENTION;
import static eu.gpefrance.tenders.analysis.SummarySection.REQUESTED;
import static eu.gpefrance.tenders.analysis.SummarySection.TO_ANSWER;

import java.util.List;
import java.util.Optional;

/**
 * The fixed list of information the model must look for in the consultation
 * rules (RC) and the notice. Every field gets an answer, possibly "not
 * indicated". Adding a field needs no database change: values are stored as
 * rows. Changing the grid or the instructions must change {@link #VERSION}
 * so that analyses stay comparable.
 */
public final class ExtractionGrid {

	public static final String VERSION = "grid-1";

	public static final String DEADLINE = "deadline";

	public static final String SITE_VISIT = "site_visit";

	public static final String VISIT_REQUIRED = "obligatoire";

	public static final String QUALIFICATIONS = "qualifications";

	public static final List<GridField> FIELDS = List.of(
			single(DEADLINE, "Date et heure limite de remise des offres", AT_A_GLANCE, FieldKind.DATE_TIME,
					"Date et heure limite de réception des offres (ou des candidatures si la procédure est restreinte)."),
			choice(SITE_VISIT, "Visite du site", AT_A_GLANCE,
					"La visite des lieux est-elle obligatoire ou facultative ? Si le document n'en parle pas, répondre non indiqué.",
					List.of(VISIT_REQUIRED, "facultative")),
			single("site_visit_details", "Modalités de la visite", AT_A_GLANCE, FieldKind.TEXT,
					"Dates, inscription et contact pour la visite."),
			list("lots", "Lots", AT_A_GLANCE, "Chaque lot : numéro et intitulé."),
			single("estimated_amount", "Montant estimé", AT_A_GLANCE, FieldKind.AMOUNT,
					"Montant estimé du marché en euros hors taxes, s'il est indiqué."),
			single("duration", "Durée du marché", AT_A_GLANCE, FieldKind.TEXT,
					"Durée du marché ou délai d'exécution, et date prévisionnelle de démarrage si elle est indiquée."),
			list("attention_points", "Points d'attention", ATTENTION,
					"Exigences inhabituelles ou contraignantes à ne pas manquer : travaux en site occupé, astreintes, délais très courts, pénalités élevées, clauses particulières."),
			single("object", "Objet", REQUESTED, FieldKind.TEXT, "Objet de la consultation."),
			single("buyer", "Acheteur", REQUESTED, FieldKind.TEXT, "Nom de l'acheteur public (pouvoir adjudicateur)."),
			single("reference", "Référence", REQUESTED, FieldKind.TEXT, "Référence ou numéro de la consultation."),
			single("location", "Lieu d'exécution", REQUESTED, FieldKind.TEXT, "Lieu d'exécution des travaux ou prestations."),
			single("procedure", "Procédure", REQUESTED, FieldKind.TEXT,
					"Type de procédure (procédure adaptée, appel d'offres ouvert, etc.)."),
			choice("variants", "Variantes", REQUESTED,
					"Les variantes à l'initiative du candidat sont-elles autorisées ou interdites ?",
					List.of("autorisées", "interdites")),
			list("award_criteria", "Critères d'attribution", TO_ANSWER,
					"Chaque critère de jugement des offres avec sa pondération (par exemple « Prix : 60 % »)."),
			list(QUALIFICATIONS, "Qualifications et certifications", TO_ANSWER,
					"Chaque qualification, certification ou label demandé (Qualibat, Qualifelec, RGE, etc.)."),
			list("insurance", "Assurances", TO_ANSWER,
					"Chaque attestation d'assurance demandée (responsabilité civile, décennale, etc.)."),
			list("references_required", "Références demandées", TO_ANSWER,
					"Références de travaux similaires ou capacités demandées aux candidats."),
			list("application_documents", "Pièces de candidature", TO_ANSWER,
					"Chaque document à fournir pour la candidature (DC1, DC2, DUME, attestations, etc.)."),
			list("offer_documents", "Pièces de l'offre", TO_ANSWER,
					"Chaque document à fournir pour l'offre (acte d'engagement, DPGF, mémoire technique, etc.)."),
			single("submission_platform", "Plateforme de dépôt", TO_ANSWER, FieldKind.TEXT,
					"Plateforme ou adresse où déposer la réponse, et modalités de remise."),
			single("questions_deadline", "Date limite des questions", TO_ANSWER, FieldKind.DATE_TIME,
					"Date limite pour poser des questions à l'acheteur ou demander des renseignements complémentaires."));

	private ExtractionGrid() {
	}

	public static Optional<GridField> find(String key) {
		return FIELDS.stream().filter(field -> field.key().equals(key)).findFirst();
	}

}
