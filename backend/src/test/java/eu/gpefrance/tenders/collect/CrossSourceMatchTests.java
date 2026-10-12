package eu.gpefrance.tenders.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import eu.gpefrance.tenders.ClockConfiguration;
import eu.gpefrance.tenders.TestcontainersConfiguration;
import eu.gpefrance.tenders.collect.boamp.BoampClient;
import eu.gpefrance.tenders.collect.ted.TedClient;
import eu.gpefrance.tenders.document.DceLink;
import eu.gpefrance.tenders.document.DceLinkService;
import eu.gpefrance.tenders.tender.NoticeKind;
import eu.gpefrance.tenders.tender.Tender;
import eu.gpefrance.tenders.tender.TenderDetailResponse;
import eu.gpefrance.tenders.tender.TenderMatch;
import eu.gpefrance.tenders.tender.TenderMatchRepository;
import eu.gpefrance.tenders.tender.TenderNotice;
import eu.gpefrance.tenders.tender.TenderRepository;
import eu.gpefrance.tenders.tender.TenderService;
import eu.gpefrance.tenders.tender.TenderStatus;

/**
 * The same consultation published by the BOAMP and by TED, recognised by its
 * eForms procedure identifier: TED never makes a second tender of a BOAMP
 * consultation and never changes it; the other way round, or when the
 * procedure is ambiguous, a person decides.
 */
@SpringBootTest(properties = "collect.disabled-sources=")
@Import(TestcontainersConfiguration.class)
class CrossSourceMatchTests {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);

	/** Procedure of the TED fixture works-lots. */
	private static final String PROCEDURE = "5c87fdc2-d20a-42e9-9b11-59f45d4a37af";

	@Autowired
	private CollectService service;

	@Autowired
	private TenderRepository tenders;

	@Autowired
	private TenderMatchRepository matches;

	@Autowired
	private RawNoticeRepository rawNotices;

	@Autowired
	private TenderService tenderService;

	@Autowired
	private DceLinkService dceLinks;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private TransactionTemplate transaction;

	@MockitoBean
	private BoampClient boampClient;

	@MockitoBean
	private TedClient tedClient;

	@TestBean
	private Clock clock;

	static Clock clock() {
		return Clock.fixed(Instant.parse("2026-10-05T08:00:00Z"), ClockConfiguration.ZONE);
	}

	@BeforeEach
	void cleanDatabase() {
		jdbc.execute("TRUNCATE collect_run, dce_link, tender_match, tender_follow, tender_notice, lot, tender, "
				+ "raw_notice "
				+ "RESTART IDENTITY CASCADE");
		given(boampClient.fetchPublishedOn(any())).willReturn(List.of());
		given(tedClient.fetchPublishedOn(any())).willReturn(List.of());
	}

	@Test
	void tedNoticesOfABoampConsultationJoinItsHistoryWithoutChangingIt() {
		given(boampClient.fetchPublishedOn(any()))
			.willReturn(List.of(boamp("26-1", "INITIAL", null, PROCEDURE.toUpperCase(), "2026-11-12T11:00:00Z")));
		given(tedClient.fetchPublishedOn(any()))
			.willReturn(List.of(ted(tedInitial()), ted(tedChange("698900-2026", "2026-11-20")),
					ted(tedAward("698950-2026"))));

		List<CollectRun> runs = service.collectActive(CollectTrigger.SCHEDULED);

		assertThat(runs).extracting(CollectRun::getSource).containsExactly("BOAMP", "TED");
		assertThat(runs.get(1).getTendersCreated()).isZero();
		assertThat(runs.get(1).getTendersUpdated()).isEqualTo(3);
		assertThat(tenders.findAll()).singleElement().satisfies(tender -> {
			assertThat(tender.getSource()).isEqualTo("BOAMP");
			assertThat(tender.getTitle()).isEqualTo("Travaux 26-1");
			assertThat(tender.getDeadline()).isEqualTo(Instant.parse("2026-11-12T11:00:00Z"));
			assertThat(tender.getStatus()).isEqualTo(TenderStatus.ACTIVE);
			assertThat(tender.getDepartments()).containsExactly("13");
		});
		inTransaction(() -> assertThat(onlyTender().getNotices())
			.extracting(TenderNotice::getSource, TenderNotice::getExternalId, TenderNotice::getKind)
			.containsExactly(tuple("BOAMP", "26-1", NoticeKind.INITIAL), tuple("TED", "698125-2026", NoticeKind.INITIAL),
					tuple("TED", "698900-2026", NoticeKind.RECTIFICATION),
					tuple("TED", "698950-2026", NoticeKind.AWARD)));
		// The addresses given by the TED notices (DCE page, buyer profile of the award) are kept apart, with
		// their source; the BOAMP tender keeps its own.
		assertThat(onlyTender().getPlatformUrl()).isNull();
		assertThat(dceLinks.linksOf(onlyTender().getId())).extracting(DceLink::getSource, DceLink::getPlatform)
			.containsExactly(tuple("TED", "marches-publics.info"), tuple("TED", "achats.hauts-de-seine.fr"));
		TenderDetailResponse detail = tenderService.detail(onlyTender().getId(), 1L);
		assertThat(detail.result()).isNull();
		assertThat(detail.notices()).extracting(TenderDetailResponse.NoticeResponse::source)
			.containsExactly("BOAMP", "TED", "TED", "TED");
		assertThat(matches.count()).isZero();
	}

	@Test
	void aBoampRectificationStillAppliesAfterALaterTedNotice() {
		given(boampClient.fetchPublishedOn(any()))
			.willReturn(List.of(boamp("26-1", "INITIAL", null, PROCEDURE, "2026-11-12T11:00:00Z")));
		// Published by TED after the BOAMP rectification below.
		given(tedClient.fetchPublishedOn(any())).willReturn(List.of(ted(tedInitial().replace("2026-10-09", "2026-10-20"))));
		service.collectActive(CollectTrigger.SCHEDULED);

		given(boampClient.fetchPublishedOn(any())).willReturn(List.of(
				boamp("26-1", "INITIAL", null, PROCEDURE, "2026-11-12T11:00:00Z"),
				boamp("26-2", "RECTIFICATIF", "26-1", PROCEDURE, "2026-11-30T11:00:00Z")));
		service.collect("BOAMP", CollectTrigger.MANUAL, TODAY);

		assertThat(onlyTender().getTitle()).isEqualTo("Travaux 26-2");
		assertThat(onlyTender().getDeadline()).isEqualTo(Instant.parse("2026-11-30T11:00:00Z"));
	}

	@Test
	void aTedNoticeWhoseProcedureHasSeveralTendersIsSetAsideForAPerson() {
		given(boampClient.fetchPublishedOn(any())).willReturn(List.of(
				boamp("26-1", "INITIAL", null, PROCEDURE, null), boamp("26-7", "INITIAL", null, PROCEDURE, null)));
		given(tedClient.fetchPublishedOn(any())).willReturn(List.of(ted(tedInitial())));

		service.collectActive(CollectTrigger.SCHEDULED);

		assertThat(tenders.findAll()).extracting(Tender::getSource).containsOnly("BOAMP").hasSize(2);
		assertThat(rawNotices.findAll()).filteredOn(raw -> raw.getSource().equals("TED"))
			.singleElement()
			.extracting(RawNotice::getNormalizationNote)
			.isEqualTo(NormalizationService.SHARED_PROCEDURE);
		inTransaction(() -> assertThat(tenders.findAll())
			.allSatisfy(tender -> assertThat(tender.getNotices()).extracting(TenderNotice::getSource).containsOnly("BOAMP")));
		assertThat(matches.count()).isZero();
	}

	@Test
	void aBoampConsultationPublishedAfterItsTedTenderIsOnlyProposedAsAMatch() {
		given(tedClient.fetchPublishedOn(any())).willReturn(List.of(ted(tedInitial())));
		service.collectActive(CollectTrigger.SCHEDULED);
		Long tedTender = tenders.findBySourceAndExternalId("TED", "698125-2026").orElseThrow().getId();
		jdbc.update("UPDATE tender SET follow_up_status = 'TO_STUDY', follow_up_comment = 'Vu sur TED' WHERE id = ?",
				tedTender);

		given(boampClient.fetchPublishedOn(any()))
			.willReturn(List.of(boamp("26-1", "INITIAL", null, PROCEDURE, "2026-11-12T11:00:00Z")));
		service.collectActive(CollectTrigger.SCHEDULED);

		Long boampTender = tenders.findBySourceAndExternalId("BOAMP", "26-1").orElseThrow().getId();
		assertThat(matches.findAll()).singleElement().satisfies(match -> {
			assertThat(match.getTenderId()).isEqualTo(boampTender);
			assertThat(match.getMatchedTenderId()).isEqualTo(tedTender);
			assertThat(match.getRule()).isEqualTo(TenderMatch.SAME_PROCEDURE);
			assertThat(match.getStatus()).isEqualTo(TenderMatch.MatchStatus.PROPOSED);
		});
		assertThat(jdbc.queryForMap("SELECT follow_up_status, follow_up_comment FROM tender WHERE id = ?", tedTender))
			.containsEntry("follow_up_status", "TO_STUDY")
			.containsEntry("follow_up_comment", "Vu sur TED");
		assertThat(tenderService.detail(tedTender, 1L).matches())
			.extracting(TenderDetailResponse.MatchResponse::tenderId, TenderDetailResponse.MatchResponse::source)
			.containsExactly(tuple(boampTender, "BOAMP"));

		// Collected again, nothing more is proposed.
		service.collectActive(CollectTrigger.SCHEDULED);
		assertThat(matches.count()).isEqualTo(1);
	}

	@Test
	void aTedConsultationAbsentFromTheBoampMakesItsOwnTender() {
		given(boampClient.fetchPublishedOn(any()))
			.willReturn(List.of(boamp("26-1", "INITIAL", null, "another-procedure", null)));
		given(tedClient.fetchPublishedOn(any())).willReturn(List.of(ted(tedInitial())));

		service.collectActive(CollectTrigger.SCHEDULED);

		assertThat(tenders.findAll()).extracting(Tender::getSource, Tender::getExternalId)
			.containsExactlyInAnyOrder(tuple("BOAMP", "26-1"), tuple("TED", "698125-2026"));
		assertThat(matches.count()).isZero();
	}

	private Tender onlyTender() {
		return tenders.findAll().getFirst();
	}

	private void inTransaction(Runnable assertions) {
		transaction.executeWithoutResult(status -> assertions.run());
	}

	private static SourceRecord boamp(String idweb, String etat, String linkedId, String procedure, String deadline) {
		String json = """
				{"idweb": "%s", "nature": "APPEL_OFFRE", "etat": "%s", "objet": "Travaux %s",
				 "dateparution": "2026-10-05", "annonce_lie": %s, "contractfolderid": "%s",
				 "code_departement": ["13"], "datelimitereponse": %s, "donnees": "{}"}
				""".formatted(idweb, etat, idweb, linkedId == null ? "null" : "[\"" + linkedId + "\"]", procedure,
				deadline == null ? "null" : "\"" + deadline + "\"");
		return new SourceRecord(idweb, TODAY, json);
	}

	private static SourceRecord ted(String payload) {
		String number = payload.replaceAll("(?s).*\"publication-number\": \"([^\"]+)\".*", "$1");
		return new SourceRecord(number, TODAY, payload);
	}

	private static String tedInitial() {
		return resource("/ted/works-lots.json");
	}

	private static String tedChange(String number, String deadline) {
		return tedInitial().replace("\"698125-2026\"", "\"" + number + "\"")
			.replace("\"notice-version\": 1", "\"notice-version\": 2, \"change-reason-code\": \"cor-buy\", "
					+ "\"change-notice-version-identifier\": \"128e9011-31c6-49c9-9b18-76b66293757a-01\"")
			.replace("2026-11-10+01:00", deadline + "+01:00");
	}

	private static String tedAward(String number) {
		return resource("/ted/award.json").replace("\"696545-2026\"", "\"" + number + "\"")
			.replace("1ff10451-6429-4570-aaa8-a22113d007b7", PROCEDURE);
	}

	private static String resource(String path) {
		try (InputStream in = CrossSourceMatchTests.class.getResourceAsStream(path)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
