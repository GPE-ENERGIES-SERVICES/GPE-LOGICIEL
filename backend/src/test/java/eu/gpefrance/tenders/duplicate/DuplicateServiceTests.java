package eu.gpefrance.tenders.duplicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.support.TransactionTemplate;

import eu.gpefrance.tenders.ClockConfiguration;
import eu.gpefrance.tenders.TestUsers;
import eu.gpefrance.tenders.TestcontainersConfiguration;
import eu.gpefrance.tenders.collect.CollectService;
import eu.gpefrance.tenders.collect.CollectTrigger;
import eu.gpefrance.tenders.collect.SourceRecord;
import eu.gpefrance.tenders.collect.boamp.BoampClient;
import eu.gpefrance.tenders.document.DceLinkService;
import eu.gpefrance.tenders.tender.NoticeKind;
import eu.gpefrance.tenders.tender.Tender;
import eu.gpefrance.tenders.tender.TenderNotice;
import eu.gpefrance.tenders.tender.TenderRepository;
import eu.gpefrance.tenders.tender.TenderService;
import eu.gpefrance.tenders.tender.TenderState;
import eu.gpefrance.tenders.tender.TenderStatus;

/**
 * The duplicates as they were before the BOAMP fix, rebuilt from two real
 * notices of one procedure: the tender of the first rectification, answered
 * by the team and closed when its deadline (12/10) passed, and a second
 * tender made by the next rectification, which pushes the deadline to 16/10
 * and cites the first one.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DuplicateServiceTests {

	private static final Instant NOW = Instant.parse("2026-10-12T12:00:00Z");

	private static final String PROCEDURE = "bac8a6d9-67fe-4707-832a-25485824ecdc";

	@Autowired
	private DuplicateService service;

	@Autowired
	private CollectService collect;

	@Autowired
	private TenderRepository tenders;

	@Autowired
	private TenderService tenderService;

	@Autowired
	private DceLinkService dceLinks;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private TransactionTemplate transaction;

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private BoampClient boampClient;

	@TestBean
	private Clock clock;

	static Clock clock() {
		return Clock.fixed(NOW, ClockConfiguration.ZONE);
	}

	private Long answered;

	private Long duplicate;

	@BeforeEach
	void rebuildTheDuplicates() {
		jdbc.execute("TRUNCATE collect_run, duplicate_decision, dce_link, tender_match, tender_follow, tender_notice, "
				+ "lot, tender, raw_notice RESTART IDENTITY CASCADE");
		jdbc.execute("""
				INSERT INTO app_user (id, email, display_name, password_hash, role, enabled, created_at)
				VALUES (1, 'marie@gpe.test', 'Marie Durand', 'x', 'USER', true, now()) ON CONFLICT DO NOTHING""");
		given(boampClient.fetchPublishedOn(any())).willReturn(List.of(record(fixture("change-as-initial-1"))));
		collect.collect("BOAMP", CollectTrigger.MANUAL, LocalDate.of(2026, 10, 12));
		answered = tenders.findBySourceAndExternalId("BOAMP", "26-92510").orElseThrow().getId();
		jdbc.update("UPDATE tender SET follow_up_status = 'SUBMITTED', follow_up_comment = 'Offre déposée', "
				+ "follow_up_by = 'Marie Durand' WHERE id = ?", answered);
		// The second rectification, as the collection made it a tender before the fix.
		Long raw = jdbc.queryForObject("""
				INSERT INTO raw_notice (source, external_id, payload, published_at, collected_at, normalized_at)
				VALUES ('BOAMP', '26-97093', CAST(? AS jsonb), DATE '2026-10-09', now(), now()) RETURNING id""",
				Long.class, fixture("change-as-initial-2"));
		duplicate = jdbc.queryForObject("""
				INSERT INTO tender (source, external_id, procedure_id, title, departments, status, deadline,
				                    published_at, created_at, updated_at)
				VALUES ('BOAMP', '26-97093', ?, 'Travaux pour la phase 1 de la requalification', '{93}', 'ACTIVE',
				        TIMESTAMPTZ '2026-10-16 10:00:00+00', DATE '2026-10-09', now(), now()) RETURNING id""",
				Long.class, PROCEDURE);
		jdbc.update("""
				INSERT INTO tender_notice (tender_id, raw_notice_id, external_id, kind, published_at, deadline, source)
				VALUES (?, ?, '26-97093', 'INITIAL', DATE '2026-10-09', TIMESTAMPTZ '2026-10-16 10:00:00+00', 'BOAMP')""",
				duplicate, raw);
		dceLinks.record(duplicate, "BOAMP", "https://plateforme.example.fr/consultation/42", NOW);
	}

	@Test
	void explainsWhyTheTwoTendersAreDuplicates() {
		assertThat(service.groups()).singleElement().satisfies(group -> {
			assertThat(group.procedureId()).isEqualTo(PROCEDURE);
			assertThat(group.certain()).isTrue();
			assertThat(group.suggestedKeptId()).isEqualTo(answered);
			assertThat(group.explanation()).anyMatch(line -> line.contains("cite l'avis 26-92510"));
			assertThat(group.tenders()).extracting(DuplicateResponses.Candidate::teamData).containsExactly(true, false);
		});
	}

	@Test
	void aMergeGivesTheTenderKeptTheLaterDeadlineAndKeepsTheDecisionOfTheTeam() {
		DuplicateDecision decision = service.merge(answered, duplicate, false, "Marie Durand");

		Tender kept = tenders.findById(answered).orElseThrow();
		assertThat(kept.getDeadline()).isEqualTo(Instant.parse("2026-10-16T10:00:00Z"));
		assertThat(kept.getStatus()).isEqualTo(TenderStatus.ACTIVE);
		assertThat(jdbc.queryForMap("SELECT follow_up_status, follow_up_comment, follow_up_by FROM tender WHERE id = ?",
				answered))
			.containsEntry("follow_up_status", "SUBMITTED")
			.containsEntry("follow_up_comment", "Offre déposée")
			.containsEntry("follow_up_by", "Marie Durand");
		transaction.executeWithoutResult(status -> assertThat(tenders.findById(answered).orElseThrow().getNotices())
			.extracting(TenderNotice::getExternalId, TenderNotice::getKind)
			.containsExactly(tuple("26-92510", NoticeKind.RECTIFICATION),
					tuple("26-97093", NoticeKind.RECTIFICATION)));
		assertThat(tenders.findById(duplicate).orElseThrow().getMergedIntoId()).isEqualTo(answered);
		assertThat(dceLinks.linksOf(answered)).extracting(link -> link.getUrl())
			.contains("https://plateforme.example.fr/consultation/42");
		assertThat(service.groups()).isEmpty();
		assertThat(decision.getReason()).contains("cite l'avis 26-92510");
		// The duplicate stays readable but is listed nowhere.
		assertThat(tenderService.detail(duplicate, 1L).mergedIntoId()).isEqualTo(answered);
		assertThat(mvc.get().uri("/api/tenders?tab=ALL&size=100").with(TestUsers.user()))
			.hasStatusOk()
			.bodyJson()
			.extractingPath("$.content[*].id")
			.asArray()
			.contains(answered.intValue())
			.doesNotContain(duplicate.intValue());
	}

	@Test
	void undoingAMergeGivesBackExactlyThePreviousState() {
		TenderState before = transaction.execute(status -> TenderState.of(tenders.findById(answered).orElseThrow()));
		DuplicateDecision decision = service.merge(answered, duplicate, false, "Marie Durand");

		service.undo(decision.getId(), "Marie Durand");

		transaction.executeWithoutResult(status -> {
			assertThat(TenderState.of(tenders.findById(answered).orElseThrow())).isEqualTo(before);
			Tender other = tenders.findById(duplicate).orElseThrow();
			assertThat(other.getMergedIntoId()).isNull();
			// Back with the kind it had before the merge.
			assertThat(other.getNotices()).extracting(TenderNotice::getExternalId, TenderNotice::getKind)
				.containsExactly(tuple("26-97093", NoticeKind.INITIAL));
		});
		assertThat(dceLinks.linksOf(duplicate)).hasSize(1);
		assertThat(service.groups()).hasSize(1);
		assertThat(service.history()).singleElement().satisfies(undone -> {
			assertThat(undone.getUndoneBy()).isEqualTo("Marie Durand");
			assertThat(undone.isActive()).isFalse();
		});
		assertThatThrownBy(() -> service.undo(decision.getId(), "Marie Durand"))
			.isInstanceOf(DuplicateRuleException.class);
	}

	@Test
	void neverHidesTheDataOfTheTeam() {
		// Only the duplicate to merge has data of the team: refused.
		assertThatThrownBy(() -> service.merge(duplicate, answered, true, "Marie Durand"))
			.isInstanceOf(DuplicateRuleException.class)
			.hasMessageContaining("conservez-la");
		// Both have: only with an explicit acknowledgement.
		jdbc.update("UPDATE tender SET follow_up_status = 'TO_STUDY' WHERE id = ?", duplicate);
		assertThatThrownBy(() -> service.merge(answered, duplicate, false, "Marie Durand"))
			.isInstanceOf(DuplicateRuleException.class)
			.hasMessageContaining("confirmez explicitement");
		service.merge(answered, duplicate, true, "Marie Durand");
		assertThat(jdbc.queryForObject("SELECT follow_up_status FROM tender WHERE id = ?", String.class, duplicate))
			.isEqualTo("TO_STUDY");
	}

	@Test
	void neverMergesTendersOfDifferentProcedures() {
		jdbc.update("UPDATE tender SET procedure_id = 'another' WHERE id = ?", duplicate);

		assertThatThrownBy(() -> service.merge(answered, duplicate, false, "Marie Durand"))
			.isInstanceOf(DuplicateRuleException.class)
			.hasMessageContaining("même identifiant de procédure");
	}

	@Test
	void twoTendersSaidDifferentAreNoLongerProposed() {
		DuplicateDecision decision = service.notDuplicates(answered, duplicate, "Marie Durand");
		assertThat(service.groups()).isEmpty();
		assertThat(tenders.findById(duplicate).orElseThrow().getMergedIntoId()).isNull();

		service.undo(decision.getId(), "Marie Durand");
		assertThat(service.groups()).hasSize(1);
	}

	@Test
	void aBatchOnlyMergesWhatThePersonRead() {
		DuplicateResponses.BatchPreview preview = service.previewBatch();
		assertThat(preview.merges()).singleElement().satisfies(merge -> {
			assertThat(merge.keptTenderId()).isEqualTo(answered);
			assertThat(merge.mergedTenderId()).isEqualTo(duplicate);
		});
		assertThat(tenders.findById(duplicate).orElseThrow().getMergedIntoId()).isNull();

		assertThatThrownBy(() -> service.mergeBatch("another token", "Marie Durand"))
			.isInstanceOf(DuplicateRuleException.class);
		assertThat(service.mergeBatch(preview.token(), "Marie Durand")).hasSize(1);
		assertThat(tenders.findById(duplicate).orElseThrow().getMergedIntoId()).isEqualTo(answered);
	}

	@Test
	void aMergeIsNotUndoneOnceTheTenderKeptReceivedAnotherNotice() {
		DuplicateDecision decision = service.merge(answered, duplicate, false, "Marie Durand");
		Long raw = jdbc.queryForObject("""
				INSERT INTO raw_notice (source, external_id, payload, published_at, collected_at, normalized_at)
				VALUES ('BOAMP', '26-99999', CAST('{}' AS jsonb), DATE '2026-10-13', now(), now()) RETURNING id""",
				Long.class);
		jdbc.update("""
				INSERT INTO tender_notice (tender_id, raw_notice_id, external_id, kind, published_at, source)
				VALUES (?, ?, '26-99999', 'RECTIFICATION', DATE '2026-10-13', 'BOAMP')""", answered, raw);

		assertThatThrownBy(() -> service.undo(decision.getId(), "Marie Durand"))
			.isInstanceOf(DuplicateRuleException.class)
			.hasMessageContaining("nouvel avis");
	}

	@Test
	void onlyAnAdministratorDecides() {
		assertThat(mvc.get().uri("/api/duplicates").with(TestUsers.user())).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(mvc.post()
			.uri("/api/duplicates/merge")
			.with(TestUsers.user())
			.with(csrf())
			.contentType("application/json")
			.content("{\"keptTenderId\": " + answered + ", \"mergedTenderId\": " + duplicate + "}"))
			.hasStatus(HttpStatus.FORBIDDEN);
		assertThat(mvc.get().uri("/api/duplicates").with(TestUsers.admin())).hasStatusOk()
			.bodyJson()
			.extractingPath("$[0].certain")
			.isEqualTo(true);
		assertThat(tenders.findById(duplicate).orElseThrow().getMergedIntoId()).isNull();
	}

	private static SourceRecord record(String payload) {
		String idweb = payload.replaceAll("(?s).*\"idweb\": \"([^\"]+)\".*", "$1");
		String published = payload.replaceAll("(?s).*\"dateparution\": \"([^\"]+)\".*", "$1");
		return new SourceRecord(idweb, LocalDate.parse(published), payload);
	}

	private static String fixture(String name) {
		try (InputStream in = DuplicateServiceTests.class.getResourceAsStream("/boamp/" + name + ".json")) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
