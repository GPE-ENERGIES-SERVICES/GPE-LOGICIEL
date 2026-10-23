package eu.gpefrance.tenders.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import eu.gpefrance.tenders.alert.AlertCriteria;
import eu.gpefrance.tenders.filter.AlertMatch.Outcome;
import eu.gpefrance.tenders.tender.MarketType;
import eu.gpefrance.tenders.tender.Tender;
import eu.gpefrance.tenders.tender.TenderDetails;

/**
 * A saved search evaluated with the rules of the filter of the company, but
 * missing information makes it indeterminate instead of letting it pass.
 */
class AlertMatcherTests {

	private static final Instant NOW = Instant.parse("2026-10-21T08:00:00Z");

	@Test
	void matchesATenderMeetingEveryCriterion() {
		AlertMatch match = AlertMatcher.match(tender("Travaux d'éclairage public", List.of("13"), "45316110", "250000"),
				criteria(List.of("éclairage"), "Provence-Alpes-Côte d'Azur", List.of("13"), List.of(MarketType.WORKS),
						List.of(), List.of("4531"), "100000", "500000"));

		assertThat(match.outcome()).isEqualTo(Outcome.MATCH);
		assertThat(match.reason()).contains("département 13", "mot-clé « éclairage »", "CPV 45316110", "montant");
	}

	@Test
	void keywordsAreAlternativesFoundWithoutAccentsNorCase() {
		AlertMatch match = AlertMatcher.match(tender("BORNES DE RECHARGE pour véhicules", List.of("13"), null, null),
				criteria(List.of("photovoltaïque", "borne de recharge"), null, List.of(), List.of(), List.of(), List.of(),
						null, null));

		assertThat(match.outcome()).isEqualTo(Outcome.MATCH);
	}

	@Test
	void doesNotMatchOutsideTheZoneEvenWhenOtherInformationIsMissing() {
		AlertMatch match = AlertMatcher.match(tender("Travaux d'éclairage", List.of("59"), null, null),
				criteria(List.of(), "Provence-Alpes-Côte d'Azur", List.of(), List.of(), List.of(), List.of("4531"),
						"100000", null));

		assertThat(match.outcome()).isEqualTo(Outcome.NO_MATCH);
		assertThat(match.reason()).contains("hors région Provence-Alpes-Côte d'Azur (59)");
	}

	@Test
	void isIndeterminateWhenTheTenderLacksWhatACriterionNeeds() {
		AlertMatch withoutCpv = AlertMatcher.match(tender("Travaux", List.of("13"), null, null),
				criteria(List.of(), null, List.of(), List.of(), List.of(), List.of("4531"), null, null));
		AlertMatch withoutDepartment = AlertMatcher.match(tender("Travaux", List.of(), "45310000", null),
				criteria(List.of(), null, List.of("13"), List.of(), List.of(), List.of(), null, null));
		AlertMatch withoutAmount = AlertMatcher.match(tender("Travaux", List.of("13"), null, null),
				criteria(List.of(), null, List.of(), List.of(), List.of(), List.of(), "100000", null));

		assertThat(withoutCpv.outcome()).isEqualTo(Outcome.INDETERMINATE);
		assertThat(withoutCpv.reason()).isEqualTo("code CPV non indiqué");
		assertThat(withoutDepartment.outcome()).isEqualTo(Outcome.INDETERMINATE);
		assertThat(withoutAmount.outcome()).isEqualTo(Outcome.INDETERMINATE);
	}

	@Test
	void typesOfWorksAreNotEvaluatedYet() {
		AlertMatch match = AlertMatcher.match(tender("Travaux d'électricité", List.of("13"), "45310000", null),
				criteria(List.of("électricité"), null, List.of(), List.of(), List.of("ELECTRICITY"), List.of(), null, null));

		assertThat(match.outcome()).isEqualTo(Outcome.INDETERMINATE);
		assertThat(match.reason()).isEqualTo("types de travaux non évalués");
	}

	@Test
	void doesNotMatchWithoutAnyKeywordOrWithAnotherCpvOrAmount() {
		assertThat(AlertMatcher.match(tender("Nettoyage des locaux", List.of("13"), null, null),
				criteria(List.of("électricité"), null, List.of(), List.of(), List.of(), List.of(), null, null))
			.outcome()).isEqualTo(Outcome.NO_MATCH);
		assertThat(AlertMatcher.match(tender("Travaux", List.of("13"), "90910000", null),
				criteria(List.of(), null, List.of(), List.of(), List.of(), List.of("4531"), null, null))
			.outcome()).isEqualTo(Outcome.NO_MATCH);
		assertThat(AlertMatcher.match(tender("Travaux", List.of("13"), null, "50000"),
				criteria(List.of(), null, List.of(), List.of(), List.of(), List.of(), "100000", null))
			.outcome()).isEqualTo(Outcome.NO_MATCH);
	}

	@Test
	void anotherMarketTypeDoesNotMatch() {
		assertThat(AlertMatcher.match(tender("Travaux", List.of("13"), null, null),
				criteria(List.of(), null, List.of(), List.of(MarketType.SERVICES), List.of(), List.of(), null, null))
			.outcome()).isEqualTo(Outcome.NO_MATCH);
	}

	private static AlertCriteria criteria(List<String> keywords, String region, List<String> departments,
			List<MarketType> marketTypes, List<String> activities, List<String> cpv, String min, String max) {
		return new AlertCriteria("Veille", keywords, region, departments, marketTypes, activities, cpv,
				min == null ? null : new BigDecimal(min), max == null ? null : new BigDecimal(max));
	}

	private static Tender tender(String title, List<String> departments, String cpv, String amount) {
		return new Tender("BOAMP", "26-1", new TenderDetails(title, null, null, null, null, departments,
				List.of(MarketType.WORKS), null, cpv == null ? List.of() : List.of(cpv),
				amount == null ? null : new BigDecimal(amount), null, NOW.plusSeconds(86_400 * 30), null, null, List.of()),
				NOW);
	}

}
