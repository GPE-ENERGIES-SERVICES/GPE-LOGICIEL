package eu.gpefrance.tenders.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import eu.gpefrance.tenders.TestUsers;
import eu.gpefrance.tenders.TestcontainersConfiguration;
import eu.gpefrance.tenders.tender.Tender;
import eu.gpefrance.tenders.tender.TenderDetails;
import eu.gpefrance.tenders.tender.TenderRepository;

/**
 * Until a provider is configured, the interface says so and nothing starts.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AnalysisWithoutProviderTests {

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private TenderRepository tenders;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void saysThatNoProviderIsConfigured() {
		jdbc.execute("TRUNCATE analysis, document_page, document, dossier, tender_notice, lot, tender "
				+ "RESTART IDENTITY CASCADE");
		Tender tender = tenders.save(new Tender("BOAMP", "26-1", new TenderDetails("Travaux", null, null, null, null,
				List.of(), List.of(), null, List.of(), null, null, null, null, null, List.of()), Instant.now()));

		assertThat(mvc.get().uri("/api/tenders/{id}/analysis", tender.getId()).with(TestUsers.user())).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("{\"configured\": false, \"latest\": null, \"history\": []}");
		assertThat(mvc.post().uri("/api/dossiers/1/analyses").with(TestUsers.user()).with(csrf()))
			.hasStatus(HttpStatus.CONFLICT)
			.bodyJson()
			.extractingPath("$.detail")
			.asString()
			.contains("Aucun fournisseur d'IA");
	}

}
