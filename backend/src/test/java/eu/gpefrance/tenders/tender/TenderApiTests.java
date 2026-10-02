package eu.gpefrance.tenders.tender;

import static org.assertj.core.api.Assertions.assertThat;

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

import eu.gpefrance.tenders.ClockConfiguration;
import eu.gpefrance.tenders.TestcontainersConfiguration;

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
		jdbc.execute("TRUNCATE tender_notice, lot, tender RESTART IDENTITY CASCADE");
	}

	@Test
	void listsTheTabSortedByDeadline() {
		save("26-1", "Travaux d'électricité", "69", 20, true, null);
		save("26-2", "Réseau de chaleur", "69", 10, true, null);
		save("26-3", "Nettoyage", "13", 15, false, null);

		assertThat(mvc.get().uri("/api/tenders?tab=NEW")).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{"totalElements": 2, "content": [{"title": "Réseau de chaleur"},
					{"title": "Travaux d'électricité", "retained": true}]}
					""");
		assertThat(mvc.get().uri("/api/tenders?tab=FILTERED_OUT")).hasStatusOk()
			.bodyJson()
			.extractingPath("$.content[0].title")
			.isEqualTo("Nettoyage");
	}

	@Test
	void appliesTheQuickFilters() {
		save("26-1", "Travaux d'électricité", "69", 20, true, new BigDecimal("50000"));
		save("26-2", "Electricite eclairage public", "01", 3, true, null);
		save("26-3", "Electricité du stade", "69", 30, true, new BigDecimal("900000"));

		assertThat(mvc.get().uri("/api/tenders?q=ELECTRICITE")).bodyJson()
			.extractingPath("$.totalElements")
			.isEqualTo(3);
		assertThat(mvc.get().uri("/api/tenders?department=69&minDaysLeft=7&maxAmount=100000")).bodyJson()
			.extractingPath("$.content[*].title")
			.asArray()
			.containsExactly("Travaux d'électricité");
	}

	@Test
	void recordsTheDecisionAndMovesTheTenderToItsTab() {
		Tender tender = save("26-1", "Travaux d'électricité", "69", 20, true, null);

		assertThat(mvc.put()
			.uri("/api/tenders/{id}/follow-up", tender.getId())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\": \"TO_STUDY\", \"comment\": \"  Visite à prévoir \"}")).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("{\"followUpStatus\": \"TO_STUDY\", \"followUpComment\": \"Visite à prévoir\"}");

		assertThat(mvc.get().uri("/api/tenders/counts")).bodyJson()
			.isLenientlyEqualTo("{\"NEW\": 0, \"TO_STUDY\": 1, \"RESPONDING\": 0}");
	}

	@Test
	void returnsTheDetailWithLotsAndHistory() {
		Tender tender = tenders.save(new Tender("BOAMP", "26-1",
				details("Construction d'une école", "69", NOW.plus(Duration.ofDays(20)), null,
						List.of(new LotDetails("1", "Gros œuvre", null), new LotDetails("2", "Électricité", null))),
				NOW));

		assertThat(mvc.get().uri("/api/tenders/{id}", tender.getId())).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{"externalId": "26-1", "title": "Construction d'une école",
					 "lots": [{"number": "1", "title": "Gros œuvre"}, {"number": "2", "title": "Électricité"}],
					 "followUpStatus": "NEW"}
					""");
	}

	@Test
	void answersNotFoundForAnUnknownTender() {
		assertThat(mvc.get().uri("/api/tenders/999")).hasStatus(HttpStatus.NOT_FOUND);
	}

	private Tender save(String externalId, String title, String department, int daysLeft, boolean retained,
			BigDecimal amount) {
		Tender tender = new Tender("BOAMP", externalId,
				details(title, department, NOW.plus(Duration.ofDays(daysLeft)), amount, List.of()), NOW);
		tender.applyFilter(retained, retained ? "Retenu" : "Écarté", NOW);
		return tenders.save(tender);
	}

	private static TenderDetails details(String title, String department, Instant deadline, BigDecimal amount,
			List<LotDetails> lots) {
		return new TenderDetails(title, null, null, null, null, List.of(department), List.of(MarketType.WORKS), null,
				List.of(), amount, null, deadline, null, null, lots);
	}

}
