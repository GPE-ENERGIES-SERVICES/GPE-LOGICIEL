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
class RawNoticeRepositoryTests {

	@Autowired
	private RawNoticeRepository repository;

	@Test
	void insertsANewNoticeWithItsJsonPayload() {
		int inserted = repository.insertIfAbsent("BOAMP", "26-1", "{\"objet\": \"Travaux\"}", LocalDate.of(2026, 10, 1));

		assertThat(inserted).isEqualTo(1);
		RawNotice found = repository.findAll().getFirst();
		assertThat(found.getExternalId()).isEqualTo("26-1");
		assertThat(found.getPayload()).contains("\"objet\"");
		assertThat(found.getPublishedAt()).isEqualTo(LocalDate.of(2026, 10, 1));
		assertThat(found.getCollectedAt()).isNotNull();
	}

	@Test
	void ignoresANoticeAlreadyCollected() {
		repository.insertIfAbsent("BOAMP", "26-2", "{}", null);

		assertThat(repository.insertIfAbsent("BOAMP", "26-2", "{}", null)).isZero();
		assertThat(repository.count()).isEqualTo(1);
	}

}
