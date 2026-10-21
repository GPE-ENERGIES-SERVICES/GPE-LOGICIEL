package eu.gpefrance.tenders.collect;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import eu.gpefrance.tenders.TestcontainersConfiguration;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class CollectRunRepositoryTests {

	@Autowired
	private CollectRunRepository repository;

	@Test
	void findsLastSuccessfulRunIgnoringFailures() {
		CollectRun success = new CollectRun("BOAMP", CollectTrigger.SCHEDULED, LocalDate.of(2026, 10, 1),
				Instant.parse("2026-10-01T05:00:00Z"));
		success.succeed(10, 8, Instant.parse("2026-10-01T05:01:00Z"));
		repository.saveAndFlush(success);

		CollectRun failure = new CollectRun("BOAMP", CollectTrigger.MANUAL, LocalDate.of(2026, 10, 2),
				Instant.parse("2026-10-02T05:00:00Z"));
		failure.fail(0, 0, "timeout", Instant.parse("2026-10-02T05:01:00Z"));
		repository.saveAndFlush(failure);

		assertThat(repository.findFirstBySourceAndStatusOrderByStartedAtDescIdDesc("BOAMP", CollectStatus.SUCCESS))
			.get()
			.extracting(CollectRun::getSinceDate)
			.isEqualTo(LocalDate.of(2026, 10, 1));
		assertThat(repository.findFirstBySourceOrderByStartedAtDescIdDesc("BOAMP"))
			.get()
			.extracting(CollectRun::getStatus)
			.isEqualTo(CollectStatus.FAILED);
	}

	@Test
	void truncatesVeryLongErrorMessages() {
		CollectRun run = new CollectRun("BOAMP", CollectTrigger.SCHEDULED, LocalDate.of(2026, 10, 1), Instant.now());
		run.fail(0, 0, "x".repeat(5000), Instant.now());

		assertThat(repository.saveAndFlush(run).getErrorMessage()).hasSize(2000);
	}

}
