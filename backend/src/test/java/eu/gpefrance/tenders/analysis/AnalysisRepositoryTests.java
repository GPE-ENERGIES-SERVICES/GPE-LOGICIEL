package eu.gpefrance.tenders.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import eu.gpefrance.tenders.TestcontainersConfiguration;
import eu.gpefrance.tenders.document.Document;
import eu.gpefrance.tenders.document.Dossier;
import eu.gpefrance.tenders.document.PieceType;
import eu.gpefrance.tenders.tender.Tender;
import eu.gpefrance.tenders.tender.TenderDetails;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class AnalysisRepositoryTests {

	private static final Instant NOW = Instant.parse("2026-12-07T09:00:00Z");

	@Autowired
	private AnalysisRepository repository;

	@Autowired
	private TestEntityManager entityManager;

	private Tender tender;

	private Dossier dossier;

	private Document rc;

	@BeforeEach
	void createDossier() {
		tender = entityManager.persist(new Tender("BOAMP", "26-1", new TenderDetails("Travaux d'électricité",
				null, null, null, null, List.of("69"), List.of(), null, List.of(), null, null, null, null, null,
				List.of()), NOW));
		dossier = new Dossier(tender.getId(), 1, "user@example.com", NOW);
		rc = dossier.addDocument("RC.pdf", "1/RC.pdf", 1000, "a".repeat(64), PieceType.RC);
		entityManager.persist(dossier);
	}

	@Test
	void keepsEachFieldWithItsSource() {
		Analysis analysis = analysis("v1", NOW);
		analysis.addField("deadline", "15/11/2026 12:00", "Date limite : 15/11/2026 à 12h00", rc.getId(), 4,
				FieldState.UNVERIFIED);
		analysis.addField("site_visit", null, null, null, null, FieldState.NOT_INDICATED);
		analysis.finish(12000, 800, NOW.plusSeconds(30));
		repository.save(analysis);
		entityManager.flush();
		entityManager.clear();

		Analysis found = repository.findById(analysis.getId()).orElseThrow();
		assertThat(found.getStatus()).isEqualTo(AnalysisStatus.DONE);
		assertThat(found.getModel()).isEqualTo("model-a");
		assertThat(found.getPromptVersion()).isEqualTo("v1");
		assertThat(found.getInputTokens()).isEqualTo(12000);
		assertThat(found.getOutputTokens()).isEqualTo(800);
		assertThat(found.getTenderId()).isEqualTo(tender.getId());
		assertThat(found.getDocumentIds()).containsExactly(rc.getId());
		assertThat(found.getFields())
			.extracting(ExtractedField::getFieldKey, ExtractedField::getValue, ExtractedField::getDocumentId,
					ExtractedField::getPageNumber, ExtractedField::getState)
			.containsExactly(tuple("deadline", "15/11/2026 12:00", rc.getId(), 4, FieldState.UNVERIFIED),
					tuple("site_visit", null, null, null, FieldState.NOT_INDICATED));
	}

	@Test
	void aNewAnalysisKeepsTheEarlierOnes() {
		repository.save(analysis("v1", NOW));
		repository.save(analysis("v2", NOW.plusSeconds(60)));

		assertThat(repository.findByDossierIdOrderByIdDesc(dossier.getId())).extracting(Analysis::getPromptVersion)
			.containsExactly("v2", "v1");
	}

	@Test
	void truncatesALongErrorMessage() {
		Analysis analysis = analysis("v1", NOW);
		analysis.fail("x".repeat(5000), NOW);

		assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.FAILED);
		assertThat(analysis.getErrorMessage()).hasSize(2000);
	}

	private Analysis analysis(String promptVersion, Instant now) {
		return new Analysis(tender.getId(), dossier.getId(), "model-a", promptVersion, List.of(rc.getId()),
				"f".repeat(64), now);
	}

}
