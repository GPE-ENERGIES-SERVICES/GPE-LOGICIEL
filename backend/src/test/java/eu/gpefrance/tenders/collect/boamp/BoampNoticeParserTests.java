package eu.gpefrance.tenders.collect.boamp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import eu.gpefrance.tenders.tender.LotDetails;
import eu.gpefrance.tenders.tender.MarketType;
import eu.gpefrance.tenders.tender.ProcedureType;
import eu.gpefrance.tenders.tender.TenderDetails;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs the parser on real notices published on the BOAMP, one per schema
 * and per kind of notice.
 */
class BoampNoticeParserTests {

	private final BoampNoticeParser parser = new BoampNoticeParser(JsonMapper.builder().build());

	@Test
	void parsesAnEformsWorksNotice() {
		ParsedNotice notice = parse("eforms-works");

		assertThat(notice.idweb()).isEqualTo("26-94229");
		assertThat(notice.type()).isEqualTo(BoampNoticeType.CALL_FOR_TENDERS);
		assertThat(notice.linkedIds()).isEmpty();
		TenderDetails details = notice.details();
		assertThat(details.title()).startsWith("Marché Global Sectoriel pour le projet de restructuration");
		assertThat(details.buyer()).isEqualTo("Centre Hospitalier de Valence - Etablissement support GHT DAV");
		assertThat(details.departments()).containsExactly("26");
		assertThat(details.marketTypes()).containsExactly(MarketType.WORKS);
		assertThat(details.procedureType()).isEqualTo(ProcedureType.COMPETITIVE_DIALOGUE);
		assertThat(details.cpvCodes()).containsExactly("45215100", "71200000", "71312000");
		assertThat(details.estimatedAmount()).isEqualByComparingTo("17500000");
		assertThat(details.publishedAt()).isEqualTo(LocalDate.of(2026, 10, 1));
		assertThat(details.noticeUrl()).isEqualTo("https://www.boamp.fr/pages/avis/?q=idweb:26-94229");
		assertThat(details.platformUrl()).isEqualTo(
				"https://www.marches-publics.info/mpiaws/index.cfm?fuseaction=dematEnt.login&type=DCE&IDM=1881891");
		assertThat(details.lots()).isEmpty();
	}

	@Test
	void readsTheDeadlineAsWrittenByTheBuyerInParisTime() {
		// Published "16:00:00+01:00" while Paris is still on winter time on November 4
		assertThat(parse("eforms-works").details().deadline()).isEqualTo(Instant.parse("2026-11-04T15:00:00Z"));
		// Published "12:00:00+01:00" on October 14, when Paris is on summer time
		assertThat(parse("eforms-change").details().deadline()).isEqualTo(Instant.parse("2026-10-14T10:00:00Z"));
		// FNSimple gives a local time without offset: 12:00 in Paris
		assertThat(parse("fns-initial").details().deadline()).isEqualTo(Instant.parse("2026-11-03T11:00:00Z"));
		assertThat(parse("mapa").details().deadline()).isEqualTo(Instant.parse("2026-10-14T10:00:00Z"));
	}

	@Test
	void parsesTheLotsOfAnEformsNotice() {
		TenderDetails details = parse("eforms-services").details();

		assertThat(details.location()).isEqualTo("LESTREM");
		assertThat(details.cpvCodes()).startsWith("66510000");
		assertThat(details.lots()).extracting(LotDetails::number).containsExactly("1", "2", "3", "4");
		assertThat(details.lots().getFirst().title()).isEqualTo("LOT 1 - DOMMAGES AUX BIENS");
	}

	@Test
	void parsesAFnsimpleNotice() {
		TenderDetails details = parse("fns-initial").details();

		assertThat(details.title()).isEqualTo("Fourniture et acheminement d'énergie électrique");
		assertThat(details.buyer()).isEqualTo("Commune d'Albiez-Montrond (73)");
		assertThat(details.reference()).isEqualTo("ALBIEZ-MONTROND_73_A_20260923_1");
		assertThat(details.location()).isEqualTo("ALBIEZ-MONTROND");
		assertThat(details.marketTypes()).containsExactly(MarketType.SUPPLIES);
		assertThat(details.procedureType()).isEqualTo(ProcedureType.OPEN);
		assertThat(details.cpvCodes()).containsExactly("09310000");
		assertThat(details.platformUrl()).isEqualTo("https://www.marches-securises.fr/");
	}

	@Test
	void keepsAnAmountGivenAsARangeAsNotIndicated() {
		assertThat(parse("fns-initial").details().estimatedAmount()).isNull();
	}

	@Test
	void parsesTheLotsOfAFnsimpleNoticeWithTheirAmounts() {
		TenderDetails details = parse("fns-lots").details();

		assertThat(details.lots()).hasSize(4);
		assertThat(details.lots()).extracting(LotDetails::number).containsExactly("1", "2", "3", "4");
		assertThat(details.lots()).extracting(LotDetails::estimatedAmount)
			.containsExactly(new BigDecimal("120000"), new BigDecimal("10000"), new BigDecimal("20000"),
					new BigDecimal("48000"));
		assertThat(details.lots().get(2).title()).isEqualTo("03 : Impression de flyers, dépliants et affiches");
	}

	@Test
	void parsesAMapaNotice() {
		TenderDetails details = parse("mapa").details();

		assertThat(details.procedureType()).isEqualTo(ProcedureType.ADAPTED);
		assertThat(details.reference()).isEqualTo("2026M016");
		assertThat(details.location()).isEqualTo("FRE12 - Pas-de-Calais");
		assertThat(details.platformUrl()).isEqualTo("http://www.marches-securises.fr");
		assertThat(details.cpvCodes()).isEmpty();
		assertThat(details.estimatedAmount()).isNull();
	}

	@Test
	void parsesARectificationWithItsChangeAndLink() {
		ParsedNotice notice = parse("fns-rectification");

		assertThat(notice.type()).isEqualTo(BoampNoticeType.RECTIFICATION);
		assertThat(notice.linkedIds()).containsExactly("26-87745");
		assertThat(notice.changeDescription()).contains("est reportée au 02/10/2026 à 16H00");
		assertThat(notice.details().deadline()).isNull();
	}

	@Test
	void parsesAnEformsChangeNoticeAsACompleteRectification() {
		ParsedNotice notice = parse("eforms-change");

		assertThat(notice.type()).isEqualTo(BoampNoticeType.COMPLETE_RECTIFICATION);
		assertThat(notice.linkedIds()).containsExactly("26-81625", "26-88165");
		assertThat(notice.changeDescription()).isNotBlank();
		assertThat(notice.details().estimatedAmount()).isEqualByComparingTo("420000");
	}

	@Test
	void parsesAnAwardAndACancellation() {
		ParsedNotice award = parse("award");
		assertThat(award.type()).isEqualTo(BoampNoticeType.AWARD);
		assertThat(award.linkedIds()).containsExactly("25-14540");

		ParsedNotice cancellation = parse("cancellation");
		assertThat(cancellation.type()).isEqualTo(BoampNoticeType.CANCELLATION);
		assertThat(cancellation.linkedIds()).containsExactly("26-89214");
		assertThat(cancellation.details().departments()).containsExactly("974", "976");
	}

	@Test
	void cleansMarkupAndKeepsMissingValuesEmpty() {
		ParsedNotice notice = parser.parse("""
				{"idweb": "26-1", "nature": "APPEL_OFFRE", "etat": "INITIAL",
				 "objet": "  <p>Travaux&nbsp;de   peinture</p> ", "nomacheteur": "",
				 "code_departement": null, "type_marche": ["TRAVAUX", "AUTRE"], "donnees": null}
				""");

		TenderDetails details = notice.details();
		assertThat(details.title()).isEqualTo("Travaux de peinture");
		assertThat(details.buyer()).isNull();
		assertThat(details.departments()).isEmpty();
		assertThat(details.marketTypes()).containsExactly(MarketType.WORKS);
		assertThat(details.procedureType()).isNull();
		assertThat(details.deadline()).isNull();
		assertThat(details.estimatedAmount()).isNull();
		assertThat(details.noticeUrl()).isEqualTo("https://www.boamp.fr/pages/avis/?q=idweb:26-1");
	}

	@Test
	void neverKeepsAZeroAmount() {
		ParsedNotice notice = parser.parse("""
				{"idweb": "26-2", "nature": "APPEL_OFFRE", "objet": "Travaux",
				 "donnees": "{\\"FNSimple\\": {\\"initial\\": {\\"natureMarche\\": {\\"valeurEstimee\\": {\\"@devise\\": \\"EUR\\", \\"valeur\\": \\"0\\"}}}}}"}
				""");

		assertThat(notice.details().estimatedAmount()).isNull();
	}

	private ParsedNotice parse(String fixture) {
		try (InputStream in = getClass().getResourceAsStream("/boamp/" + fixture + ".json")) {
			return parser.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
