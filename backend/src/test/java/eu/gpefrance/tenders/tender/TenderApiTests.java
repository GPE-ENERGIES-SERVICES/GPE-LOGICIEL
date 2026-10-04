package eu.gpefrance.tenders.tender;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.assertj.core.api.ObjectArrayAssert;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TenderApiTests {

	private static final Instant NOW = Instant.parse("2026-10-05T08:00:00Z");

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
		// The follows refer to the accounts of the test users.
		jdbc.execute("""
				INSERT INTO app_user (id, email, display_name, password_hash, role, enabled, created_at)
				VALUES (1, 'marie@gpe.test', 'Marie Durand', 'x', 'USER', true, now()),
				       (2, 'admin@gpe.test', 'Admin GPE', 'x', 'ADMIN', true, now())
				ON CONFLICT DO NOTHING
				""");
	}

	@Test
	void listsTheTabSortedByDeadline() {
		save("26-1", "Travaux d'électricité", "69", 20, true, null);
		save("26-2", "Réseau de chaleur", "69", 10, true, null);
		save("26-3", "Nettoyage", "13", 15, false, null);

		assertThat(mvc.get().uri("/api/tenders?tab=NEW").with(TestUsers.user())).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{"totalElements": 2, "content": [{"title": "Réseau de chaleur"},
					{"title": "Travaux d'électricité", "retained": true}]}
					""");
		assertThat(mvc.get().uri("/api/tenders?tab=FILTERED_OUT").with(TestUsers.user())).hasStatusOk()
			.bodyJson()
			.extractingPath("$.content[0].title")
			.isEqualTo("Nettoyage");
	}

	@Test
	void appliesTheQuickFilters() {
		save("26-1", "Travaux d'électricité", "69", 20, true, new BigDecimal("50000"));
		save("26-2", "Electricite eclairage public", "01", 3, true, null);
		save("26-3", "Electricité du stade", "69", 30, true, new BigDecimal("900000"));

		assertThat(mvc.get().uri("/api/tenders?q=ELECTRICITE").with(TestUsers.user())).bodyJson()
			.extractingPath("$.totalElements")
			.isEqualTo(3);
		titles("/api/tenders?department=69&minDaysLeft=7&maxAmount=100000").containsExactly("Travaux d'électricité");
	}

	@Test
	void splitsTheOpenTendersIntoTabsAndKeepsThePastOnesApart() {
		save("26-1", "Nouveau", "13", 20, true, null);
		followUp(save("26-2", "À analyser", "13", 20, true, null), "{\"status\": \"TO_STUDY\"}");
		followUp(save("26-3", "Écarté", "13", 20, true, null), "{\"status\": \"DISMISSED\"}");
		save("26-4", "Hors critères", "69", 20, false, null);
		Tender past = save("26-5", "Clos", "13", 20, true, null);
		past.close(NOW);
		tenders.save(past);

		assertThat(mvc.get().uri("/api/tenders/counts").with(TestUsers.user())).bodyJson()
			.isLenientlyEqualTo("""
					{"NEW": 1, "TO_PROCESS": 1, "FOLLOWED": 0, "ALL": 3, "DISMISSED": 1, "ARCHIVED": 1,
					 "FILTERED_OUT": 1}
					""");
		titles("/api/tenders?tab=ARCHIVED").containsExactly("Clos");
	}

	@Test
	void recordsTheDecisionAndMovesTheTenderToItsTab() {
		Tender tender = save("26-1", "Travaux d'électricité", "69", 20, true, null);

		assertThat(mvc.put()
			.uri("/api/tenders/{id}/follow-up", tender.getId())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\": \"TO_STUDY\", \"comment\": \"  Visite à prévoir \"}")
			.with(TestUsers.user())
			.with(csrf())).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("{\"followUpStatus\": \"TO_STUDY\", \"followUpComment\": \"Visite à prévoir\"}");

		assertThat(mvc.get().uri("/api/tenders/counts").with(TestUsers.user())).bodyJson()
			.isLenientlyEqualTo("{\"NEW\": 0, \"TO_PROCESS\": 1, \"ALL\": 1}");
	}

	@Test
	void keepsTheCommentWhenOnlyTheStatusChanges() {
		Tender tender = save("26-1", "Travaux d'électricité", "69", 20, true, null);
		followUp(tender, "{\"status\": \"TO_STUDY\", \"comment\": \"Visite à prévoir\"}");

		assertThat(followUp(tender, "{\"status\": \"RESPONDING\"}")).bodyJson()
			.isLenientlyEqualTo("{\"followUpStatus\": \"RESPONDING\", \"followUpComment\": \"Visite à prévoir\"}");
		assertThat(followUp(tender, "{\"status\": \"RESPONDING\", \"comment\": \" \"}")).bodyJson()
			.extractingPath("$.followUpComment")
			.isNull();
	}

	@Test
	void acceptsTheSubmittedAnswerAndCountsTheStatuses() {
		Tender tender = save("26-1", "Travaux d'électricité", "69", 20, true, null);
		save("26-2", "Éclairage public", "69", 20, true, null);

		assertThat(followUp(tender, "{\"status\": \"SUBMITTED\"}")).hasStatusOk()
			.bodyJson()
			.extractingPath("$.followUpStatus")
			.isEqualTo("SUBMITTED");
		// The status filter is ignored: the counts are those of every status.
		assertThat(mvc.get().uri("/api/tenders/status-counts?tab=ALL&status=NEW").with(TestUsers.user())).bodyJson()
			.isLenientlyEqualTo("""
					{"NEW": 1, "TO_STUDY": 0, "RESPONDING": 0, "SUBMITTED": 1, "DISMISSED": 0}
					""");
	}

	@Test
	void keepsTheFollowOfEachUserInTheDatabase() {
		Tender tender = save("26-1", "Travaux d'électricité", "69", 20, true, null);

		assertThat(follow(tender, true)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(follow(tender, true)).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(jdbc.queryForObject("SELECT count(*) FROM tender_follow", Long.class)).isEqualTo(1);
		assertThat(mvc.get().uri("/api/tenders/{id}", tender.getId()).with(TestUsers.user())).bodyJson()
			.extractingPath("$.followed")
			.isEqualTo(true);
		assertThat(mvc.get().uri("/api/tenders?tab=FOLLOWED").with(TestUsers.user())).bodyJson()
			.isLenientlyEqualTo("{\"totalElements\": 1, \"content\": [{\"followed\": true}]}");
		assertThat(mvc.get().uri("/api/tenders?tab=FOLLOWED").with(TestUsers.admin())).bodyJson()
			.extractingPath("$.totalElements")
			.isEqualTo(0);

		assertThat(follow(tender, false)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(mvc.get().uri("/api/tenders?tab=NEW").with(TestUsers.user())).bodyJson()
			.extractingPath("$.content[0].followed")
			.isEqualTo(false);
	}

	@Test
	void combinesTheFilters() {
		tenders.save(retained(new Tender("BOAMP", "26-1",
				new TenderDetails("Rénovation de l'école", null, null, null, "Aubagne", List.of("13"),
						List.of(MarketType.WORKS), ProcedureType.OPEN, List.of("45453000-7"), new BigDecimal("900000"),
						null, NOW.plus(Duration.ofDays(5)), null, null, List.of(new LotDetails("1", "Isolation", null))),
				NOW)));
		tenders.save(retained(new Tender("BOAMP", "26-2",
				new TenderDetails("Maintenance des bâtiments", null, null, null, "Toulon", List.of("83"),
						List.of(MarketType.SERVICES), ProcedureType.ADAPTED, List.of("50711000-2"), null, null,
						NOW.plus(Duration.ofDays(30)), null, null, List.of()),
				NOW)));
		save("26-3", "Travaux à Lyon", "69", 5, true, new BigDecimal("50000"));

		titles("/api/tenders?tab=ALL&region=Provence-Alpes-Côte d'Azur").containsExactlyInAnyOrder(
				"Rénovation de l'école", "Maintenance des bâtiments");
		titles("/api/tenders?tab=ALL&department=13,69&marketType=WORKS&hasLots=true")
			.containsExactly("Rénovation de l'école");
		titles("/api/tenders?tab=ALL&city=aubagne&procedure=OPEN&maxDaysLeft=7&minAmount=100000")
			.containsExactly("Rénovation de l'école");
		titles("/api/tenders?tab=ALL&q=45453&status=NEW").containsExactly("Rénovation de l'école");
		titles("/api/tenders?tab=ALL&q=toulon").containsExactly("Maintenance des bâtiments");
		assertThat(mvc.get().uri("/api/tenders?tab=ALL&hasLots=true").with(TestUsers.user())).bodyJson()
			.extractingPath("$.content[*].lotCount")
			.asArray()
			.containsExactly(1);
	}

	@Test
	void sortsByAmountOrPutsPassedDeadlinesAfterTheOpenOnes() {
		save("26-1", "Moyen", "13", 10, true, new BigDecimal("50000"));
		save("26-2", "Sans montant", "13", 2, true, null);
		save("26-3", "Gros", "13", 30, true, new BigDecimal("900000"));
		Tender passed = save("26-4", "Passé", "13", -3, true, null);
		Tender close = save("26-5", "Proche", "13", 1, true, null);
		follow(passed, true);
		follow(close, true);

		titles("/api/tenders?sort=-amount").containsExactly("Gros", "Moyen", "Proche", "Passé", "Sans montant");
		titles("/api/tenders?tab=FOLLOWED").containsExactly("Proche", "Passé");
	}

	@Test
	void readsTheOutcomeFromTheNotices() {
		Tender tender = save("26-1", "Travaux d'électricité", "69", 20, true, null);
		Long rawNoticeId = jdbc.queryForObject(
				"INSERT INTO raw_notice (source, external_id, payload) VALUES ('BOAMP', '26-9', '{}') RETURNING id",
				Long.class);
		tender.addNotice(rawNoticeId, "26-9", NoticeKind.AWARD, null, null, null);
		tenders.save(tender);
		Tender cancelled = save("26-2", "Annulé", "69", 20, true, null);
		cancelled.cancel(NOW);
		tenders.save(cancelled);

		assertThat(mvc.get().uri("/api/tenders/{id}", tender.getId()).with(TestUsers.user())).bodyJson()
			.extractingPath("$.result.kind")
			.isEqualTo("AWARDED");
		assertThat(mvc.get().uri("/api/tenders/{id}", cancelled.getId()).with(TestUsers.user())).bodyJson()
			.extractingPath("$.result.kind")
			.isEqualTo("CANCELLED");
	}

	@Test
	void returnsTheDetailWithLotsAndHistory() {
		Tender tender = tenders.save(new Tender("BOAMP", "26-1",
				details("Construction d'une école", "69", NOW.plus(Duration.ofDays(20)), null,
						List.of(new LotDetails("1", "Gros œuvre", null), new LotDetails("2", "Électricité", null))),
				NOW));

		assertThat(mvc.get().uri("/api/tenders/{id}", tender.getId()).with(TestUsers.user())).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{"externalId": "26-1", "title": "Construction d'une école",
					 "lots": [{"number": "1", "title": "Gros œuvre"}, {"number": "2", "title": "Électricité"}],
					 "followUpStatus": "NEW", "followed": false, "result": null}
					""");
	}

	@Test
	void answersNotFoundForAnUnknownTender() {
		assertThat(mvc.get().uri("/api/tenders/999").with(TestUsers.user())).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(mvc.put().uri("/api/tenders/999/follow").with(TestUsers.user()).with(csrf()))
			.hasStatus(HttpStatus.NOT_FOUND);
	}

	/** Titles of the tenders listed, in their order. */
	private ObjectArrayAssert<Object> titles(String uri) {
		return assertThat(mvc.get().uri(uri).with(TestUsers.user())).bodyJson()
			.extractingPath("$.content[*].title")
			.asArray();
	}

	private MvcTestResult followUp(Tender tender, String body) {
		return mvc.put()
			.uri("/api/tenders/{id}/follow-up", tender.getId())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.with(TestUsers.user())
			.with(csrf())
			.exchange();
	}

	private MvcTestResult follow(Tender tender, boolean followed) {
		return (followed ? mvc.put() : mvc.delete()).uri("/api/tenders/{id}/follow", tender.getId())
			.with(TestUsers.user())
			.with(csrf())
			.exchange();
	}

	private Tender save(String externalId, String title, String department, int daysLeft, boolean retained,
			BigDecimal amount) {
		Tender tender = new Tender("BOAMP", externalId,
				details(title, department, NOW.plus(Duration.ofDays(daysLeft)), amount, List.of()), NOW);
		tender.applyFilter(retained, retained ? "Retenu" : "Écarté", NOW);
		return tenders.save(tender);
	}

	private static Tender retained(Tender tender) {
		tender.applyFilter(true, "Retenu", NOW);
		return tender;
	}

	private static TenderDetails details(String title, String department, Instant deadline, BigDecimal amount,
			List<LotDetails> lots) {
		return new TenderDetails(title, null, null, null, null, List.of(department), List.of(MarketType.WORKS), null,
				List.of(), amount, null, deadline, null, null, lots);
	}

}
