package eu.gpefrance.tenders.collect.approch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import eu.gpefrance.tenders.collect.NoticeType;
import eu.gpefrance.tenders.collect.SourceNotice;
import eu.gpefrance.tenders.tender.MarketType;
import eu.gpefrance.tenders.tender.ProcedureType;
import eu.gpefrance.tenders.tender.TenderDetails;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Real projects of the APProch export of 21/10/2026.
 */
class ApprochNoticeParserTests {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final ApprochNoticeParser parser = new ApprochNoticeParser(JSON);

	@Test
	void readsAProjectAsAForecastWithoutDeadline() {
		SourceNotice notice = parser.parse(stored(project(0), "MINISTERE DE L'INTERIEUR"));

		assertThat(notice.externalId()).isEqualTo("66108");
		assertThat(notice.type()).isEqualTo(NoticeType.CALL_FOR_TENDERS);
		assertThat(notice.linkedIds()).isEmpty();
		assertThat(notice.procedureId()).isNull();
		TenderDetails details = notice.details();
		assertThat(details.title()).startsWith("Fourniture composants électroniques");
		assertThat(details.buyer()).isEqualTo("MINISTERE DE L'INTERIEUR");
		assertThat(details.procedureType()).isEqualTo(ProcedureType.PURCHASE_PROJECT);
		assertThat(details.marketTypes()).containsExactly(MarketType.SUPPLIES);
		assertThat(details.cpvCodes()).containsExactly("31681400", "31711100");
		assertThat(details.departments()).containsExactly("72");
		assertThat(details.publishedAt()).isEqualTo(LocalDate.of(2026, 10, 21));
		assertThat(details.deadline()).isNull();
		assertThat(details.estimatedAmount()).isNull();
		assertThat(details.reference()).isNull();
		assertThat(details.noticeUrl()).isEqualTo("https://projets-achats.marches-publics.gouv.fr/project/66108");
		assertThat(details.platformUrl())
			.isEqualTo("https://www.marches-publics.gouv.fr/entreprise/consultation/3032509?orgAcronyme=g6l");
		assertThat(details.lots()).isEmpty();
	}

	@Test
	void writesTheForecastAfterTheDescription() {
		String description = parser.parse(stored(project(0), null)).details().description();

		assertThat(description).startsWith("Fourniture de composants électroniques")
			.contains("Projet d'achat prévisionnel publié sur APProch : ce n'est pas un avis d'appel d'offres.",
					"Publication prévisionnelle de la consultation : 08/04/2026",
					"Date cible de remise des offres : 30/11/2026", "Procédure envisagée : Appel d'offres ouvert",
					"Montant estimé : 100k - 500k€", "Durée prévisionnelle : 48 mois");
	}

	@Test
	void leavesOutWhatTheProjectDoesNotSay() {
		SourceNotice notice = parser.parse(stored(project(1), null));

		assertThat(notice.details().platformUrl()).isNull();
		assertThat(notice.details().marketTypes()).containsExactly(MarketType.WORKS);
		assertThat(notice.details().description()).doesNotContain("Date cible", "Procédure envisagée");
	}

	@Test
	void keepsTheDepartmentsAsListedWithCorsica() {
		assertThat(parser.parse(stored(project(1), null)).details().departments()).hasSize(96)
			.contains("01", "2A", "2B", "95");
	}

	@Test
	void namesTheBuyerByItsSirenWhenItsNameIsUnknown() {
		assertThat(parser.parse(stored(project(0), null)).details().buyer())
			.isEqualTo("Entité acheteuse SIREN 110014016");
	}

	@Test
	void ignoresAProjectThatIsNoLongerOpen() {
		ObjectNode project = project(0);
		project.put("statut", "Clôturé");

		assertThat(parser.parse(stored(project, null)).type()).isEqualTo(NoticeType.OTHER);
	}

	@Test
	void keepsOnlyAnHttpLinkToTheConsultation() {
		ObjectNode project = project(0);
		project.put("lien_vers_la_consultation", "voir PLACE");

		assertThat(parser.parse(stored(project, null)).details().platformUrl()).isNull();
	}

	@Test
	void refusesAProjectWithoutCode() {
		ObjectNode project = project(0);
		project.remove("code");

		assertThatThrownBy(() -> parser.parse(stored(project, null))).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void republishesItsChangedProjects() {
		assertThat(parser.republishesChangedNotices()).isTrue();
		assertThat(parser.linksNoticesByProcedure()).isFalse();
		assertThat(parser.defersToOtherSources()).isFalse();
	}

	static ObjectNode project(int index) {
		return (ObjectNode) export().get(index);
	}

	static JsonNode export() {
		try (InputStream in = ApprochNoticeParserTests.class.getResourceAsStream("/approch/export.json")) {
			return JSON.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	static String stored(JsonNode project, String buyerName) {
		return stored(project, buyerName, LocalDate.of(2026, 10, 21));
	}

	static String stored(JsonNode project, String buyerName, LocalDate collectedOn) {
		ObjectNode stored = JSON.createObjectNode();
		stored.put("collectedOn", collectedOn.toString());
		stored.put("buyerName", buyerName);
		stored.set("project", project);
		return stored.toString();
	}

}
