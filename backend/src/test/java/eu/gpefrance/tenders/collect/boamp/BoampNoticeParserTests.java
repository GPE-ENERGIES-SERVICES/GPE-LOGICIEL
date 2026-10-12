package eu.gpefrance.tenders.collect.boamp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import eu.gpefrance.tenders.collect.NoticeType;
import eu.gpefrance.tenders.collect.SourceNotice;
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
		SourceNotice notice = parse("eforms-works");

		assertThat(notice.externalId()).isEqualTo("26-94229");
		assertThat(notice.type()).isEqualTo(NoticeType.CALL_FOR_TENDERS);
		assertThat(notice.linkedIds()).isEmpty();
		TenderDetails details = notice.details();
		assertThat(details.title()).startsWith("Marché Global Sectoriel pour le projet de restructuration");
		assertThat(details.description()).startsWith("La présente consultation est lancée par l'établissement support");
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
		assertThat(details.description()).startsWith("Les prestations objet du marché concernent les points de livraison");
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

		assertThat(details.description()).startsWith("La présente consultation concerne des prestations d'impression");
		assertThat(details.lots()).hasSize(4);
		assertThat(details.lots()).extracting(LotDetails::number).containsExactly("1", "2", "3", "4");
		assertThat(details.lots()).extracting(LotDetails::estimatedAmount)
			.containsExactly(new BigDecimal("120000"), new BigDecimal("10000"), new BigDecimal("20000"),
					new BigDecimal("48000"));
		assertThat(details.lots().get(2).title()).isEqualTo("03 : Impression de flyers, dépliants et affiches");
	}

	/**
	 * Real notices of one procedure, both published by the BOAMP with etat =
	 * INITIAL: each is an eForms change notice of the previous one.
	 */
	@Test
	void readsAnEformsChangeNoticePublishedAsInitialAsACompleteRectification() {
		SourceNotice first = parse("change-as-initial-1");
		SourceNotice second = parse("change-as-initial-2");

		assertThat(first.type()).isEqualTo(NoticeType.COMPLETE_RECTIFICATION);
		assertThat(second.type()).isEqualTo(NoticeType.COMPLETE_RECTIFICATION);
		assertThat(second.linkedIds()).containsExactly("26-77966", "26-92510");
		assertThat(second.procedureId()).isEqualTo(first.procedureId())
			.isEqualTo("bac8a6d9-67fe-4707-832a-25485824ecdc");
		assertThat(second.details().deadline()).isEqualTo(Instant.parse("2026-10-16T10:00:00Z"));
	}

	@Test
	void keepsANoticeCitingAnotherWithoutChangeSectionAsACallForTenders() {
		// The same notice without its eForms change section: only cited, as a relaunch is.
		String withoutChange = fixture("change-as-initial-2").replace("efbc:ChangedNoticeIdentifier", "efbc:Other");

		SourceNotice notice = parser.parse(withoutChange);

		assertThat(notice.type()).isEqualTo(NoticeType.CALL_FOR_TENDERS);
		assertThat(notice.linkedIds()).containsExactly("26-77966", "26-92510");
	}

	@Test
	void readsTheProcedureIdentifierOfAnEformsNoticeOnly() {
		assertThat(parse("eforms-works").procedureId()).isEqualTo("9d230084-9a6a-4272-bd8f-a9b7d123fa86");
		assertThat(parse("eforms-change").procedureId()).isEqualTo("5e37d2bf-7ea0-417c-8f45-b4a97b825e1a");
		assertThat(parse("mapa").procedureId()).isNull();
		assertThat(parse("award").procedureId()).isNull();
	}

	@Test
	void parsesAMapaNotice() {
		TenderDetails details = parse("mapa").details();

		assertThat(details.procedureType()).isEqualTo(ProcedureType.ADAPTED);
		assertThat(details.description()).startsWith("Montant HT maximum pour la durée totale du contrat");
		assertThat(details.reference()).isEqualTo("2026M016");
		assertThat(details.location()).isEqualTo("FRE12 - Pas-de-Calais");
		assertThat(details.platformUrl()).isEqualTo("http://www.marches-securises.fr");
		assertThat(details.cpvCodes()).isEmpty();
		assertThat(details.estimatedAmount()).isNull();
	}

	@Test
	void parsesARectificationWithItsChangeAndLink() {
		SourceNotice notice = parse("fns-rectification");

		assertThat(notice.type()).isEqualTo(NoticeType.RECTIFICATION);
		assertThat(notice.linkedIds()).containsExactly("26-87745");
		assertThat(notice.changeDescription()).contains("est reportée au 02/10/2026 à 16H00");
		assertThat(notice.details().deadline()).isNull();
	}

	@Test
	void parsesAnEformsChangeNoticeAsACompleteRectification() {
		SourceNotice notice = parse("eforms-change");

		assertThat(notice.type()).isEqualTo(NoticeType.COMPLETE_RECTIFICATION);
		assertThat(notice.linkedIds()).containsExactly("26-81625", "26-88165");
		assertThat(notice.changeDescription()).isNotBlank();
		assertThat(notice.details().estimatedAmount()).isEqualByComparingTo("420000");
	}

	@Test
	void parsesAnAwardAndACancellation() {
		SourceNotice award = parse("award");
		assertThat(award.type()).isEqualTo(NoticeType.AWARD);
		assertThat(award.linkedIds()).containsExactly("25-14540");

		SourceNotice cancellation = parse("cancellation");
		assertThat(cancellation.type()).isEqualTo(NoticeType.CANCELLATION);
		assertThat(cancellation.linkedIds()).containsExactly("26-89214");
		assertThat(cancellation.details().departments()).containsExactly("974", "976");
	}

	@Test
	void cleansTextsAndKeepsMissingValuesEmpty() {
		SourceNotice notice = parser.parse("""
				{"idweb": "26-1", "nature": "APPEL_OFFRE", "etat": "INITIAL",
				 "objet": "  <p>Travaux&nbsp;de</p>\\n   peinture ", "nomacheteur": "",
				 "code_departement": ["5", "2A", "971"], "type_marche": ["TRAVAUX", "AUTRE"], "donnees": null}
				""");

		TenderDetails details = notice.details();
		assertThat(details.title()).isEqualTo("Travaux de peinture");
		assertThat(details.buyer()).isNull();
		assertThat(details.departments()).containsExactly("05", "2A", "971");
		assertThat(details.marketTypes()).containsExactly(MarketType.WORKS);
		assertThat(details.procedureType()).isNull();
		assertThat(details.deadline()).isNull();
		assertThat(details.estimatedAmount()).isNull();
		assertThat(details.noticeUrl()).isEqualTo("https://www.boamp.fr/pages/avis/?q=idweb:26-1");
	}

	@Test
	void neverTakesALotDescriptionForTheWholeTender() {
		String donnees = """
				{"FNSimple": {"initial": {"natureMarche": {"intitule": "Travaux"}, "lots": {"lot": [
				{"description": "1 : Gros oeuvre"}, {"description": "2 : Electricite"}]}}}}""";
		SourceNotice notice = parser.parse(JsonMapper.builder()
			.build()
			.createObjectNode()
			.put("idweb", "26-3")
			.put("nature", "APPEL_OFFRE")
			.put("objet", "Travaux")
			.put("donnees", donnees)
			.toString());

		assertThat(notice.details().description()).isNull();
		assertThat(notice.details().lots()).hasSize(2);
	}

	@Test
	void takesATitleWrittenInTheLotNumberAsTheTitle() {
		String donnees = """
				{"FNSimple": {"initial": {"natureMarche": {"intitule": "Fournitures"}, "lots": {"lot": [
				{"numLot": "Lot 1 - Maintenance du matériel ELLEX ou équivalent et de ses accessoires"},
				{"numLot": "2", "intitule": "Consommables"}]}}}}""";
		SourceNotice notice = parser.parse(JsonMapper.builder()
			.build()
			.createObjectNode()
			.put("idweb", "26-4")
			.put("nature", "APPEL_OFFRE")
			.put("objet", "Fournitures")
			.put("donnees", donnees)
			.toString());

		assertThat(notice.details().lots()).extracting(LotDetails::number, LotDetails::title)
			.containsExactly(
					tuple("1",
							"Lot 1 - Maintenance du matériel ELLEX ou équivalent et de ses accessoires"),
					tuple("2", "Consommables"));
	}

	@Test
	void neverKeepsAZeroAmount() {
		SourceNotice notice = parser.parse("""
				{"idweb": "26-2", "nature": "APPEL_OFFRE", "objet": "Travaux",
				 "donnees": "{\\"FNSimple\\": {\\"initial\\": {\\"natureMarche\\": {\\"valeurEstimee\\": {\\"@devise\\": \\"EUR\\", \\"valeur\\": \\"0\\"}}}}}"}
				""");

		assertThat(notice.details().estimatedAmount()).isNull();
	}

	private SourceNotice parse(String fixture) {
		return parser.parse(fixture(fixture));
	}

	private String fixture(String name) {
		try (InputStream in = getClass().getResourceAsStream("/boamp/" + name + ".json")) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
