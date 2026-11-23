package eu.gpefrance.tenders.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.support.TransactionTemplate;

import eu.gpefrance.tenders.ClockConfiguration;
import eu.gpefrance.tenders.TestUsers;
import eu.gpefrance.tenders.TestcontainersConfiguration;
import eu.gpefrance.tenders.analysis.ExtractionClient.ModelAnswer;

/**
 * Every call to the AI is one line of its ledger, priced once, with its
 * tender: the totals by service, by tender and of the month all add up these
 * lines and nothing else.
 */
@SpringBootTest(properties = { "analysis.enabled=true", "analysis.provider=test",
		"analysis.external-transmission-allowed=true", "analysis.input-price-per-million=3",
		"analysis.output-price-per-million=15", "analysis.pricing.models.cheap-model.input=1",
		"analysis.pricing.models.cheap-model.output=2" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AiUsageTests {

	private static final Instant NOW = Instant.parse("2026-11-20T08:00:00Z");

	@Autowired
	private AiAssistant ai;

	@Autowired
	private AiUsageService usage;

	@Autowired
	private AiLedger ledger;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private TransactionTemplate transaction;

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private ExtractionClient client;

	@TestBean
	private Clock clock;

	static Clock clock() {
		return Clock.fixed(NOW, ClockConfiguration.ZONE);
	}

	private long finished;

	private long ongoing;

	@BeforeEach
	void prepare() {
		jdbc.execute("TRUNCATE ai_call, analysis, tender RESTART IDENTITY CASCADE");
		finished = jdbc.queryForObject("""
				INSERT INTO tender (source, external_id, title, status, created_at, updated_at)
				VALUES ('BOAMP', '26-1', 'Clos', 'CLOSED', now(), now()) RETURNING id""", Long.class);
		ongoing = jdbc.queryForObject("""
				INSERT INTO tender (source, external_id, title, status, deadline, created_at, updated_at)
				VALUES ('BOAMP', '26-2', 'En cours', 'ACTIVE', '2026-12-31T12:00:00Z', now(), now()) RETURNING id""",
				Long.class);
		given(client.provider()).willReturn("test");
		given(client.model()).willReturn("test-model");
	}

	@Test
	void recordsEachCallWithItsTenderAndItsCost() {
		given(client.complete(any())).willReturn(new ModelAnswer("{}", 1_000_000, 100_000));

		ai.ask("ESTIMATE_DRAFT", "v1", "i", "c", "{}", 100, "Alice", finished);

		Map<String, Object> call = jdbc.queryForMap("SELECT * FROM ai_call");
		assertThat(call).containsEntry("kind", "ESTIMATE_DRAFT").containsEntry("tender_id", finished)
			.containsEntry("status", "DONE").containsEntry("created_by", "Alice");
		// 1 M input tokens at 3 €, 0.1 M output tokens at 15 €.
		assertThat((BigDecimal) call.get("cost")).isEqualByComparingTo("4.5");
		assertThat(call.get("cost_confirmed")).isEqualTo(false);
	}

	@Test
	void pricesAModelWithItsOwnPrices() {
		assertThat(ledger.cost("cheap-model", 1_000_000, 1_000_000)).isEqualByComparingTo("3");
		assertThat(ledger.cost("other-model", 1_000_000, 1_000_000)).isEqualByComparingTo("18");
	}

	@Test
	void keepsAFailedCallWhenTheWorkThatAskedForItIsRolledBack() {
		given(client.complete(any())).willThrow(new ProviderException(FailureKind.TIMEOUT, "lent",
				new ModelAnswer("", 200_000, 0)));

		assertThatThrownBy(() -> transaction.executeWithoutResult(
				tx -> ai.ask("MEMOIR_SECTION", "v1", "i", "c", "{}", 100, "Alice", ongoing)));

		Map<String, Object> call = jdbc.queryForMap("SELECT * FROM ai_call");
		assertThat(call).containsEntry("status", "FAILED").containsEntry("error_kind", "TIMEOUT")
			.containsEntry("input_tokens", 200_000);
		assertThat((BigDecimal) call.get("cost")).isEqualByComparingTo("0.6");
		assertThat(usage.overview(null).health().state()).isEqualTo("RED");
	}

	@Test
	void addsUpTheCallsByServiceAndByTenderOnce() {
		given(client.complete(any())).willReturn(new ModelAnswer("{}", 1_000_000, 0));
		ai.ask("ESTIMATE_DRAFT", "v1", "i", "c", "{}", 100, "Alice", finished);
		ai.ask("MEMOIR_SECTION", "v1", "i", "c", "{}", 100, "Alice", finished);
		ai.ask("MEMOIR_SECTION", "v1", "i", "c", "{}", 100, "Alice", ongoing);
		ai.ask("MEMOIR_SECTION", "v1", "i", "c", "{}", 100, "Alice", null);

		AiUsageService.Overview overview = usage.overview(null);

		assertThat(overview.calls()).isEqualTo(4);
		assertThat(overview.cost()).isEqualByComparingTo("12");
		assertThat(overview.month().spent()).isEqualByComparingTo("12");
		AiUsageService.ServiceUse memoir = overview.services().getFirst();
		assertThat(memoir.kind()).isEqualTo("MEMOIR_SECTION");
		assertThat(memoir.calls()).isEqualTo(3);
		assertThat(memoir.cost()).isEqualByComparingTo("9");
		assertThat(memoir.averageCost()).isEqualByComparingTo("3");
		assertThat(memoir.share()).isEqualByComparingTo("0.75");
		// The finished tender: estimate and memoir, 6 €; the ongoing one apart; the call for no tender in none.
		assertThat(overview.perTender().finished().tenders()).isEqualTo(1);
		assertThat(overview.perTender().finished().average()).isEqualByComparingTo("6");
		assertThat(overview.perTender().ongoing().average()).isEqualByComparingTo("3");
		assertThat(overview.perTender().unattributedCalls()).isEqualTo(1);
		assertThat(overview.perTender().unattributedCost()).isEqualByComparingTo("3");

		AiUsageService.TenderCost first = usage.tenders(0, 20).content().getFirst();
		assertThat(first.tenderId()).isEqualTo(finished);
		assertThat(first.finished()).isTrue();
		assertThat(first.cost()).isEqualByComparingTo("6");
		assertThat(first.services()).hasSize(2);
	}

	@Test
	void listsTheCallsToAdministratorsOnly() {
		given(client.complete(any())).willReturn(new ModelAnswer("{}", 1_000, 0));
		for (int i = 0; i < 3; i++) {
			ai.ask("ESTIMATE_DRAFT", "v1", "i", "c", "{}", 100, "Alice", ongoing);
		}

		assertThat(mvc.get().uri("/api/ai-usage/calls?size=2").with(TestUsers.user()).exchange())
			.hasStatus(HttpStatus.FORBIDDEN);
		assertThat(mvc.get().uri("/api/ai-usage/calls?size=2&kind=ESTIMATE_DRAFT").with(TestUsers.admin()).exchange())
			.hasStatusOk()
			.bodyJson()
			.extractingPath("$.totalElements")
			.isEqualTo(3);
	}

}
