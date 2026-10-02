package eu.gpefrance.tenders.collect;

import static org.assertj.core.api.Assertions.assertThat;

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
		CollectRun success = new CollectRun("BOAMP", CollectTrigger.SCHEDULED, LocalDate.of(2026, 10, 1));
		success.succeed(10, 8);
		repository.saveAndFlush(success);

		CollectRun failure = new CollectRun("BOAMP", CollectTrigger.MANUAL, LocalDate.of(2026, 10, 2));
		failure.fail(0, 0, "timeout");
		repository.saveAndFlush(failure);

		assertThat(repository.findFirstBySourceAndStatusOrderByStartedAtDesc("BOAMP", CollectStatus.SUCCESS))
			.get()
			.extracting(CollectRun::getSinceDate)
			.isEqualTo(LocalDate.of(2026, 10, 1));
		assertThat(repository.findFirstBySourceOrderByStartedAtDesc("BOAMP"))
			.get()
			.extracting(CollectRun::getStatus)
			.isEqualTo(CollectStatus.FAILED);
	}

	@Test
	void truncatesVeryLongErrorMessages() {
		CollectRun run = new CollectRun("BOAMP", CollectTrigger.SCHEDULED, LocalDate.of(2026, 10, 1));
		run.fail(0, 0, "x".repeat(5000));

		assertThat(repository.saveAndFlush(run).getErrorMessage()).hasSize(2000);
	}

}
