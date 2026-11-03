package eu.gpefrance.tenders.tender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import eu.gpefrance.tenders.TestUsers;
import eu.gpefrance.tenders.TestcontainersConfiguration;
import eu.gpefrance.tenders.document.DossierRepository;
import eu.gpefrance.tenders.document.DossierStatus;
import eu.gpefrance.tenders.document.TestFiles;
import eu.gpefrance.tenders.eligibility.TenderEligibilityRepository;
import eu.gpefrance.tenders.filter.FilterService;

/**
 * A tender added by a user goes the way of any other, without taking part in
 * what is meant for the notices collected.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ManualTenderTests {

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private TenderRepository tenders;

	@Autowired
	private DossierRepository dossiers;

	@Autowired
	private FilterService filter;

	@Autowired
	private TenderEligibilityRepository eligibilities;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void cleanDatabase() {
		jdbc.execute("TRUNCATE extracted_field, analysis_document, analysis, document_page, document, dossier, "
				+ "tender_notice, lot, tender RESTART IDENTITY CASCADE");
	}

	@Test
	void isCreatedToStudyAndListedAmongTheTendersToProcess() {
		Tender tender = create("{\"title\": \" Toiture de la mairie \", \"buyer\": \"Ville de Bron\", "
				+ "\"deadline\": \"2030-01-15T11:00:00Z\"}");

		assertThat(tender.getSource()).isEqualTo(Tender.MANUAL_SOURCE);
		assertThat(tender.getTitle()).isEqualTo("Toiture de la mairie");
		assertThat(tender.getBuyer()).isEqualTo("Ville de Bron");
		assertThat(tender.getDeadline()).isEqualTo(Instant.parse("2030-01-15T11:00:00Z"));
		assertThat(tender.getFollowUpStatus()).isEqualTo(FollowUpStatus.TO_STUDY);
		assertThat(mvc.get().uri("/api/tenders?tab=TO_PROCESS").with(TestUsers.user())).hasStatusOk()
			.bodyJson()
			.extractingPath("$.content[0].id")
			.isEqualTo(tender.getId().intValue());
		assertThat(mvc.get().uri("/api/tenders/{id}", tender.getId()).with(TestUsers.user())).hasStatusOk();
	}

	@Test
	void takesNoPartInTheFilterNorInTheSavedSearches() {
		Tender tender = create("{\"title\": \"Toiture\"}");

		filter.filterPending();
		filter.filterAll();

		assertThat(tenders.findById(tender.getId()).orElseThrow().getRetained()).isNull();
		assertThat(eligibilities.countOutOfDate(0)).isZero();
		assertThat(eligibilities.findOutOfDate(0, 0, 10)).isEmpty();
	}

	@Test
	void receivesItsConsultationFileWithoutStartingAnyAnalysis() throws InterruptedException {
		Tender tender = create("{\"title\": \"Toiture\"}");

		assertThat(mvc.post()
			.uri("/api/tenders/{id}/dossiers", tender.getId())
			.multipart()
			.file(new MockMultipartFile("files", "RC.pdf", "application/pdf", TestFiles.pdf("Règlement de la consultation.")))
			.with(TestUsers.user())
			.with(csrf())).hasStatus(HttpStatus.ACCEPTED);

		for (int attempt = 0; attempt < 100
				&& dossiers.findByTenderIdOrderByVersionDesc(tender.getId()).getFirst().getStatus() != DossierStatus.DONE; attempt++) {
			Thread.sleep(100);
		}
		assertThat(dossiers.findByTenderIdOrderByVersionDesc(tender.getId()).getFirst().getStatus())
			.isEqualTo(DossierStatus.DONE);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM analysis", Long.class)).isZero();
	}

	@Test
	void refusesAnEmptyName() {
		assertThat(post("{\"title\": \"  \"}")).isEqualTo(HttpStatus.BAD_REQUEST.value());
		assertThat(tenders.findAll()).isEmpty();
	}

	private Tender create(String json) {
		assertThat(post(json)).isEqualTo(HttpStatus.CREATED.value());
		List<Tender> all = tenders.findAll();
		return all.getLast();
	}

	private int post(String json) {
		return mvc.post()
			.uri("/api/tenders")
			.contentType(MediaType.APPLICATION_JSON)
			.content(json)
			.with(TestUsers.user())
			.with(csrf())
			.exchange()
			.getResponse()
			.getStatus();
	}

}
