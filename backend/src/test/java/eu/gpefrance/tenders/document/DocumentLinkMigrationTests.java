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
 * The address of the consultation documents of the BOAMP notices already
 * collected (V25), applied to a database at V24 in a schema of its own: each
 * tender gets the link its notices give, nothing else changes.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DocumentLinkMigrationTests {

	private static final String SCHEMA = "document_link_check";

	@Autowired
	private DataSource dataSource;

	@Autowired
	private JdbcTemplate jdbc;

	@AfterEach
	void dropSchema() {
		jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
	}

	@Test
	void addsTheLinkOfTheConsultationDocumentsOfTheNoticesAlreadyCollected() {
		migrate("24");
		jdbc.execute("""
					INSERT INTO document_link_check.tender (id, source, external_id, title, status, platform_url, created_at, updated_at)
					VALUES (1, 'BOAMP', '26-1', 'Sans lien', 'ACTIVE', null, now(), now()),
					       (2, 'BOAMP', '26-2', 'Avec lien', 'ACTIVE', 'https://kept.test/2', now(), now()),
					       (3, 'BOAMP', '26-3', 'Profil seul', 'ACTIVE', 'https://profile.test', now(), now());
					INSERT INTO document_link_check.raw_notice (id, source, external_id, payload, published_at)
					VALUES (1, 'BOAMP', '26-1', jsonb_build_object('donnees',
					            '{"MAPA": {"initial": {"communication": {"urlDocConsul": "www.docs.test/c?id=1&amp;org=a"}}}}'), '2026-10-01'),
					       (2, 'BOAMP', '26-2', jsonb_build_object('donnees',
					            '{"FNSimple": {"initial": {"communication": {"urlDocConsul": "https://WWW.Docs.test/c/2"}}}}'), '2026-10-02'),
					       (3, 'BOAMP', '26-3', jsonb_build_object('donnees',
					            '{"MAPA": {"organisme": {"urlProfilAcheteur": "https://profile.test"}}}'), '2026-10-03');
					INSERT INTO document_link_check.tender_notice (tender_id, raw_notice_id, external_id, kind, published_at, source)
					VALUES (1, 1, '26-1', 'INITIAL', '2026-10-01', 'BOAMP'),
					       (2, 2, '26-2', 'INITIAL', '2026-10-02', 'BOAMP'),
					       (3, 3, '26-3', 'INITIAL', '2026-10-03', 'BOAMP');
					INSERT INTO document_link_check.dce_link (tender_id, source, url, platform, status, first_seen_at, last_seen_at)
					VALUES (3, 'BOAMP', 'https://profile.test', 'profile.test', 'NOT_ELIGIBLE', now(), now());
					INSERT INTO document_link_check.tender_eligibility (tender_id, state, reason, alert_revision, evaluated_at)
					VALUES (1, 'NOT_ELIGIBLE', 'Aucune veille', 3, now()), (3, 'NOT_ELIGIBLE', 'Aucune veille', 3, now());
				""");

		migrate("25");

		assertThat(jdbc.queryForList("SELECT tender_id || ' ' || url || ' ' || platform || ' ' || status FROM " + SCHEMA
				+ ".dce_link ORDER BY tender_id, id", String.class))
			.containsExactly("1 https://www.docs.test/c?id=1&org=a docs.test AVAILABLE",
					"2 https://WWW.Docs.test/c/2 docs.test AVAILABLE", "3 https://profile.test profile.test NOT_ELIGIBLE");
		assertThat(jdbc.queryForList("SELECT platform_url FROM " + SCHEMA + ".tender ORDER BY id", String.class))
			.containsExactly("https://www.docs.test/c?id=1&org=a", "https://kept.test/2", "https://profile.test");
		assertThat(jdbc.queryForList("SELECT tender_id || ' ' || alert_revision FROM " + SCHEMA
				+ ".tender_eligibility ORDER BY tender_id", String.class))
			.containsExactly("1 -1", "3 3");
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
