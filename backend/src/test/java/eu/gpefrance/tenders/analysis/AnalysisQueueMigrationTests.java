package eu.gpefrance.tenders.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import eu.gpefrance.tenders.TestcontainersConfiguration;

/**
 * The queue of the analyses (V24) applied to a database at V23 holding
 * analyses, in a schema of its own: every analysis is kept, two unfinished
 * analyses of the same tender can no longer exist.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AnalysisQueueMigrationTests {

	private static final String SCHEMA = "analysis_migration_check";

	@Autowired
	private DataSource dataSource;

	@Autowired
	private JdbcTemplate jdbc;

	@AfterEach
	void dropSchema() {
		jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
	}

	@Test
	void keepsEveryAnalysisAndClosesTheOlderDuplicates() {
		migrate("23");
		jdbc.execute("""
				INSERT INTO analysis_migration_check.tender (id, source, external_id, title, status, created_at, updated_at)
				VALUES (1, 'BOAMP', '26-1', 'Travaux', 'ACTIVE', now(), now());
				INSERT INTO analysis_migration_check.dossier (id, tender_id, version, status, uploaded_by, uploaded_at)
				VALUES (1, 1, 1, 'DONE', 'Marie Durand', now());
				INSERT INTO analysis_migration_check.analysis
				    (id, tender_id, dossier_id, status, model, prompt_version, input_fingerprint, created_at)
				VALUES (1, 1, 1, 'DONE', 'm', 'grid-1/prompt-1', repeat('a', 64), now()),
				       (2, 1, 1, 'PENDING', 'm', 'grid-1/prompt-1', repeat('b', 64), now()),
				       (3, 1, 1, 'PROCESSING', 'm', 'grid-1/prompt-1', repeat('c', 64), now());
				""");

		migrate("24");

		assertThat(jdbc.queryForList("SELECT id || ':' || status || ':' || attempts || ':' || coalesce(error_kind, '') FROM "
				+ SCHEMA + ".analysis ORDER BY id", String.class))
			.containsExactly("1:DONE:1:", "2:FAILED:0:UNEXPECTED", "3:PROCESSING:0:");
		assertThat(jdbc.queryForObject("SELECT next_attempt_at IS NOT NULL FROM " + SCHEMA + ".analysis WHERE id = 3",
				Boolean.class)).isTrue();
		assertThatThrownBy(() -> jdbc.update("""
				INSERT INTO analysis_migration_check.analysis
				    (tender_id, dossier_id, status, model, prompt_version, input_fingerprint, created_at)
				VALUES (1, 1, 'RETRY_WAITING', 'm', 'v', repeat('d', 64), now())"""))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	private void migrate(String target) {
		Flyway.configure()
			.dataSource(dataSource)
			.schemas(SCHEMA)
			.defaultSchema(SCHEMA)
			.locations("classpath:db/migration")
			.target(target)
			.load()
			.migrate();
	}

}
