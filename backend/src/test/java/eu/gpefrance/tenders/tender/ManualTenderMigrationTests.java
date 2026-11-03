package eu.gpefrance.tenders.tender;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import eu.gpefrance.tenders.TestcontainersConfiguration;

/**
 * A consultation file analysed without tender (V39) becomes a tender added by
 * a user (V40), applied in a schema of its own: nothing it held is lost.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ManualTenderMigrationTests {

	private static final String SCHEMA = "manual_tender_check";

	@Autowired
	private DataSource dataSource;

	@Autowired
	private JdbcTemplate jdbc;

	@AfterEach
	void dropSchema() {
		jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
	}

	@Test
	void keepsTheDossiersAndAnalysesOfAReview() {
		migrate("39");
		jdbc.execute("""
				INSERT INTO manual_tender_check.tender (id, source, external_id, title, status, created_at, updated_at)
				VALUES (1000, 'BOAMP', '26-1', 'Collecté', 'ACTIVE', now(), now());
				INSERT INTO manual_tender_check.dce_review (id, name, created_by, created_at)
				VALUES (7, 'Toiture', 'Marie Durand', '2026-11-01T10:00:00Z');
				INSERT INTO manual_tender_check.dossier (id, tender_id, review_id, version, status, uploaded_at)
				VALUES (1, 1000, null, 1, 'DONE', now()), (2, null, 7, 1, 'DONE', now());
				INSERT INTO manual_tender_check.analysis (id, tender_id, review_id, dossier_id, status, model, prompt_version, input_fingerprint, created_at)
				VALUES (1, null, 7, 2, 'DONE', 'm', 'v', 'f', now());
				""");

		migrate("40");

		Long tender = jdbc.queryForObject("SELECT id FROM " + SCHEMA + ".tender WHERE source = 'MANUAL'", Long.class);
		assertThat(jdbc.queryForObject("SELECT title || ' ' || follow_up_status || ' ' || follow_up_by FROM " + SCHEMA
				+ ".tender WHERE id = ?", String.class, tender)).isEqualTo("Toiture TO_STUDY Marie Durand");
		assertThat(jdbc.queryForObject("SELECT tender_id FROM " + SCHEMA + ".dossier WHERE id = 2", Long.class))
			.isEqualTo(tender);
		assertThat(jdbc.queryForObject("SELECT tender_id FROM " + SCHEMA + ".dossier WHERE id = 1", Long.class))
			.isEqualTo(1000L);
		assertThat(jdbc.queryForObject("SELECT tender_id FROM " + SCHEMA + ".analysis WHERE id = 1", Long.class))
			.isEqualTo(tender);
	}

	private void migrate(String version) {
		Flyway.configure()
			.dataSource(dataSource)
			.schemas(SCHEMA)
			.defaultSchema(SCHEMA)
			.locations("classpath:db/migration")
			.target(version)
			.load()
			.migrate();
	}

}
