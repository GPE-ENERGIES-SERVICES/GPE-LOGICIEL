package eu.gpefrance.tenders.tender;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import eu.gpefrance.tenders.ClockConfiguration;
import eu.gpefrance.tenders.TestcontainersConfiguration;
import eu.gpefrance.tenders.TestUsers;

/**
 * The result of a tender: transitions of the follow-up, award recorded once
 * per lot, history of the statuses, « Marchés attribués ».
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AwardApiTests {

	private static final Instant NOW = Instant.parse("2026-10-05T08:00:00Z");

	private static final String WON = """
			{"won": true, "winner": "GPE Énergies & Services", "amount": 146665, "awardedAt": "2026-09-30",
			 "source": "MANUAL", "reason": "Notification reçue"}""";

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private TenderRepository tenders;

	@Autowired
	private JdbcTemplate jdbc;

	@TestBean
	private Clock clock;

	static Clock clock() {
		return Clock.fixed(NOW, ClockConfiguration.ZONE);
	}

	@BeforeEach
	void cleanDatabase() {
		jdbc.execute("TRUNCATE tender_follow, tender_notice, lot, tender, raw_notice RESTART IDENTITY CASCADE");
	}

	@Test
	void refusesToSignOrWinATenderNotAnswered() {
		Tender tender = save("26-1", List.of());

		assertThat(followUp(tender, "{\"status\": \"SIGNED\"}")).hasStatus(HttpStatus.CONFLICT)
			.bodyJson()
			.extractingPath("$.detail")
			.isEqualTo("Passage de « Nouveau » à « Signé » impossible.");
		assertThat(award(tender, WON)).hasStatus(HttpStatus.CONFLICT);
		assertThat(followUp(tender, "{\"status\": \"AWARDED\"}")).hasStatus(HttpStatus.CONFLICT);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM tender_award", Long.class)).isZero();
	}

	@Test
	void addsATenderSetToAwardedToTheAwardedContracts() {
		Tender tender = save("26-1", List.of());
		followUp(tender, "{\"status\": \"SUBMITTED\"}");

		assertThat(followUp(tender, "{\"status\": \"AWARDED\"}")).hasStatusOk();
		// Set again after a correction: still one award.
		followUp(tender, "{\"status\": \"SUBMITTED\"}");
		followUp(tender, "{\"status\": \"AWARDED\"}");

		assertThat(mvc.get().uri("/api/awards").with(TestUsers.user())).bodyJson().isLenientlyEqualTo("""
				{"totalElements": 1, "content": [{"tenderId": %d, "won": true, "amount": null,
				 "awardedAt": "2026-10-05", "source": "MANUAL"}]}""".formatted(tender.getId()));
	}

	@Test
	void turnsATenderWonIntoAnAwardedContractThenSigned() {
		Tender tender = save("26-1", List.of());
		followUp(tender, "{\"status\": \"RESPONDING\"}");
		followUp(tender, "{\"status\": \"SUBMITTED\"}");

		assertThat(award(tender, WON)).hasStatusOk().bodyJson().extractingPath("$.followUpStatus").isEqualTo("AWARDED");
		assertThat(followUp(tender, "{\"status\": \"SIGNED\", \"comment\": \"Signé le 4/10\"}")).hasStatusOk();

		assertThat(mvc.get().uri("/api/tenders/{id}/history", tender.getId()).with(TestUsers.user())).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					[{"oldStatus": "AWARDED", "newStatus": "SIGNED", "origin": "USER", "reason": "Signé le 4/10"},
					 {"oldStatus": "SUBMITTED", "newStatus": "AWARDED", "origin": "USER", "reason": "Notification reçue"},
					 {"oldStatus": "RESPONDING", "newStatus": "SUBMITTED"},
					 {"oldStatus": "NEW", "newStatus": "RESPONDING", "changedAt": "2026-10-05T08:00:00Z"}]""");
		assertThat(mvc.get().uri("/api/awards").with(TestUsers.user())).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{"totalElements": 1, "winnerCount": 1, "totalAmount": 146665,
				 "content": [{"tenderId": %d, "title": "Travaux d'électricité", "winner": "GPE Énergies & Services",
				              "amount": 146665.0, "awardedAt": "2026-09-30", "source": "MANUAL", "won": true,
				              "lotNumber": null}]}""".formatted(tender.getId()));
	}

	@Test
	void recordsAnAwardOncePerLotWhateverTheNumberOfTimes() {
		Tender tender = save("26-1", List.of(new LotDetails("1", "Courants forts", null),
				new LotDetails("2", "Photovoltaïque", null)));
		followUp(tender, "{\"status\": \"SUBMITTED\"}");
		String lot1 = WON.replace("\"source\": \"MANUAL\"", "\"source\": \"NOTICE\", \"lotNumber\": \"1\"");

		award(tender, lot1);
		// The same award notice read again, then corrected: still one award for the lot.
		award(tender, lot1);
		assertThat(award(tender, lot1.replace("146665", "150000"))).hasStatusOk();
		// Lot 2 went to another company: the tender stays won.
		assertThat(award(tender, """
				{"won": false, "winner": "Solaire Concurrent", "amount": 90000, "awardedAt": "2026-09-30",
				 "source": "NOTICE", "lotNumber": "2"}""")).hasStatusOk()
			.bodyJson()
			.extractingPath("$.followUpStatus")
			.isEqualTo("AWARDED");

		assertThat(mvc.get().uri("/api/tenders/{id}/awards", tender.getId()).with(TestUsers.user())).bodyJson()
			.isLenientlyEqualTo("""
					[{"lotNumber": "1", "lot": "Lot 1 – Courants forts", "amount": 150000, "won": true},
					 {"lotNumber": "2", "winner": "Solaire Concurrent", "won": false}]""");
		// The status changed once, from the notice.
		assertThat(mvc.get().uri("/api/tenders/{id}/history", tender.getId()).with(TestUsers.user())).bodyJson()
			.isLenientlyEqualTo("[{\"newStatus\": \"AWARDED\", \"origin\": \"NOTICE\"}, {\"newStatus\": \"SUBMITTED\"}]");
		assertThat(award(tender, WON.replace("\"source\": \"MANUAL\"", "\"source\": \"MANUAL\", \"lotNumber\": \"9\"")))
			.hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void rejectsAnOfferLostAndKeepsWhoWon() {
		Tender tender = save("26-1", List.of());
		followUp(tender, "{\"status\": \"SUBMITTED\"}");

		assertThat(award(tender, """
				{"won": false, "winner": "Électricité Concurrente", "amount": 120000, "awardedAt": "2026-09-30",
				 "source": "MANUAL", "reason": "Prix trop élevé"}""")).hasStatusOk()
			.bodyJson()
			.extractingPath("$.followUpStatus")
			.isEqualTo("REJECTED");
		assertThat(mvc.get().uri("/api/awards?winner=electricite concurrente").with(TestUsers.user())).bodyJson()
			.extractingPath("$.totalElements")
			.isEqualTo(1);
		assertThat(followUp(tender, "{\"status\": \"SIGNED\"}")).hasStatus(HttpStatus.CONFLICT);
	}

	@Test
	void keepsTheTendersOfBeforeAsTheyWere() {
		// A tender saved before the result existed: its status and comment are unchanged, its history is empty.
		Tender tender = save("26-1", List.of());
		tender.followUp(FollowUpStatus.SUBMITTED, "Envoyé par la plateforme", "Marie", NOW);
		tenders.save(tender);

		assertThat(mvc.get().uri("/api/tenders/{id}", tender.getId()).with(TestUsers.user())).bodyJson()
			.isLenientlyEqualTo("{\"followUpStatus\": \"SUBMITTED\", \"followUpComment\": \"Envoyé par la plateforme\"}");
		assertThat(mvc.get().uri("/api/tenders/{id}/history", tender.getId()).with(TestUsers.user())).bodyJson()
			.isLenientlyEqualTo("[]");
		assertThat(mvc.get().uri("/api/tenders/{id}/awards", tender.getId()).with(TestUsers.user())).bodyJson()
			.isLenientlyEqualTo("[]");
	}

	private MvcTestResult followUp(Tender tender, String body) {
		return put("/api/tenders/{id}/follow-up", tender, body);
	}

	private MvcTestResult award(Tender tender, String body) {
		return put("/api/tenders/{id}/award", tender, body);
	}

	private MvcTestResult put(String uri, Tender tender, String body) {
		return mvc.put()
			.uri(uri, tender.getId())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.with(TestUsers.user())
			.with(csrf())
			.exchange();
	}

	private Tender save(String externalId, List<LotDetails> lots) {
		Tender tender = new Tender("BOAMP", externalId,
				new TenderDetails("Travaux d'électricité", null, "Ville de Nîmes", null, "Nîmes", List.of("30"),
						List.of(MarketType.WORKS), null, List.of(), new BigDecimal("150000"), null,
						NOW.plus(Duration.ofDays(10)), null, null, lots),
				NOW);
		tender.applyFilter(true, "Retenu", NOW);
		return tenders.save(tender);
	}

}
