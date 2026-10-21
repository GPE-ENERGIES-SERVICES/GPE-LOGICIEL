package eu.gpefrance.tenders.collect.awsolutions;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.node.ObjectNode;

/**
 * Real pages of marches-publics.info read by hand on 21/10/2026: the first
 * page of the works published on 20/10/2026, and two notices.
 */
class AwsPagesTests {

	@Test
	void readsEveryNoticeOfAListPage() {
		List<ObjectNode> listed = AwsPages.listing(page("list-page.html"));

		assertThat(listed).hasSize(10);
		assertThat(listed).allSatisfy(notice -> {
			assertThat(notice.path("publishedOn").asString()).isEqualTo("20/10/26");
			assertThat(notice.path("noticeId").asString()).matches("^(MPI|LDM|NR)-pub-\\d+$");
		});
		ObjectNode first = listed.getFirst();
		assertThat(first.path("buyer").asString()).isEqualTo("SA HLM DES CHALETS");
		assertThat(first.path("postCode").asString()).isEqualTo("31027");
		assertThat(first.path("reference").asString()).isEqualTo("LAUNAGUET ML3");
		assertThat(first.path("title").asString()).isEqualTo("Mobilier Cuisine");
		assertThat(first.path("deadline").asString()).isEqualTo("03/11/26 à 12h00");
		assertThat(first.path("noticeId").asString()).isEqualTo("LDM-pub-2026293007");
		assertThat(first.path("noticeUrl").asString())
			.isEqualTo("https://www.marches-publics.info/Annonces/LDM-pub-2026293007.htm");
		assertThat(first.path("consultationId").asString()).isEqualTo("42607");
		assertThat(first.path("dceUrl").asString()).contains("type=DCE", "IDM=42607");
	}

	@Test
	void readsTheNumberOfPagesFromTheirLinks() {
		assertThat(AwsPages.pageCount(page("list-page.html"))).isEqualTo(6);
		assertThat(AwsPages.pageCount(Jsoup.parse("<html><body></body></html>"))).isEqualTo(1);
	}

	@Test
	void readsTheLabelledRowsOfANotice() {
		ObjectNode notice = AwsPages.notice(page("notice-mpi.html"));

		assertThat(notice.path("heading").asString()).isEqualTo("AVIS D'APPEL PUBLIC A LA CONCURRENCE");
		assertThat(notice.path("buyer").asString()).isEqualTo("CONSEIL DÉPARTEMENTAL DU CANTAL");
		assertThat(notice.path("siret").asString()).isEqualTo("22150001000014");
		ObjectNode fields = (ObjectNode) notice.path("fields");
		assertThat(fields.path("Objet").asString()).isEqualTo("Travaux de plantations de haies et d'alignement d'arbres");
		assertThat(fields.path("Référence").asString()).isEqualTo("2026TXSSTA0178");
		assertThat(fields.path("Type de marché").asString()).isEqualTo("Travaux");
		assertThat(fields.path("Mode").asString()).isEqualTo("Procédure adaptée ouverte");
		assertThat(fields.path("Lieu d'exécution principal").asString()).isEqualTo("FRK12 - Cantal");
		assertThat(fields.path("Code CPV principal").asString()).startsWith("45112730");
		assertThat(fields.path("Offres").asString()).contains("09/11/26 à 12h00");
	}

	@Test
	void readsANoticeOfAnotherNewspaperTheSameWay() {
		ObjectNode fields = (ObjectNode) AwsPages.notice(page("notice-ldm.html")).path("fields");

		assertThat(fields.path("Référence").asString()).isEqualTo("2026-084-REHAB-TVX-AYGUESVIVES");
		assertThat(fields.path("Lieu d'exécution").asString()).contains("31450 Ayguesvives");
		assertThat(fields.path("Code CPV complémentaire").asString()).contains("45454100");
	}

	@Test
	void namesANoticeAfterItsPage() {
		assertThat(AwsPages.noticeId("https://www.marches-publics.info/Annonces/MPI-pub-20262941419.htm"))
			.isEqualTo("MPI-pub-20262941419");
	}

	static Document page(String name) {
		try (InputStream in = AwsPagesTests.class.getResourceAsStream("/aws/" + name)) {
			return Jsoup.parse(in, null, "https://www.marches-publics.info/Annonces/lister");
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	static byte[] bytes(String name) {
		try (InputStream in = AwsPagesTests.class.getResourceAsStream("/aws/" + name)) {
			return in.readAllBytes();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
