package eu.gpefrance.tenders.collect;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import eu.gpefrance.tenders.TestcontainersConfiguration;

/**
 * V16 gives the tenders already collected from the BOAMP the procedure
 * identifier of their notices, and only when their notices agree.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ProcedureIdMigrationTests {

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void cleanDatabase() {
		jdbc.execute("TRUNCATE tender_follow, tender_notice, lot, tender, raw_notice RESTART IDENTITY CASCADE");
	}

	@Test
	void fillsTheIdentifierGivenByTheNoticesOfEachTender() {
		long single = tender("BOAMP", "26-1", null);
		notice(single, "BOAMP", "26-1", "AB-CD");
		notice(single, "BOAMP", "26-2", "ab-cd");
		long withoutIdentifier = tender("BOAMP", "26-3", null);
		notice(withoutIdentifier, "BOAMP", "26-3", null);
		long conflicting = tender("BOAMP", "26-4", null);
		notice(conflicting, "BOAMP", "26-4", "one");
		notice(conflicting, "BOAMP", "26-5", "two");
		long otherSource = tender("TED", "1-2026", null);
		notice(otherSource, "TED", "1-2026", "ted-procedure");
		long known = tender("BOAMP", "26-6", "kept");
		notice(known, "BOAMP", "26-6", "another");

		jdbc.execute(migrationBlock());

		assertThat(procedure(single)).isEqualTo("ab-cd");
		assertThat(procedure(withoutIdentifier)).isNull();
		assertThat(procedure(conflicting)).isNull();
		assertThat(procedure(otherSource)).isNull();
		assertThat(procedure(known)).isEqualTo("kept");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM tender", Long.class)).isEqualTo(5);
	}

	private long tender(String source, String externalId, String procedureId) {
		return jdbc.queryForObject("""
				INSERT INTO tender (source, external_id, title, status, created_at, updated_at, procedure_id)
				VALUES (?, ?, 'Travaux', 'ACTIVE', now(), now(), ?) RETURNING id""", Long.class, source, externalId,
				procedureId);
	}

	private void notice(long tenderId, String source, String externalId, String contractFolderId) {
		String payload = contractFolderId == null ? "{\"contractfolderid\": null}"
				: "{\"contractfolderid\": \"" + contractFolderId + "\"}";
		long raw = jdbc.queryForObject("""
				INSERT INTO raw_notice (source, external_id, payload, collected_at)
				VALUES (?, ?, CAST(? AS jsonb), now()) RETURNING id""", Long.class, source, externalId, payload);
		jdbc.update("INSERT INTO tender_notice (tender_id, raw_notice_id, external_id, kind) VALUES (?, ?, ?, 'INITIAL')",
				tenderId, raw, externalId);
	}

	private String procedure(long tenderId) {
		return jdbc.queryForObject("SELECT procedure_id FROM tender WHERE id = ?", String.class, tenderId);
	}

	private static String migrationBlock() {
		String migration;
		try (InputStream in = ProcedureIdMigrationTests.class
			.getResourceAsStream("/db/migration/V16__add_tender_procedure_id.sql")) {
			migration = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		return migration.substring(migration.indexOf("DO $$"), migration.lastIndexOf("END $$;") + "END $$;".length());
	}

}
