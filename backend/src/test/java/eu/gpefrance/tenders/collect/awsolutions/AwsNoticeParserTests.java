package eu.gpefrance.tenders.collect.awsolutions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import eu.gpefrance.tenders.collect.NoticeType;
import eu.gpefrance.tenders.collect.SourceNotice;
import eu.gpefrance.tenders.tender.MarketType;
import eu.gpefrance.tenders.tender.ProcedureType;
import eu.gpefrance.tenders.tender.TenderDetails;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

class AwsNoticeParserTests {

	private final AwsNoticeParser parser = new AwsNoticeParser(JsonMapper.builder().build());

	@Test
	void readsANoticeOfTheCantal() {
		SourceNotice notice = parser.parse(stored(cantalListing(), AwsPages.notice(AwsPagesTests.page("notice-mpi.html"))));

		assertThat(notice.externalId()).isEqualTo("IDM-1892875");
		assertThat(notice.type()).isEqualTo(NoticeType.CALL_FOR_TENDERS);
		assertThat(notice.procedureId()).isNull();
		TenderDetails details = notice.details();
		assertThat(details.title()).isEqualTo("Travaux de plantations de haies et d'alignement d'arbres");
		assertThat(details.buyer()).isEqualTo("Conseil départemental du Cantal");
		assertThat(details.reference()).isEqualTo("2026TXSSTA0178");
		assertThat(details.location()).isEqualTo("Cantal");
		assertThat(details.departments()).containsExactly("15");
		assertThat(details.marketTypes()).containsExactly(MarketType.WORKS);
		assertThat(details.procedureType()).isEqualTo(ProcedureType.ADAPTED);
		assertThat(details.cpvCodes()).containsExactly("45112730");
		assertThat(details.publishedAt()).isEqualTo(LocalDate.of(2026, 10, 21));
		assertThat(details.deadline()).isEqualTo(Instant.parse("2026-11-09T11:00:00Z"));
		assertThat(details.description()).contains("Chaque lot fera l'objet d'une attribution séparée.",
				"Durée : 42 mois");
		assertThat(details.noticeUrl()).isEqualTo("https://www.marches-publics.info/Annonces/MPI-pub-20262941419.htm");
		assertThat(details.platformUrl()).contains("type=DCE", "IDM=1892875");
	}

	@Test
	void keepsTheComplementaryCpvCodes() {
		SourceNotice notice = parser.parse(stored(cantalListing(), AwsPages.notice(AwsPagesTests.page("notice-ldm.html"))));

		assertThat(notice.details().cpvCodes()).containsExactly("45210000", "45454100");
		assertThat(notice.details().departments()).containsExactly("31");
		assertThat(notice.details().deadline()).isEqualTo(Instant.parse("2026-11-13T11:00:00Z"));
	}

	@Test
	void fallsBackOnTheListWithoutThePageOfTheNotice() {
		ObjectNode listing = cantalListing();
		listing.remove("consultationId");

		SourceNotice notice = parser.parse(stored(listing, JsonNodeFactory.instance.objectNode()));

		assertThat(notice.externalId()).isEqualTo("MPI-pub-20262941419");
		assertThat(notice.details().title()).isEqualTo("Travaux de plantations");
		assertThat(notice.details().reference()).isEqualTo("2026TXSSTA0178");
		assertThat(notice.details().departments()).containsExactly("15");
		assertThat(notice.details().deadline()).isEqualTo(Instant.parse("2026-11-09T11:00:00Z"));
	}

	@Test
	void readsACancellationOfItsConsultation() {
		ObjectNode page = AwsPages.notice(AwsPagesTests.page("notice-mpi.html"));
		page.put("heading", "AVIS D'ANNULATION");

		SourceNotice notice = parser.parse(stored(cantalListing(), page));

		assertThat(notice.type()).isEqualTo(NoticeType.CANCELLATION);
		assertThat(notice.linkedIds()).containsExactly("IDM-1892875");
	}

	@Test
	void refusesANoticeWithoutPage() {
		ObjectNode listing = cantalListing();
		listing.remove("noticeId");

		assertThatThrownBy(() -> parser.parse(stored(listing, JsonNodeFactory.instance.objectNode())))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void updatesItsTendersAndProposesMatchesByReference() {
		assertThat(parser.republishesChangedNotices()).isTrue();
		assertThat(parser.proposesMatchesByReference()).isTrue();
		assertThat(parser.linksNoticesByProcedure()).isFalse();
		assertThat(parser.defersToOtherSources()).isFalse();
	}

	/** The Cantal notice as shown in the list of 21/10/2026. */
	static ObjectNode cantalListing() {
		ObjectNode listing = JsonNodeFactory.instance.objectNode();
		listing.put("publishedOn", "21/10/26");
		listing.put("deadline", "09/11/26 à 12h00");
		listing.put("buyer", "Conseil départemental du Cantal");
		listing.put("postCode", "15000");
		listing.put("reference", "2026TXSSTA0178");
		listing.put("title", "Travaux de plantations");
		listing.put("lots", "2");
		listing.put("noticeUrl", "https://www.marches-publics.info/Annonces/MPI-pub-20262941419.htm");
		listing.put("noticeId", "MPI-pub-20262941419");
		listing.put("dceUrl",
				"https://www.marches-publics.info/mpiaws/index.cfm?fuseaction=dematEnt.login&type=DCE&IDM=1892875");
		listing.put("consultationId", "1892875");
		return listing;
	}

	static String stored(ObjectNode listing, ObjectNode notice) {
		ObjectNode stored = JsonNodeFactory.instance.objectNode();
		stored.put("collectedOn", "2026-10-21");
		stored.set("listing", listing);
		stored.set("notice", notice);
		return stored.toString();
	}

}
