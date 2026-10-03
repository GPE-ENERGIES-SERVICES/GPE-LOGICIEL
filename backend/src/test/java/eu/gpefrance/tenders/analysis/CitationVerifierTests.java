package eu.gpefrance.tenders.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CitationVerifierTests {

	private static final Map<Integer, String> PAGES = Map.of(1, "Règlement de la consultation\nObjet : travaux",
			2, "Les offres devront par-\nvenir avant le 15 novembre 2026 à 12h00.\nLa visite est obliga-",
			3, "toire pour tous les candidats.", 4, "Article 7 – Critères : Prix des presta­tions : 60 %");

	@Test
	void findsTheQuoteInTheCitedPageDespiteLineBreaksAndAccents() {
		assertThat(CitationVerifier.locate("Les offres devront parvenir avant le 15 novembre 2026 à 12h00", PAGES, 2))
			.contains(2);
	}

	@Test
	void findsAQuoteThatContinuesOnTheNextPage() {
		assertThat(CitationVerifier.locate("La visite est obligatoire pour tous les candidats", PAGES, 2)).contains(2);
	}

	@Test
	void correctsTheCitedPageWhenTheQuoteIsElsewhereInTheDocument() {
		assertThat(CitationVerifier.locate("Critères : Prix des prestations : 60 %", PAGES, 1)).contains(4);
	}

	@Test
	void acceptsWordsSkippedWithAnEllipsis() {
		assertThat(CitationVerifier.locate("Les offres devront parvenir […] à 12h00", PAGES, 2)).contains(2);
	}

	@Test
	void refusesAQuoteThatIsNotInTheDocument() {
		assertThat(CitationVerifier.locate("Les offres devront parvenir avant le 16 novembre 2026", PAGES, 2)).isEmpty();
		assertThat(CitationVerifier.locate("Objet : travaux", PAGES, null)).contains(1);
	}

	@Test
	void refusesAQuoteTooShortToProveAnything() {
		assertThat(CitationVerifier.locate("Prix", PAGES, 4)).isEmpty();
		assertThat(CitationVerifier.locate(null, PAGES, 4)).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { "Date limite : 15/11/2026 à 12h00", "le 15.11.26 à 12 h", "le 15 novembre 2026 à 12:00",
			"avant le 15 novembre à midi", "15-11-2026, 12 heures" })
	void readsADeadlineWrittenInDifferentWays(String quote) {
		assertThat(CitationVerifier.valueMatches(FieldKind.DATE_TIME, "2026-11-15T12:00", quote)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "Date limite : 16/11/2026 à 12h00", "le 15/11/2026 à 12h30", "le 15/12/2026 à 12h00",
			"le 15 novembre 2026" })
	void refusesADeadlineThatDiffersFromTheQuote(String quote) {
		assertThat(CitationVerifier.valueMatches(FieldKind.DATE_TIME, "2026-11-15T12:00", quote)).isFalse();
	}

	@Test
	void readsADateWithoutTimeAndTheFirstOfTheMonth() {
		assertThat(CitationVerifier.valueMatches(FieldKind.DATE_TIME, "2026-12-01", "jusqu'au 1er décembre 2026"))
			.isTrue();
		assertThat(CitationVerifier.valueMatches(FieldKind.DATE, "2026-12-01", "le 01/12/2026")).isTrue();
		assertThat(CitationVerifier.valueMatches(FieldKind.DATE_TIME, "15/11/2026", "le 15/11/2026")).isFalse();
	}

	@Test
	void readsAnAmountWithThousandsSeparators() {
		assertThat(CitationVerifier.valueMatches(FieldKind.AMOUNT, "125000", "Montant estimé : 125 000,00 € HT"))
			.isTrue();
		assertThat(CitationVerifier.valueMatches(FieldKind.AMOUNT, "125000", "estimé à 125.000 euros")).isTrue();
		assertThat(CitationVerifier.valueMatches(FieldKind.AMOUNT, "125000", "Montant estimé : 152 000 € HT"))
			.isFalse();
	}

	@Test
	void onlyChecksTheQuoteOfTextAndChoices() {
		assertThat(CitationVerifier.valueMatches(FieldKind.CHOICE, "interdites", "Les variantes ne sont pas autorisées"))
			.isTrue();
		assertThat(CitationVerifier.valueMatches(FieldKind.TEXT, null, "texte")).isFalse();
	}

}
