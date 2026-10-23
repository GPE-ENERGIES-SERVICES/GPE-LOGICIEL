package eu.gpefrance.tenders.document;

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
 * The migrations of the saved searches, of the eligibility and of the queue
 * of retrieval (V21 to V23), applied to a database at V22 holding links,
 * dossiers and documents, in a schema of its own: nothing is lost, the
 * former statuses become their successors.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DceMigrationTests {

	private static final String SCHEMA = "migration_check";

	@Autowired
	private DataSource dataSource;

	@Autowired
	private JdbcTemplate jdbc;

	@AfterEach
	void dropSchema() {
		jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
	}

	@Test
	void keepsEveryLinkDossierAndDocumentAndMapsTheFormerStatuses() {
		migrate("22");
		jdbc.execute("""
					INSERT INTO migration_check.tender (id, source, external_id, title, status, created_at, updated_at)
					VALUES (1, 'BOAMP', '26-1', 'Travaux', 'ACTIVE', now(), now());
					INSERT INTO migration_check.dce_link (id, tender_id, source, url, platform, status, status_detail, first_seen_at, last_seen_at)
					VALUES (1, 1, 'BOAMP', 'https://a.test/1', 'a.test', 'AVAILABLE', null, now(), now()),
					       (2, 1, 'BOAMP', 'https://a.test/2', 'a.test', 'TO_FETCH', null, now(), now()),
					       (3, 1, 'TED', 'https://a.test/3', 'a.test', 'FAILED', 'HTTP 500', now(), now());
					INSERT INTO migration_check.dossier (id, tender_id, version, status, uploaded_by, uploaded_at)
					VALUES (1, 1, 1, 'DONE', 'Marie Durand', now());
					INSERT INTO migration_check.document (id, dossier_id, file_name, storage_path, size_bytes, sha256, piece_type, state)
					VALUES (1, 1, 'RC.pdf', 'tenders/1/v1/files/RC.pdf', 50, repeat('a', 64), 'RC', 'READ');
				""");

		migrate("23");

		assertThat(jdbc.queryForList("SELECT id || ':' || url || ':' || status || ':' || coalesce(status_detail, '') FROM "
				+ SCHEMA + ".dce_link ORDER BY id", String.class))
			.containsExactly("1:https://a.test/1:AVAILABLE:", "2:https://a.test/2:ELIGIBLE_PENDING:",
					"3:https://a.test/3:PERMANENT_ERROR:HTTP 500");
		assertThat(jdbc.queryForMap("SELECT attempts, file_count FROM " + SCHEMA + ".dce_link WHERE id = 1"))
			.containsEntry("attempts", 0)
			.containsEntry("file_count", null);
		assertThat(jdbc.queryForMap("SELECT origin, uploaded_by, stored_bytes FROM " + SCHEMA + ".dossier"))
			.containsEntry("origin", "UPLOAD")
			.containsEntry("uploaded_by", "Marie Durand")
			.containsEntry("stored_bytes", null);
		assertThat(jdbc.queryForObject("SELECT storage_path FROM " + SCHEMA + ".document", String.class))
			.isEqualTo("tenders/1/v1/files/RC.pdf");
		assertThat(jdbc.queryForObject("SELECT revision FROM " + SCHEMA + ".alert_revision", Long.class)).isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM " + SCHEMA + ".tender_eligibility", Long.class)).isZero();
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
