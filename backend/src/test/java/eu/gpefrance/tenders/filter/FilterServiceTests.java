package eu.gpefrance.tenders.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import eu.gpefrance.tenders.TestcontainersConfiguration;
import eu.gpefrance.tenders.tender.MarketType;
import eu.gpefrance.tenders.tender.Tender;
import eu.gpefrance.tenders.tender.TenderDetails;
import eu.gpefrance.tenders.tender.TenderRepository;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class FilterServiceTests {

	@Autowired
	private FilterService service;

	@Autowired
	private TenderRepository tenders;

	@Autowired
	private FilterCriteriaRepository criteria;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void reset() {
		jdbc.execute("TRUNCATE tender_notice, lot, tender RESTART IDENTITY CASCADE");
		service.replaceCriteria(request(List.of("69"), List.of("électricité")), null);
	}

	@Test
	void filtersNewTendersOnlyOnce() {
		tenders.save(tender("26-1", "Travaux d'électricité", "69"));
		tenders.save(tender("26-2", "Travaux d'électricité", "13"));

		assertThat(service.filterPending()).isEqualTo(2);
		assertThat(service.filterPending()).isZero();

		assertThat(tenders.findBySourceAndExternalId("BOAMP", "26-1").orElseThrow().getRetained()).isTrue();
		Tender excluded = tenders.findBySourceAndExternalId("BOAMP", "26-2").orElseThrow();
		assertThat(excluded.getRetained()).isFalse();
		assertThat(excluded.getFilterReason()).isEqualTo("Écarté : hors zone (13)");
	}

	@Test
	void filtersEveryTenderAgainWithNewCriteria() {
		tenders.save(tender("26-1", "Travaux d'électricité", "13"));
		service.filterPending();

		service.replaceCriteria(request(List.of("13"), List.of("électricité")), "admin@gpe.test");
		assertThat(service.filterAll()).isEqualTo(1);

		assertThat(tenders.findBySourceAndExternalId("BOAMP", "26-1").orElseThrow().getRetained()).isTrue();
		assertThat(criteria.getCurrent().getUpdatedBy()).isEqualTo("admin@gpe.test");
		assertThat(criteria.getCurrent().getDepartments()).containsExactly("13");
	}

	private static FilterCriteriaRequest request(List<String> departments, List<String> keywords) {
		return new FilterCriteriaRequest(departments, List.of(MarketType.WORKS), null, List.of(), keywords, List.of(),
				null, null);
	}

	private static Tender tender(String externalId, String title, String department) {
		return new Tender("BOAMP", externalId, new TenderDetails(title, null, null, null, null, List.of(department),
				List.of(MarketType.WORKS), null, List.of(), null, null, null, null, null, List.of()),
				Instant.parse("2026-10-01T08:00:00Z"));
	}

}
