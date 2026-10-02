package eu.gpefrance.tenders.tender;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.convention.TestBean;

import eu.gpefrance.tenders.ClockConfiguration;
import eu.gpefrance.tenders.TestcontainersConfiguration;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TenderLifecycleServiceTests {

	private static final Instant NOW = Instant.parse("2026-10-05T10:00:00Z");

	@Autowired
	private TenderLifecycleService service;

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
	void closesOnlyActiveTendersWhoseDeadlineHasPassed() {
		Tender expired = tenders.save(tender("26-1", NOW.minusSeconds(60)));
		Tender open = tenders.save(tender("26-2", NOW.plusSeconds(60)));
		Tender withoutDeadline = tenders.save(tender("26-3", null));
		Tender cancelled = tender("26-4", NOW.minusSeconds(60));
		cancelled.cancel(NOW);
		cancelled = tenders.save(cancelled);

		assertThat(service.closeExpired()).isEqualTo(1);

		assertThat(status(expired)).isEqualTo(TenderStatus.CLOSED);
		assertThat(status(open)).isEqualTo(TenderStatus.ACTIVE);
		assertThat(status(withoutDeadline)).isEqualTo(TenderStatus.ACTIVE);
		assertThat(status(cancelled)).isEqualTo(TenderStatus.CANCELLED);
	}

	private TenderStatus status(Tender tender) {
		return tenders.findById(tender.getId()).orElseThrow().getStatus();
	}

	private static Tender tender(String externalId, Instant deadline) {
		return new Tender("BOAMP", externalId, new TenderDetails("Travaux", null, null, null, null, List.of(), List.of(),
				null, List.of(), null, null, deadline, null, null, List.of()), NOW);
	}

}
