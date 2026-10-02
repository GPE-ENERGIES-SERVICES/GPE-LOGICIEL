package eu.gpefrance.tenders.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import eu.gpefrance.tenders.TestcontainersConfiguration;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class RawNoticeRepositoryTests {

	@Autowired
	private RawNoticeRepository repository;

	@Test
	void savesPayloadAsJson() {
		RawNotice saved = repository.saveAndFlush(
				new RawNotice("BOAMP", "26-1", "{\"objet\": \"Travaux\"}", LocalDate.of(2026, 10, 1)));

		RawNotice found = repository.findById(saved.getId()).orElseThrow();
		assertThat(found.getPayload()).contains("\"objet\"");
		assertThat(found.getCollectedAt()).isNotNull();
		assertThat(repository.existsBySourceAndExternalId("BOAMP", "26-1")).isTrue();
	}

	@Test
	void rejectsSameNoticeTwice() {
		repository.saveAndFlush(new RawNotice("BOAMP", "26-2", "{}", null));

		assertThatThrownBy(() -> repository.saveAndFlush(new RawNotice("BOAMP", "26-2", "{}", null)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

}
