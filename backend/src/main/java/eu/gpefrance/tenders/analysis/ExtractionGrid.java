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

	public static final String VERSION = "grid-3";

	public static final String DEADLINE = "deadline";

	public static final String SITE_VISIT = "site_visit";

	public static final String VISIT_REQUIRED = "obligatoire";

	public static final String QUALIFICATIONS = "qualifications";

	public static final List<GridField> FIELDS = List.of(
			single(DEADLINE, "Date et heure limite de remise des offres", AT_A_GLANCE, FieldKind.DATE_TIME,
					"Date et heure limite de réception des offres, telles qu'écrites dans le règlement de la consultation "
							+ "(à défaut l'avis). En procédure restreinte, celle des candidatures. Si une date a été "
							+ "reportée, la plus récente, avec AMBIGUOUS si les documents ne disent pas laquelle prévaut."),
			choice(SITE_VISIT, "Visite du site", AT_A_GLANCE,
					"La visite des lieux est-elle obligatoire (une offre sans visite est rejetée ou une attestation de "
							+ "visite est exigée) ou facultative (conseillée, recommandée) ? Si aucun document n'en parle, "
							+ "NOT_INDICATED.",
					List.of(VISIT_REQUIRED, "facultative")),
			single("site_visit_details", "Modalités de la visite", AT_A_GLANCE, FieldKind.TEXT,
					"Dates ou créneaux, lieu de rendez-vous, modalités d'inscription (délai, adresse électronique), "
							+ "nom et téléphone du contact, attestation à joindre à l'offre."),
			list("lots", "Lots", AT_A_GLANCE,
					"Un élément par lot, sous la forme « Lot 1 : intitulé », en reprenant le numéro et l'intitulé "
							+ "exacts. Indiquer aussi si une offre peut porter sur plusieurs lots ou si le nombre de lots "
							+ "attribués à un même candidat est limité, comme élément séparé."),
			single("estimated_amount", "Montant estimé", AT_A_GLANCE, FieldKind.AMOUNT,
					"Montant estimé du marché entier en euros HT. Jamais le montant d'un seul lot, jamais un montant "
							+ "TTC présenté comme HT : si seul le TTC est donné, AMBIGUOUS avec ce montant et l'extrait."),
			single("amount_min", "Montant minimum", AT_A_GLANCE, FieldKind.AMOUNT,
					"Montant minimum de l'accord-cadre en euros HT, sur toute sa durée ou par période (préciser "
							+ "laquelle dans l'extrait)."),
			single("amount_max", "Montant maximum", AT_A_GLANCE, FieldKind.AMOUNT,
					"Montant maximum de l'accord-cadre en euros HT, sur toute sa durée ou par période (préciser "
							+ "laquelle dans l'extrait)."),
			single("duration", "Durée ou délai d'exécution", AT_A_GLANCE, FieldKind.TEXT,
					"Délai global d'exécution des travaux ou durée du marché (avec son unité : jours, semaines, mois), "
							+ "son point de départ (notification, ordre de service) et la date prévisionnelle de "
							+ "démarrage si elle est écrite. Délais par lot ou par phase si le document les distingue."),
			single("preparation_period", "Période de préparation", AT_A_GLANCE, FieldKind.TEXT,
					"Durée de la période de préparation du chantier, et si elle est comprise ou non dans le délai "
							+ "d'exécution."),
			single("renewals", "Reconductions", AT_A_GLANCE, FieldKind.TEXT,
					"Nombre et durée des reconductions, reconduction tacite ou expresse, ou absence de reconduction "
							+ "si elle est écrite."),
			list("tranches_options", "Tranches et options", AT_A_GLANCE,
					"Chaque tranche (ferme, optionnelle avec son délai d'affermissement) et chaque prestation "
							+ "supplémentaire éventuelle (PSE) ou option, avec son intitulé et si elle est obligatoire "
							+ "à chiffrer."),
			list("attention_points", "Exigences particulières relevées", ATTENTION,
					"Ce qui peut faire perdre le marché ou de l'argent et qu'un chef d'entreprise doit voir tout de "
							+ "suite : travaux en site occupé ou de nuit, horaires imposés, astreintes, coactivité, "
							+ "amiante ou plomb, phasage contraignant, délai très court, échantillons ou maquettes, "
							+ "réunions obligatoires, clause de réexamen. Un élément par exigence, sans répéter les "
							+ "pénalités ni les clauses environnementales, relevées à part."),
			list("late_penalties", "Pénalités", ATTENTION,
					"Chaque pénalité chiffrée, avec son montant et son unité (par jour calendaire de retard, par "
							+ "absence, en euros ou en fraction du montant), et le plafond s'il est écrit : retard "
							+ "d'exécution, absence aux réunions, retard de remise des documents, propreté du chantier, "
							+ "insertion non réalisée."),
			list("environmental_social_clauses", "Clauses environnementales et sociales", ATTENTION,
					"Chaque clause imposée : heures d'insertion (avec le nombre d'heures), gestion et traçabilité des "
							+ "déchets (SOGED), chantier propre ou à faibles nuisances, matériaux biosourcés ou réemploi, "
							+ "critère environnemental de jugement."),
			single("object", "Objet", REQUESTED, FieldKind.TEXT,
					"Objet de la consultation tel qu'il est intitulé dans le règlement de la consultation."),
			single("contract_type", "Type de marché", REQUESTED, FieldKind.TEXT,
					"Travaux, fournitures ou services, et forme du marché : marché ordinaire, accord-cadre à bons de "
							+ "commande ou à marchés subséquents, mono ou multi-attributaire."),
			single("price_form", "Forme et révision des prix", REQUESTED, FieldKind.TEXT,
					"Forme du prix (global et forfaitaire, unitaire, mixte) et mode de variation : ferme, actualisable "
							+ "ou révisable, avec l'indice (BT01, TP01…) et le mois zéro s'ils sont écrits."),
			list("works_nature", "Prestations demandées", REQUESTED,
					"Chaque nature de travaux ou corps d'état demandé (gros œuvre, électricité, VRD…), avec les "
							+ "quantités ou surfaces principales si elles sont écrites. Pas le détail ligne par ligne du "
							+ "bordereau."),
			list("technical_standards", "Normes et spécifications", REQUESTED,
					"Chaque norme (NF, EN), DTU, avis technique ou référentiel imposé explicitement, avec son numéro. "
							+ "Pas les normes citées seulement à titre d'exemple."),
			single("buyer", "Acheteur", REQUESTED, FieldKind.TEXT,
					"Nom de l'acheteur public (pouvoir adjudicateur) et, s'il est distinct, du maître d'œuvre."),
			single("reference", "Référence", REQUESTED, FieldKind.TEXT,
					"Référence ou numéro de la consultation donné par l'acheteur."),
			single("location", "Lieu d'exécution", REQUESTED, FieldKind.TEXT,
					"Adresse ou commune du chantier, avec le bâtiment ou le site s'il est précisé."),
			single("procedure", "Procédure", REQUESTED, FieldKind.TEXT,
					"Type de procédure (procédure adaptée, appel d'offres ouvert ou restreint…) et possibilité de "
							+ "négociation si elle est écrite."),
			choice("variants", "Variantes", REQUESTED,
					"Les variantes à l'initiative du candidat sont-elles autorisées ou interdites ? Les variantes "
							+ "imposées par l'acheteur (PSE) ne comptent pas ici.",
					List.of("autorisées", "interdites")),
			list("award_criteria", "Critères d'attribution", TO_ANSWER,
					"Chaque critère de jugement des offres avec sa pondération, sous la forme « Prix : 60 % » ; les "
							+ "sous-critères entre parenthèses (« Valeur technique : 40 % (méthodologie 20 %, "
							+ "planning 10 %…) »)."),
			list(QUALIFICATIONS, "Qualifications et certifications", TO_ANSWER,
					"Chaque qualification, certification ou label demandé, avec son code s'il est écrit (Qualibat "
							+ "2112, Qualifelec, RGE…), et la mention « ou équivalent » si elle figure."),
			list("insurance", "Assurances", TO_ANSWER,
					"Chaque attestation d'assurance demandée (responsabilité civile professionnelle, décennale…), avec "
							+ "le montant de garantie exigé s'il est écrit."),
			list("references_required", "Références demandées", TO_ANSWER,
					"Références de travaux similaires demandées (nombre, ancienneté, montant) et autres capacités "
							+ "techniques exigées (effectifs, moyens matériels)."),
			list("financial_capacity", "Capacité financière", TO_ANSWER,
					"Chaque exigence de chiffre d'affaires ou de capacité financière, avec le montant et la période "
							+ "demandés."),
			single("subcontracting", "Sous-traitance", TO_ANSWER, FieldKind.TEXT,
					"Conditions ou limites de la sous-traitance (tâches essentielles réservées au titulaire, part "
							+ "maximale), si le document en parle."),
			single("groupement", "Groupement d'entreprises", TO_ANSWER, FieldKind.TEXT,
					"Forme de groupement imposée ou acceptée (conjoint, solidaire, mandataire solidaire) et "
							+ "interdictions éventuelles (candidater seul et en groupement…)."),
			list("guarantees", "Garanties financières", TO_ANSWER,
					"Chaque garantie avec son taux : retenue de garantie (souvent 5 %), garantie à première demande "
							+ "ou caution qui la remplace, garantie de l'avance."),
			single("payment_terms", "Paiement et avance", TO_ANSWER, FieldKind.TEXT,
					"Délai de paiement, acomptes (fréquence) et avance : taux, seuil de déclenchement, possibilité "
							+ "d'y renoncer."),
			single("offer_validity", "Validité des offres", TO_ANSWER, FieldKind.TEXT,
					"Durée de validité des offres (en jours ou en mois) à compter de la date limite de remise."),
			list("application_documents", "Pièces de candidature", TO_ANSWER,
					"Chaque document à fournir pour la candidature (DC1, DC2, DUME, attestations sociales et "
							+ "fiscales, Kbis, références…), un élément par pièce."),
			list("offer_documents", "Pièces de l'offre", TO_ANSWER,
					"Chaque document à fournir pour l'offre (acte d'engagement, DPGF ou BPU et DQE, mémoire technique "
							+ "et son cadre éventuel, planning, attestation de visite…), un élément par pièce."),
			single("submission_platform", "Plateforme de dépôt", TO_ANSWER, FieldKind.TEXT,
					"Plateforme ou adresse où déposer la réponse, et modalités de remise (électronique obligatoire, "
							+ "copie de sauvegarde, signature électronique exigée ou non)."),
			single("questions_deadline", "Date limite des questions", TO_ANSWER, FieldKind.DATE_TIME,
					"Date limite pour poser des questions à l'acheteur ou demander des renseignements complémentaires. "
							+ "Si le document ne donne qu'un délai (« 10 jours avant la date limite »), ne calcule pas "
							+ "la date : NOT_INDICATED."));

	private ExtractionGrid() {
	}

	public static Optional<GridField> find(String key) {
		return FIELDS.stream().filter(field -> field.key().equals(key)).findFirst();
	}

}
