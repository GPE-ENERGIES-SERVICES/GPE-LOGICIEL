package eu.gpefrance.tenders.collect.approch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import eu.gpefrance.tenders.ClockConfiguration;
import eu.gpefrance.tenders.TestcontainersConfiguration;
import eu.gpefrance.tenders.collect.CollectRun;
import eu.gpefrance.tenders.collect.CollectService;
import eu.gpefrance.tenders.collect.CollectStatus;
import eu.gpefrance.tenders.collect.CollectTrigger;
import eu.gpefrance.tenders.collect.SourceRecord;
import eu.gpefrance.tenders.collect.boamp.BoampClient;
import eu.gpefrance.tenders.tender.NoticeKind;
import eu.gpefrance.tenders.tender.ProcedureType;
import eu.gpefrance.tenders.tender.Tender;
import eu.gpefrance.tenders.tender.TenderNotice;
import eu.gpefrance.tenders.tender.TenderRepository;
import eu.gpefrance.tenders.tender.TenderStatus;
import tools.jackson.databind.node.ObjectNode;

/**
 * The APProch projects, read from the real fixtures, through the whole
 * collection: one tender per project whatever the number of collections, a
 * changed project updating its tender, the data of the team left alone.
 */
@SpringBootTest(properties = "collect.disabled-sources=TED,AWS")
@Import(TestcontainersConfiguration.class)
class ApprochCollectTests {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);

	private static final String INTERIOR = "MINISTERE DE L'INTERIEUR";

	@Autowired
	private CollectService service;

	@Autowired
	private TenderRepository tenders;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private TransactionTemplate transaction;

	@MockitoBean
	private ApprochClient approchClient;

	@MockitoBean
	private BoampClient boampClient;

	@TestBean
	private Clock clock;

	static Clock clock() {
		return Clock.fixed(Instant.parse("2026-10-21T11:00:00Z"), ClockConfiguration.ZONE);
	}

	@BeforeEach
	void cleanDatabase() {
		jdbc.execute("TRUNCATE collect_run, tender_follow, tender_notice, lot, dce_link, tender, raw_notice "
				+ "RESTART IDENTITY CASCADE");
		given(boampClient.fetchPublishedOn(any())).willReturn(List.of());
	}

	@Test
	void eachProjectMakesOneForecastTenderWithItsConsultationLink() {
		CollectRun run = collect(version(project(0), TODAY), version(project(1), TODAY));

		assertThat(run.getStatus()).isEqualTo(CollectStatus.SUCCESS);
		assertThat(run.getTendersCreated()).isEqualTo(2);
		inTransaction(() -> {
			Tender tender = tenders.findBySourceAndExternalId("APPROCH", "66108").orElseThrow();
			assertThat(tender.getProcedureType()).isEqualTo(ProcedureType.PURCHASE_PROJECT);
			assertThat(tender.getBuyer()).isEqualTo(INTERIOR);
			assertThat(tender.getDeadline()).isNull();
			assertThat(tender.getPublishedAt()).isEqualTo(TODAY);
			assertThat(tender.getStatus()).isEqualTo(TenderStatus.ACTIVE);
			assertThat(tender.getProcedureId()).isNull();
			assertThat(tender.getNotices()).extracting(TenderNotice::getKind).containsExactly(NoticeKind.INITIAL);
		});
		assertThat(jdbc.queryForList("SELECT url FROM dce_link WHERE source = 'APPROCH'", String.class))
			.containsExactly("https://www.marches-publics.gouv.fr/entreprise/consultation/3032509?orgAcronyme=g6l");
	}

	@Test
	void readsTheProjectsOnceWhateverThePeriodCollected() {
		collect(TODAY.minusDays(5), version(project(0), TODAY));

		then(approchClient).should(times(1)).fetchCurrent(any());
		then(approchClient).should().fetchCurrent(TODAY);
	}

	@Test
	void aSecondCollectionOfUnchangedProjectsAddsNothing() {
		collect(version(project(0), TODAY), version(project(1), TODAY));

		CollectRun again = collect(version(project(0), TODAY), version(project(1), TODAY));

		assertThat(again.getNoticesNew()).isZero();
		assertThat(again.getNoticesKnown()).isEqualTo(2);
		assertThat(tenders.count()).isEqualTo(2);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM tender_notice", Integer.class)).isEqualTo(2);
	}

	@Test
	void aChangedProjectUpdatesItsTenderAndLeavesTheTeamDataAlone() {
		collect(version(project(0), TODAY.minusDays(1)));
		Long id = tenders.findAll().getFirst().getId();
		jdbc.update("UPDATE tender SET follow_up_status = 'TO_STUDY', follow_up_comment = 'À suivre' WHERE id = ?",
				id);
		ObjectNode changed = project(0);
		changed.put("libelle", "Fourniture de composants pour la gendarmerie");
		changed.put("date_cible_de_remise_des_offres", "2026-12-15");

		CollectRun run = collect(version(project(0), TODAY.minusDays(1)), version(changed, TODAY));

		assertThat(run.getNoticesNew()).isEqualTo(1);
		assertThat(run.getTendersUpdated()).isEqualTo(1);
		assertThat(tenders.count()).isEqualTo(1);
		inTransaction(() -> {
			Tender tender = tenders.findById(id).orElseThrow();
			assertThat(tender.getTitle()).isEqualTo("Fourniture de composants pour la gendarmerie");
			assertThat(tender.getDescription()).contains("Date cible de remise des offres : 15/12/2026");
			assertThat(tender.getPublishedAt()).isEqualTo(TODAY.minusDays(1));
			assertThat(tender.getNotices()).extracting(TenderNotice::getExternalId, TenderNotice::getKind)
				.containsExactly(tuple("66108", NoticeKind.INITIAL), tuple("66108", NoticeKind.RECTIFICATION));
		});
		assertThat(jdbc.queryForMap("SELECT follow_up_status, follow_up_comment FROM tender WHERE id = ?", id))
			.containsEntry("follow_up_status", "TO_STUDY")
			.containsEntry("follow_up_comment", "À suivre");
	}

	@Test
	void anOlderVersionReadAfterANewerOneOnlyCompletesTheHistory() {
		ObjectNode changed = project(0);
		changed.put("libelle", "Nouveau libellé");
		collect(version(changed, TODAY));

		collect(version(project(0), TODAY.minusDays(1)));

		inTransaction(() -> assertThat(tenders.findAll()).singleElement()
			.satisfies(tender -> assertThat(tender.getTitle()).isEqualTo("Nouveau libellé"))
			.satisfies(tender -> assertThat(tender.getNotices()).hasSize(2)));
	}

	@Test
	void aProjectNoLongerOpenMakesNoTender() {
		ObjectNode closed = project(0);
		closed.put("statut", "Clôturé");

		CollectRun run = collect(version(closed, TODAY));

		assertThat(tenders.count()).isZero();
		assertThat(run.getNoticesIgnored()).isEqualTo(1);
	}

	@Test
	void isCollectedAfterTheBoampWhichItNeverChanges() {
		String boamp = """
				{"idweb": "26-1", "nature": "APPEL_OFFRE", "etat": "INITIAL",
				 "objet": "Fourniture composants électroniques, électriques et diverses pièces de rechange",
				 "dateparution": "2026-10-21", "contractfolderid": "5c87fdc2-d20a-42e9-9b11-59f45d4a37af",
				 "code_departement": ["72"], "datelimitereponse": "2026-11-30T11:00:00Z", "donnees": "{}"}""";
		given(boampClient.fetchPublishedOn(any())).willReturn(List.of());
		given(boampClient.fetchPublishedOn(TODAY)).willReturn(List.of(new SourceRecord("26-1", TODAY, boamp)));
		given(approchClient.fetchCurrent(any())).willReturn(List.of(version(project(0), TODAY)));

		List<CollectRun> runs = service.collectActive(CollectTrigger.SCHEDULED);

		assertThat(runs).extracting(CollectRun::getSource).containsExactly("BOAMP", "APPROCH");
		assertThat(tenders.count()).isEqualTo(2);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM tender_match", Integer.class)).isZero();
		inTransaction(() -> {
			Tender boampTender = tenders.findBySourceAndExternalId("BOAMP", "26-1").orElseThrow();
			assertThat(boampTender.getNotices()).extracting(TenderNotice::getSource).containsExactly("BOAMP");
			assertThat(boampTender.getDeadline()).isEqualTo(Instant.parse("2026-11-30T11:00:00Z"));
		});
	}

	@Test
	void theProjectsAreNotCollectedByDefault() throws IOException {
		String properties = new ClassPathResource("application.properties").getContentAsString(StandardCharsets.UTF_8);

		assertThat(properties).containsPattern("(?m)^collect\\.disabled-sources=APPROCH,AWS$");
	}

	private CollectRun collect(SourceRecord... records) {
		return collect(TODAY, records);
	}

	private CollectRun collect(LocalDate since, SourceRecord... records) {
		given(approchClient.fetchCurrent(any())).willReturn(Arrays.asList(records));
		return service.collect("APPROCH", CollectTrigger.MANUAL, since);
	}

	private void inTransaction(Runnable assertions) {
		transaction.executeWithoutResult(status -> assertions.run());
	}

	private static ObjectNode project(int index) {
		return ApprochNoticeParserTests.project(index);
	}

	/** A project as the client stores it when read on the given day. */
	private static SourceRecord version(ObjectNode project, LocalDate day) {
		return new SourceRecord(project.path("code").asString() + "-" + ApprochClient.digest(project, INTERIOR), day,
				ApprochNoticeParserTests.stored(project, INTERIOR, day));
	}

}
