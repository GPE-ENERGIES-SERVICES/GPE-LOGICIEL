package eu.gpefrance.tenders.collect.ted;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
 * The fixtures are real TED notices, as the search API returned them with
 * the fields of {@link TedClient#FIELDS} and stored by the client.
 */
class TedNoticeParserTests {

	private final TedNoticeParser parser = new TedNoticeParser(JsonMapper.builder().build());

	@Test
	void parsesACallForTendersWithItsLots() {
		SourceNotice notice = parse("works-lots");
		TenderDetails details = notice.details();

		assertThat(notice.externalId()).isEqualTo("698125-2026");
		assertThat(notice.type()).isEqualTo(NoticeType.CALL_FOR_TENDERS);
		assertThat(notice.linkedIds()).isEmpty();
		assertThat(notice.procedureId()).isEqualTo("5c87fdc2-d20a-42e9-9b11-59f45d4a37af");
		assertThat(notice.changeDescription()).isNull();
		assertThat(details.title()).isEqualTo(
				"Fourniture et mise en oeuvre de matériaux bitumineux coulé à chaud pour les besoins du département de la Manche");
		assertThat(details.buyer()).isEqualTo("Département de la Manche");
		assertThat(details.reference()).isEqualTo("DR-104-26");
		assertThat(details.location()).isEqualTo("Saint-Lo");
		assertThat(details.departments()).containsExactly("50");
		assertThat(details.marketTypes()).containsExactly(MarketType.WORKS);
		assertThat(details.procedureType()).isEqualTo(ProcedureType.OPEN);
		assertThat(details.cpvCodes()).containsExactly("45233220", "45200000");
		assertThat(details.estimatedAmount()).isEqualByComparingTo(new BigDecimal("10000000"));
		assertThat(details.publishedAt()).isEqualTo(LocalDate.of(2026, 10, 9));
		assertThat(details.deadline()).isEqualTo(Instant.parse("2026-11-10T11:00:00Z"));
		assertThat(details.noticeUrl()).isEqualTo("https://ted.europa.eu/fr/notice/-/detail/698125-2026");
		assertThat(details.platformUrl()).isEqualTo(
				"https://www.marches-publics.info/mpiaws/index.cfm?fuseaction=dematEnt.login&type=DCE&IDM=1885479");
		assertThat(details.lots()).extracting(LotDetails::number).containsExactly("1", "2", "3", "4", "5");
		assertThat(details.lots().getFirst().title()).isEqualTo("Lot 1 : ATD du Cotentin");
	}

	@Test
	void parsesAChangeNoticeAsACompleteRectificationLinkedByItsProcedure() {
		SourceNotice notice = parse("change");

		assertThat(notice.type()).isEqualTo(NoticeType.COMPLETE_RECTIFICATION);
		// Cited by its eForms identifier, not a publication number: the procedure links it.
		assertThat(notice.linkedIds()).isEmpty();
		assertThat(notice.procedureId()).isEqualTo("9f6c99ee-0017-44b8-9d56-a84d1d115566");
		assertThat(notice.changeDescription()).startsWith("La rubrique Date limite de réception des offres")
			.doesNotContain("\n");
		assertThat(notice.details().deadline()).isEqualTo(Instant.parse("2026-10-14T10:00:00Z"));
		assertThat(notice.details().estimatedAmount()).isNull();
		assertThat(notice.details().location()).isEqualTo("ISERE");
		assertThat(notice.details().departments()).containsExactly("38");
		assertThat(notice.details().lots()).extracting(LotDetails::number, LotDetails::title)
			.containsExactly(tuple("1", "Secteur Oisans"),
					tuple("2", "Secteur Vercors"),
					tuple("3", "secteur Matheysine"),
					tuple("4", "secteur Grésivaudan"));
	}

	@Test
	void keepsTheChangedNoticeWhenItIsCitedByItsPublicationNumber() {
		String payload = fixture("change").replace("\"4bbb43f2-3f35-43a8-a2c8-d791fbb00b27-01\"", "\"601234-2026\"");

		assertThat(parser.parse(payload).linkedIds()).containsExactly("601234-2026");
	}

	@Test
	void parsesACancellationAnAwardAndANoticeOutOfScope() {
		assertThat(parse("cancellation").type()).isEqualTo(NoticeType.CANCELLATION);
		assertThat(parse("cancellation").details().departments()).containsExactly("34");
		SourceNotice award = parse("award");
		assertThat(award.type()).isEqualTo(NoticeType.AWARD);
		assertThat(award.details().lots()).isEmpty();
		// No place of performance: the department of the buyer.
		assertThat(award.details().departments()).containsExactly("92");
		assertThat(parse("other").type()).isEqualTo(NoticeType.OTHER);
	}

	@Test
	void parsesAQualificationSystemAsACallForCompetition() {
		SourceNotice notice = parse("qualification-system");

		assertThat(notice.type()).isEqualTo(NoticeType.CALL_FOR_TENDERS);
		assertThat(notice.procedureId()).isEqualTo("923f310c-f712-4fcd-97b6-d3d5600083d6");
		assertThat(notice.details().title()).isEqualTo("SQE Travaux et maintenance électrique dans le domaine de la HTB");
		assertThat(notice.details().procedureType()).isEqualTo(ProcedureType.QUALIFICATION_SYSTEM);
		assertThat(notice.details().marketTypes()).containsExactly(MarketType.WORKS);
		assertThat(notice.details().departments()).containsExactly("69");
		// No tender yet: the deadline is the one of the applications.
		assertThat(notice.details().deadline()).isEqualTo(Instant.parse("2026-02-09T10:00:00Z"));
		assertThat(notice.details().platformUrl()).isEqualTo("https://portal.cnr.oalia.com/portal");
	}

	@Test
	void parsesAPriorInformationNoticeUsedAsACallForCompetition() {
		SourceNotice notice = parse("prior-information-call");

		assertThat(notice.type()).isEqualTo(NoticeType.CALL_FOR_TENDERS);
		assertThat(notice.details().procedureType()).isEqualTo(ProcedureType.PRIOR_INFORMATION_CALL);
		assertThat(notice.details().title()).startsWith("CONSTRUCTION D'UN POLE VIANDE INTERCOMMUNAL");
		assertThat(notice.details().departments()).containsExactly("43");
		assertThat(notice.details().deadline()).isNull();
		// Changed, it is a complete rectification like a contract notice.
		String changed = fixture("prior-information-call").replace("\"notice-version\": 1",
				"\"notice-version\": 2, \"change-notice-version-identifier\": \"39000-2026\"");
		assertThat(parser.parse(changed).type()).isEqualTo(NoticeType.COMPLETE_RECTIFICATION);
	}

	@Test
	void stillIgnoresThePriorInformationNoticesThatOpenNoCompetition() {
		String priorInformationOnly = fixture("prior-information-call").replace("\"pin-cfc-standard\"", "\"pin-only\"");

		assertThat(parser.parse(priorInformationOnly).type()).isEqualTo(NoticeType.OTHER);
	}

	@Test
	void findsTheDepartmentOfAPostCode() {
		assertThat(TedNoticeParser.departmentOfPostCode("02100")).isEqualTo("02");
		assertThat(TedNoticeParser.departmentOfPostCode("69269")).isEqualTo("69");
		assertThat(TedNoticeParser.departmentOfPostCode("20090")).isEqualTo("2A");
		assertThat(TedNoticeParser.departmentOfPostCode("20200")).isEqualTo("2B");
		assertThat(TedNoticeParser.departmentOfPostCode("97400")).isEqualTo("974");
		assertThat(TedNoticeParser.departmentOfPostCode("97500")).isNull();
		assertThat(TedNoticeParser.departmentOfPostCode("98000")).isNull();
		assertThat(TedNoticeParser.departmentOfPostCode("L-1234")).isNull();
		assertThat(TedNoticeParser.departmentOfPostCode(null)).isNull();
	}

	@Test
	void findsTheDepartmentOfEveryFrenchNutsRegion() {
		assertThat(NutsDepartments.departmentOf("FRD12")).isEqualTo("50");
		assertThat(NutsDepartments.departmentOf("FR101")).isEqualTo("75");
		assertThat(NutsDepartments.departmentOf("FRM02")).isEqualTo("2B");
		assertThat(NutsDepartments.departmentOf("FRY50")).isEqualTo("976");
		assertThat(NutsDepartments.departmentOf("FRK")).isNull();
		assertThat(NutsDepartments.departmentOf("DE300")).isNull();
	}

	@Test
	void takesTheNutsRegionWhenThePlaceHasNoPostCode() {
		String payload = """
				{"publication-number": "1-2026", "notice-type": "cn-standard",
				 "place-of-performance-subdiv-proc": ["FRK24", "FRK26"], "buyer-post-code": ["75001"],
				 "deadline-receipt-tender-date-lot": ["2026-11-10+01:00"]}""";

		TenderDetails details = parser.parse(payload).details();

		assertThat(details.departments()).containsExactly("38", "69");
		assertThat(details.title()).isNull();
		// A date without time: the end of that day, Paris time.
		assertThat(details.deadline()).isEqualTo(Instant.parse("2026-11-10T22:59:00Z"));
	}

	@Test
	void takesTheDeadlineOfTheRequestsToParticipateWhenNoTenderIsAskedYet() {
		String payload = """
				{"publication-number": "1-2026", "notice-type": "cn-standard", "procedure-type": "neg-w-call",
				 "deadline-receipt-request-date-lot": ["2026-10-30+01:00", "2026-10-28+01:00"],
				 "deadline-receipt-request-time-lot": ["12:00:00+01:00", "16:00:00+01:00"]}""";

		assertThat(parser.parse(payload).details().deadline()).isEqualTo(Instant.parse("2026-10-28T15:00:00Z"));
		assertThat(parse("works-lots").details().deadline()).isEqualTo(Instant.parse("2026-11-10T11:00:00Z"));
	}

	@Test
	void readsThePlatformAddressAsTheBoampAddresses() {
		String withoutScheme = """
				{"publication-number": "1-2026", "notice-type": "cn-standard",
				 "document-url-lot": ["www.marches-securises.fr"]}""";
		String notAnAddress = """
				{"publication-number": "1-2026", "notice-type": "cn-standard",
				 "document-url-lot": ["voir le règlement"], "buyer-profile": ["https://achats.example.fr"]}""";

		assertThat(parser.parse(withoutScheme).details().platformUrl()).isEqualTo("https://www.marches-securises.fr");
		assertThat(parser.parse(notAnAddress).details().platformUrl()).isEqualTo("https://achats.example.fr");
	}

	@Test
	void refusesANoticeWithoutPublicationNumber() {
		assertThatThrownBy(() -> parser.parse("{\"notice-type\": \"cn-standard\"}"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	private SourceNotice parse(String fixture) {
		return parser.parse(fixture(fixture));
	}

	static String fixture(String name) {
		try (InputStream in = TedNoticeParserTests.class.getResourceAsStream("/ted/" + name + ".json")) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
