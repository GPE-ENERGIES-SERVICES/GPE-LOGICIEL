package eu.gpefrance.tenders.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import eu.gpefrance.tenders.TestUsers;
import eu.gpefrance.tenders.TestcontainersConfiguration;
import eu.gpefrance.tenders.tender.Tender;
import eu.gpefrance.tenders.tender.TenderDetails;
import eu.gpefrance.tenders.tender.TenderRepository;

/**
 * The links to the consultation file: kept once per address with the source
 * that gave it, listed apart from the files, and filled by V18 for the
 * tenders collected before.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DceLinkTests {

	private static final Instant FIRST = Instant.parse("2026-10-05T05:00:00Z");

	private static final Instant LATER = Instant.parse("2026-10-06T05:00:00Z");

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private DceLinkService service;

	@Autowired
	private DceLinkRepository links;

	@Autowired
	private TenderRepository tenders;

	@Autowired
	private JdbcTemplate jdbc;

	private Tender tender;

	@BeforeEach
	void createTender() {
		jdbc.execute("TRUNCATE dce_link, document_page, document, dossier, tender_notice, lot, tender "
				+ "RESTART IDENTITY CASCADE");
		tender = tenders.save(new Tender("BOAMP", "26-1", details("https://www.marches-securises.fr"), FIRST));
	}

	@Test
	void keepsEachAddressOnceWithTheSourceThatGaveIt() {
		service.record(tender.getId(), "BOAMP", "https://www.marches-securises.fr", FIRST);
		service.record(tender.getId(), "TED", " https://www.marches-publics.info/avis?IDM=1&type=DCE ", FIRST);
		service.record(tender.getId(), "BOAMP", "https://www.marches-securises.fr", LATER);
		service.record(tender.getId(), "BOAMP", "www.sans-protocole.fr", LATER);
		service.record(tender.getId(), "BOAMP", null, LATER);

		assertThat(service.linksOf(tender.getId()))
			.extracting(DceLink::getSource, DceLink::getUrl, DceLink::getPlatform, DceLink::getStatus,
					DceLink::getLastSeenAt)
			.containsExactly(
					tuple("BOAMP", "https://www.marches-securises.fr", "marches-securises.fr", DceLinkStatus.AVAILABLE,
							LATER),
					tuple("TED", "https://www.marches-publics.info/avis?IDM=1&type=DCE", "marches-publics.info",
							DceLinkStatus.AVAILABLE, FIRST));
	}

	@Test
	void listsTheLinksOfATenderApartFromItsFiles() {
		service.record(tender.getId(), "TED", "https://achats.example.fr/consultation/42", FIRST);

		assertThat(mvc.get().uri("/api/tenders/{id}/dce-links", tender.getId()).with(TestUsers.user()))
			.hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					[{"source": "TED", "url": "https://achats.example.fr/consultation/42",
					  "platform": "achats.example.fr", "status": "AVAILABLE", "statusDetail": null,
					  "firstSeenAt": "2026-10-05T05:00:00Z", "checkedAt": null}]""");
		assertThat(mvc.get().uri("/api/tenders/{id}/dossiers", tender.getId()).with(TestUsers.user()))
			.hasStatusOk()
			.bodyJson()
			.isEqualTo("[]");
	}

	@Test
	void theMigrationKeepsTheAddressOfEachTenderCollectedBefore() {
		Tender withoutAddress = tenders.save(new Tender("BOAMP", "26-2", details(null), FIRST));
		Tender notAnAddress = tenders.save(new Tender("BOAMP", "26-3", details("voir le règlement"), FIRST));
		Tender fromTed = tenders.save(new Tender("TED", "1-2026", details("http://WWW.Achats.fr/dce"), FIRST));
		service.record(tender.getId(), "BOAMP", "https://www.marches-securises.fr", LATER);

		jdbc.execute(migrationInsert());

		assertThat(links.findAll()).extracting(DceLink::getTenderId, DceLink::getSource, DceLink::getPlatform)
			.containsExactlyInAnyOrder(tuple(tender.getId(), "BOAMP", "marches-securises.fr"),
					tuple(fromTed.getId(), "TED", "achats.fr"));
		assertThat(service.linksOf(withoutAddress.getId())).isEmpty();
		assertThat(service.linksOf(notAnAddress.getId())).isEmpty();
	}

	@Test
	void findsThePlatformOfAnHttpAddressOnly() {
		assertThat(DceLinkService.platformOf("https://www.Marches-Publics.gouv.fr/?page=x")).isEqualTo("marches-publics.gouv.fr");
		assertThat(DceLinkService.platformOf("http://marches.maximilien.fr")).isEqualTo("marches.maximilien.fr");
		assertThat(DceLinkService.platformOf("ftp://example.fr")).isNull();
		assertThat(DceLinkService.platformOf("www.example.fr")).isNull();
		assertThat(DceLinkService.platformOf("https://exa mple.fr")).isNull();
		assertThat(DceLinkService.platformOf("")).isNull();
	}

	private static TenderDetails details(String platformUrl) {
		return new TenderDetails("Travaux", null, null, null, null, List.of("69"), List.of(), null, List.of(), null,
				null, null, null, platformUrl, List.of());
	}

	private static String migrationInsert() {
		String migration;
		try (InputStream in = DceLinkTests.class.getResourceAsStream("/db/migration/V18__create_dce_link.sql")) {
			migration = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		String insert = migration.substring(migration.indexOf("INSERT INTO dce_link"));
		return insert.substring(0, insert.indexOf(';'));
	}

}
