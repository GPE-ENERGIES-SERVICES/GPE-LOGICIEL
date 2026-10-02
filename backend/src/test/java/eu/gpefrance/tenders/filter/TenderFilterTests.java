package eu.gpefrance.tenders.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import eu.gpefrance.tenders.tender.LotDetails;
import eu.gpefrance.tenders.tender.MarketType;
import eu.gpefrance.tenders.tender.Tender;
import eu.gpefrance.tenders.tender.TenderDetails;

class TenderFilterTests {

	private static final Instant NOW = Instant.parse("2026-10-05T08:00:00Z");

	private final FilterCriteria criteria = new FilterCriteria(List.of("69", "01"), List.of(MarketType.WORKS), 7,
			List.of("4531"), List.of("électricité", "borne de recharge"), List.of("nettoyage"), null,
			new BigDecimal("2000000"), NOW);

	@Test
	void retainsATenderMeetingAllCriteriaAndExplainsWhy() {
		Tender tender = tender().title("Travaux d'ELECTRICITE au gymnase").cpv("45310000").build();

		FilterResult result = TenderFilter.apply(tender, criteria, NOW);

		assertThat(result.retained()).isTrue();
		assertThat(result.reason()).isEqualTo(
				"Retenu : département 69 ; montant non indiqué ; CPV 45310000 ; mot-clé « électricité »");
	}

	@Test
	void excludesATenderOutsideTheZone() {
		FilterResult result = TenderFilter.apply(tender().departments("13").cpv("45310000").build(), criteria, NOW);

		assertThat(result.retained()).isFalse();
		assertThat(result.reason()).isEqualTo("Écarté : hors zone (13)");
	}

	@Test
	void excludesAnotherMarketType() {
		Tender tender = tender().marketTypes(MarketType.SERVICES).cpv("45310000").build();

		assertThat(TenderFilter.apply(tender, criteria, NOW).reason())
			.isEqualTo("Écarté : type de marché non suivi (services)");
	}

	@Test
	void excludesATenderWithTooLittleTimeLeft() {
		Tender tender = tender().deadline(NOW.plus(Duration.ofDays(3))).cpv("45310000").build();

		assertThat(TenderFilter.apply(tender, criteria, NOW).reason())
			.isEqualTo("Écarté : délai trop court (3 jours restants)");
	}

	@Test
	void requiresAtLeastOneBusinessCriterion() {
		Tender tender = tender().title("Réfection de toiture").cpv("45261000").build();

		FilterResult result = TenderFilter.apply(tender, criteria, NOW);

		assertThat(result.retained()).isFalse();
		assertThat(result.reason()).isEqualTo("Écarté : aucun code CPV ni mot-clé métier");
	}

	@Test
	void findsKeywordsInTheDescriptionAndTheLots() {
		Tender inLot = tender().title("Construction d'un parking")
			.lots("Gros œuvre", "Bornes de recharge pour véhicules électriques")
			.build();
		Tender inDescription = tender().title("Réhabilitation d'un gymnase")
			.description("Lot unique : électricité courants forts et faibles")
			.build();

		assertThat(TenderFilter.apply(inLot, criteria, NOW).reason())
			.isEqualTo("Retenu : département 69 ; montant non indiqué ; mot-clé « borne de recharge »");
		assertThat(TenderFilter.apply(inDescription, criteria, NOW).retained()).isTrue();
	}

	@Test
	void excludesATenderWithAnExcludedKeyword() {
		Tender tender = tender().title("Nettoyage et électricité des locaux").build();

		assertThat(TenderFilter.apply(tender, criteria, NOW).reason())
			.isEqualTo("Écarté : mot-clé exclu « nettoyage »");
	}

	@Test
	void neverExcludesATenderForMissingInformation() {
		Tender tender = tender().departments().marketTypes().deadline(null).title("Travaux d'électricité").build();

		FilterResult result = TenderFilter.apply(tender, criteria, NOW);

		assertThat(result.retained()).isTrue();
		assertThat(result.reason()).isEqualTo("Retenu : département non indiqué ; type de marché non indiqué ; "
				+ "date limite non indiquée ; montant non indiqué ; mot-clé « électricité »");
	}

	@Test
	void appliesTheAmountRangeOnlyWhenTheAmountIsKnown() {
		Tender tooBig = tender().cpv("45310000").amount("2500000").build();
		Tender inRange = tender().cpv("45310000").amount("150000").build();

		assertThat(TenderFilter.apply(tooBig, criteria, NOW).reason())
			.isEqualTo("Écarté : montant supérieur au maximum");
		assertThat(TenderFilter.apply(inRange, criteria, NOW).retained()).isTrue();
	}

	@Test
	void retainsEverythingWhenNoCriterionIsDefined() {
		FilterCriteria none = new FilterCriteria(List.of(), List.of(), null, List.of(), List.of(), List.of(), null,
				null, NOW);

		FilterResult result = TenderFilter.apply(tender().departments("13").build(), none, NOW);

		assertThat(result.retained()).isTrue();
		assertThat(result.reason()).isEqualTo("Retenu : aucun critère défini");
	}

	@Test
	void matchesKeywordsAtTheBeginningOfWordsOnly() {
		TextMatcher text = new TextMatcher("Rénovation du réseau d'éclairage photovoltaïque");

		assertThat(text.contains("photovolt")).isTrue();
		assertThat(text.contains("RESEAU")).isTrue();
		assertThat(text.contains("eau")).isFalse();
		assertThat(text.contains("  ")).isFalse();
		assertThat(new TextMatcher("Bornes de recharge").contains("borne de recharge")).isTrue();
		assertThat(new TextMatcher("Borne et recharge").contains("borne de recharge")).isFalse();
	}

	private static TenderBuilder tender() {
		return new TenderBuilder();
	}

	private static final class TenderBuilder {

		private String title = "Travaux divers";

		private String description;

		private List<String> departments = List.of("69");

		private List<MarketType> marketTypes = List.of(MarketType.WORKS);

		private List<String> cpvCodes = List.of();

		private BigDecimal amount;

		private Instant deadline = NOW.plus(Duration.ofDays(30));

		private List<LotDetails> lots = List.of();

		TenderBuilder title(String title) {
			this.title = title;
			return this;
		}

		TenderBuilder description(String description) {
			this.description = description;
			return this;
		}

		TenderBuilder departments(String... departments) {
			this.departments = List.of(departments);
			return this;
		}

		TenderBuilder marketTypes(MarketType... marketTypes) {
			this.marketTypes = List.of(marketTypes);
			return this;
		}

		TenderBuilder cpv(String... codes) {
			this.cpvCodes = List.of(codes);
			return this;
		}

		TenderBuilder amount(String amount) {
			this.amount = new BigDecimal(amount);
			return this;
		}

		TenderBuilder deadline(Instant deadline) {
			this.deadline = deadline;
			return this;
		}

		TenderBuilder lots(String... titles) {
			this.lots = java.util.stream.IntStream.range(0, titles.length)
				.mapToObj(i -> new LotDetails(String.valueOf(i + 1), titles[i], null))
				.toList();
			return this;
		}

		Tender build() {
			return new Tender("BOAMP", "26-1", new TenderDetails(title, description, null, null, null, departments,
					marketTypes, null, cpvCodes, amount, null, deadline, null, null, lots), NOW);
		}

	}

}
