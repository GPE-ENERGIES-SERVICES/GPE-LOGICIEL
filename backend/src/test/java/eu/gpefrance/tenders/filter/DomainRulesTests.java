package eu.gpefrance.tenders.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import eu.gpefrance.tenders.tender.MarketType;
import eu.gpefrance.tenders.tender.Tender;
import eu.gpefrance.tenders.tender.TenderDetails;

class DomainRulesTests {

	@Test
	void findsIrveApartFromCfoCfa() {
		assertThat(DomainRules.of(tender("Fourniture et pose de bornes de recharge pour véhicules électriques",
				List.of()))).containsExactly(DomainRules.IRVE);
		assertThat(DomainRules.of(tender("Chargeurs", List.of("31681500")))).containsExactly(DomainRules.IRVE);
		assertThat(DomainRules.of(tender("Rénovation TGBT et installation IRVE du parking", List.of())))
			.containsExactly(DomainRules.ELECTRICITY, DomainRules.IRVE);
		assertThat(DomainRules.of(tender("Ombrières photovoltaïques", List.of())))
			.containsExactly(DomainRules.PHOTOVOLTAIC);
	}

	private static Tender tender(String title, List<String> cpvCodes) {
		return new Tender("BOAMP", "26-1", new TenderDetails(title, null, null, null, null, List.of("13"),
				List.of(MarketType.WORKS), null, cpvCodes, null, null, null, null, null, List.of()), Instant.EPOCH);
	}

}
