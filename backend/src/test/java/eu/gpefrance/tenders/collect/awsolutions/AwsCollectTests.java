package eu.gpefrance.tenders.collect.awsolutions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpServerErrorException;

import eu.gpefrance.tenders.ClockConfiguration;
import eu.gpefrance.tenders.TestcontainersConfiguration;
import eu.gpefrance.tenders.collect.CollectRun;
import eu.gpefrance.tenders.collect.CollectService;
import eu.gpefrance.tenders.collect.CollectStatus;
import eu.gpefrance.tenders.collect.CollectTrigger;
import eu.gpefrance.tenders.collect.SourceRecord;
import eu.gpefrance.tenders.collect.approch.ApprochClient;
import eu.gpefrance.tenders.collect.boamp.BoampClient;
import eu.gpefrance.tenders.tender.NoticeKind;
import eu.gpefrance.tenders.tender.Tender;
import eu.gpefrance.tenders.tender.TenderNotice;
import eu.gpefrance.tenders.tender.TenderRepository;
import tools.jackson.databind.node.ObjectNode;

/**
 * The notices of marches-publics.info, built from the real pages, through the
 * whole collection, with the BOAMP: one tender per consultation, a changed
 * deadline applied, the data of the team and the BOAMP tenders left alone, a
 * match proposed only for the same reference and deadline.
 */
@SpringBootTest(properties = "collect.disabled-sources=TED,APPROCH")
@Import(TestcontainersConfiguration.class)
class AwsCollectTests {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);

	private static final Instant DEADLINE = Instant.parse("2026-11-09T11:00:00Z");

	@Autowired
	private CollectService service;

	@Autowired
	private TenderRepository tenders;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private TransactionTemplate transaction;

	@MockitoBean
	private AwsClient awsClient;

	@MockitoBean
	private BoampClient boampClient;

	@MockitoBean
	private ApprochClient approchClient;

	@TestBean
	private Clock clock;

	static Clock clock() {
		return Clock.fixed(Instant.parse("2026-10-21T11:00:00Z"), ClockConfiguration.ZONE);
	}

	@BeforeEach
	void cleanDatabase() {
		jdbc.execute("TRUNCATE collect_run, tender_follow, tender_notice, tender_match, lot, dce_link, tender, "
				+ "raw_notice RESTART IDENTITY CASCADE");
		given(boampClient.fetchPublishedOn(any())).willReturn(List.of());
		given(awsClient.fetchPublishedOn(any(), any())).willReturn(List.of());
	}

	@Test
	void aNoticeMakesOneTenderWithItsDceLink() {
		CollectRun run = collectAws(cantal("09/11/26 à 12h00"));

		assertThat(run.getStatus()).isEqualTo(CollectStatus.SUCCESS);
		assertThat(run.getTendersCreated()).isEqualTo(1);
		inTransaction(() -> {
			Tender tender = tenders.findBySourceAndExternalId("AWS", "IDM-1892875").orElseThrow();
			assertThat(tender.getReference()).isEqualTo("2026TXSSTA0178");
			assertThat(tender.getDeadline()).isEqualTo(DEADLINE);
			assertThat(tender.getDepartments()).containsExactly("15");
		});
		assertThat(jdbc.queryForList("SELECT url FROM dce_link WHERE source = 'AWS'", String.class))
			.singleElement()
			.asString()
			.contains("IDM=1892875");
	}

	@Test
	void aSecondCollectionOfTheSameNoticeAddsNothing() {
		collectAws(cantal("09/11/26 à 12h00"));

		CollectRun again = collectAws(cantal("09/11/26 à 12h00"));

		assertThat(again.getNoticesNew()).isZero();
		assertThat(tenders.count()).isEqualTo(1);
	}

	@Test
	void aChangedDeadlineUpdatesTheTenderAndLeavesTheTeamDataAlone() {
		collectAws(cantal("09/11/26 à 12h00"));
		Long id = tenders.findAll().getFirst().getId();
		jdbc.update("UPDATE tender SET follow_up_status = 'RESPONDING', follow_up_comment = 'Chiffrage' WHERE id = ?",
				id);

		collectAws(cantal("16/11/26 à 12h00"));

		assertThat(tenders.count()).isEqualTo(1);
		inTransaction(() -> {
			Tender tender = tenders.findById(id).orElseThrow();
			assertThat(tender.getDeadline()).isEqualTo(Instant.parse("2026-11-16T11:00:00Z"));
			assertThat(tender.getNotices()).extracting(TenderNotice::getKind)
				.containsExactly(NoticeKind.INITIAL, NoticeKind.RECTIFICATION);
		});
		assertThat(jdbc.queryForMap("SELECT follow_up_status, follow_up_comment FROM tender WHERE id = ?", id))
			.containsEntry("follow_up_status", "RESPONDING")
			.containsEntry("follow_up_comment", "Chiffrage");
	}

	@Test
	void aBoampTenderWithTheSameReferenceAndDeadlineIsOnlyProposed() {
		given(boampClient.fetchPublishedOn(TODAY)).willReturn(List.of(boamp("26-1", "2026TXSSTA0178")));
		given(awsClient.fetchPublishedOn(any(), any())).willReturn(List.of(cantal("09/11/26 à 12h00")));

		List<CollectRun> runs = service.collectActive(CollectTrigger.SCHEDULED);

		assertThat(runs).extracting(CollectRun::getSource).containsExactly("BOAMP", "AWS");
		assertThat(tenders.count()).isEqualTo(2);
		assertThat(matches()).containsExactly(tuple("AWS", "BOAMP", "SAME_REFERENCE", "PROPOSED"));
		inTransaction(() -> {
			Tender boampTender = tenders.findBySourceAndExternalId("BOAMP", "26-1").orElseThrow();
			assertThat(boampTender.getNotices()).extracting(TenderNotice::getSource).containsExactly("BOAMP");
			assertThat(boampTender.getDeadline()).isEqualTo(DEADLINE);
		});
	}

	@Test
	void aBoampTenderCreatedAfterIsProposedToo() {
		collectAws(cantal("09/11/26 à 12h00"));
		given(boampClient.fetchPublishedOn(TODAY)).willReturn(List.of(boamp("26-1", "2026TXSSTA0178")));

		service.collect("BOAMP", CollectTrigger.MANUAL, TODAY);

		assertThat(matches()).containsExactly(tuple("BOAMP", "AWS", "SAME_REFERENCE", "PROPOSED"));
	}

	@Test
	void anotherDeadlineOrReferenceProposesNothing() {
		given(boampClient.fetchPublishedOn(TODAY))
			.willReturn(List.of(boamp("26-1", "2026TXSSTA0179"), boampWithDeadline("26-2", "2026TXSSTA0178",
					"2026-11-10T12:00:00+01:00")));
		service.collect("BOAMP", CollectTrigger.MANUAL, TODAY);

		collectAws(cantal("09/11/26 à 12h00"));

		assertThat(tenders.count()).isEqualTo(3);
		assertThat(matches()).isEmpty();
	}

	@Test
	void aFailingSiteFailsItsRunOnlyAndIsReadAgainNextTime() {
		given(boampClient.fetchPublishedOn(TODAY)).willReturn(List.of(boamp("26-1", "AUTRE-REF")));
		given(awsClient.fetchPublishedOn(any(), any()))
			.willThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE));

		List<CollectRun> runs = service.collectActive(CollectTrigger.SCHEDULED);

		assertThat(runs).extracting(CollectRun::getSource, CollectRun::getStatus)
			.containsExactly(tuple("BOAMP", CollectStatus.SUCCESS), tuple("AWS", CollectStatus.FAILED));
		assertThat(runs.get(1).getErrorMessage()).contains("503");
		assertThat(tenders.findAll()).extracting(Tender::getSource).containsExactly("BOAMP");

		CollectRun next = collectAws(cantal("09/11/26 à 12h00"));

		assertThat(next.getStatus()).isEqualTo(CollectStatus.SUCCESS);
		assertThat(tenders.count()).isEqualTo(2);
	}

	@Test
	void aProposedMatchLeavesTheTeamDataOfTheBoampTenderAlone() {
		given(boampClient.fetchPublishedOn(TODAY)).willReturn(List.of(boamp("26-1", "2026TXSSTA0178")));
		service.collect("BOAMP", CollectTrigger.MANUAL, TODAY);
		Long boampId = tenders.findBySourceAndExternalId("BOAMP", "26-1").orElseThrow().getId();
		jdbc.update("UPDATE tender SET follow_up_status = 'SUBMITTED', follow_up_comment = 'Offre déposée' "
				+ "WHERE id = ?", boampId);
		String before = row(boampId);

		collectAws(cantal("09/11/26 à 12h00"));
		collectAws(cantal("16/11/26 à 12h00"));

		assertThat(row(boampId)).isEqualTo(before);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM tender_notice WHERE tender_id = ?", Integer.class,
				boampId))
			.isEqualTo(1);
		assertThat(matches()).containsExactly(tuple("AWS", "BOAMP", "SAME_REFERENCE", "PROPOSED"));
	}

	private CollectRun collectAws(SourceRecord... records) {
		willReturn(Arrays.asList(records)).given(awsClient).fetchPublishedOn(any(), any());
		return service.collect("AWS", CollectTrigger.MANUAL, TODAY);
	}

	private List<org.assertj.core.groups.Tuple> matches() {
		return jdbc.queryForList("""
				SELECT t.source a, o.source b, m.rule, m.status FROM tender_match m
				JOIN tender t ON t.id = m.tender_id JOIN tender o ON o.id = m.matched_tender_id ORDER BY m.id""")
			.stream()
			.map((Map<String, Object> row) -> tuple(row.get("a"), row.get("b"), row.get("rule"), row.get("status")))
			.toList();
	}

	private String row(Long id) {
		return jdbc.queryForObject("SELECT row_to_json(t)::text FROM tender t WHERE id = ?", String.class, id);
	}

	private void inTransaction(Runnable assertions) {
		transaction.executeWithoutResult(status -> assertions.run());
	}

	/** The Cantal notice read on its real page, with the deadline shown in the list. */
	private static SourceRecord cantal(String deadline) {
		ObjectNode listing = AwsNoticeParserTests.cantalListing();
		listing.put("deadline", deadline);
		ObjectNode page = AwsPages.notice(AwsPagesTests.page("notice-mpi.html"));
		ObjectNode fields = (ObjectNode) page.path("fields");
		fields.put("Offres", "Remise des offres le " + deadline + " au plus tard.");
		return new SourceRecord("MPI-pub-20262941419#" + AwsClient.deadlineKey(deadline), TODAY,
				AwsNoticeParserTests.stored(listing, page));
	}

	private static SourceRecord boamp(String idweb, String reference) {
		return boampWithDeadline(idweb, reference, "2026-11-09T12:00:00+01:00");
	}

	private static SourceRecord boampWithDeadline(String idweb, String reference, String deadline) {
		String json = """
				{"idweb": "%s", "nature": "APPEL_OFFRE", "etat": "INITIAL", "objet": "Travaux de plantations",
				 "dateparution": "2026-10-21", "code_departement": ["15"], "datelimitereponse": "%s",
				 "donnees": "{\\"identifiantInterne\\": \\"%s\\"}"}""".formatted(idweb, deadline, reference);
		return new SourceRecord(idweb, TODAY, json);
	}

}
