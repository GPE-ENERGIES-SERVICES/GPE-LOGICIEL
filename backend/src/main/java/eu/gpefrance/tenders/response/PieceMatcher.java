package eu.gpefrance.tenders.response;

import java.text.Normalizer;
import java.util.regex.Pattern;

import eu.gpefrance.tenders.company.CompanyDocument;

/**
 * What a piece asked by the buyer is, read from its label: a suggestion, that
 * a user can always change.
 */
final class PieceMatcher {

	enum Kind {

		/** DC1, DC2, DUME. */
		FORM,
		/** The form of the buyer, in the consultation file. */
		ENGAGEMENT,
		/** A price schedule of the buyer, filled from the estimate. */
		PRICES,
		MEMOIR,
		OTHER

	}

	private static final Pattern FORM = Pattern.compile("\\b(dc ?1|dc ?2|dume)\\b");

	private static final Pattern ENGAGEMENT = Pattern.compile("\\bacte d.engagement\\b|\\bae\\b|\\batri\\b");

	private static final Pattern PRICES = Pattern
		.compile("\\b(dpgf|bpu|dqe)\\b|decomposition du prix|bordereau des prix|detail quantitatif");

	private static final Pattern MEMOIR = Pattern.compile("\\bmemoire\\b|note methodologique");

	private PieceMatcher() {
	}

	static Kind kind(String label) {
		String text = plain(label);
		if (FORM.matcher(text).find()) {
			return Kind.FORM;
		}
		if (ENGAGEMENT.matcher(text).find()) {
			return Kind.ENGAGEMENT;
		}
		if (PRICES.matcher(text).find()) {
			return Kind.PRICES;
		}
		return MEMOIR.matcher(text).find() ? Kind.MEMOIR : Kind.OTHER;
	}

	/**
	 * The category of official document answering a piece, or null. The most
	 * precise words first: an « attestation de vigilance URSSAF » is social too.
	 */
	static CompanyDocument.Category category(String label) {
		if (kind(label) != Kind.OTHER) {
			return null;
		}
		String text = plain(label);
		if (text.contains("kbis") || text.contains("k-bis") || text.contains("registre du commerce")) {
			return CompanyDocument.Category.KBIS;
		}
		if (text.contains("vigilance") || text.contains("urssaf")) {
			return CompanyDocument.Category.VIGILANCE;
		}
		if (text.contains("assurance") || text.contains("decennale") || text.contains("responsabilite civile")) {
			return CompanyDocument.Category.INSURANCE;
		}
		if (text.contains("fiscal") || text.contains("impot")) {
			return CompanyDocument.Category.TAX;
		}
		if (text.contains("social") || text.contains("cotisation")) {
			return CompanyDocument.Category.SOCIAL;
		}
		if (text.contains("bilan") || text.contains("chiffre d'affaires") || text.contains("chiffre d’affaires")) {
			return CompanyDocument.Category.FINANCIAL;
		}
		if (text.contains("charte") || text.contains("prevention des risques")) {
			return CompanyDocument.Category.SAFETY;
		}
		if (text.contains("moyens humains") || text.contains("effectif")) {
			return CompanyDocument.Category.STAFF;
		}
		if (text.contains("moyens materiels") || text.contains("materiel")) {
			return CompanyDocument.Category.EQUIPMENT;
		}
		if (text.contains("reference") || text.contains("realisation") || text.contains("presentation de l'entreprise")) {
			return CompanyDocument.Category.REFERENCES;
		}
		if (text.contains("qualibat") || text.contains("qualifelec") || text.contains("certificat")
				|| text.contains("qualification") || text.contains("rge")) {
			return CompanyDocument.Category.CERTIFICATE;
		}
		return null;
	}

	private static String plain(String label) {
		return Normalizer.normalize(label, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase();
	}

}
