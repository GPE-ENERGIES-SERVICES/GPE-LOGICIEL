package eu.gpefrance.tenders.collect.approch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.twice;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import eu.gpefrance.tenders.collect.CollectProperties;
import eu.gpefrance.tenders.collect.SourceRecord;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class ApprochClientTests {

	private static final String EXPORT = "https://approch.test/exports/json";

	private static final String COMPANIES = "https://companies.test/search";

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);

	private static final String INTERIOR = """
			{"results": [{"siren": "110014016", "nom_complet": "MINISTERE DE L'INTERIEUR"}]}""";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private MockRestServiceServer server;

	private ApprochClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		client = new ApprochClient(builder, new ApprochProperties("https://approch.test", "https://companies.test"),
				new CollectProperties(7, Set.of(), Duration.ZERO));
	}

	@Test
	void readsEveryProjectWithTheNameOfItsBuyerAskedOnce() {
		server.expect(requestTo(Matchers.startsWith(EXPORT)))
			.andExpect(method(HttpMethod.GET))
			.andExpect(queryParam("select", String.join(",", ApprochClient.FIELDS)))
			.andRespond(withSuccess(ApprochNoticeParserTests.export().toString(), MediaType.APPLICATION_JSON));
		server.expect(once(), requestTo(Matchers.startsWith(COMPANIES)))
			.andExpect(queryParam("q", "110014016"))
			.andRespond(withSuccess(INTERIOR, MediaType.APPLICATION_JSON));

		List<SourceRecord> records = client.fetchCurrent(TODAY);

		assertThat(records).hasSize(2);
		assertThat(records).extracting(SourceRecord::externalId)
			.allSatisfy(id -> assertThat(id).matches("^\\d+-[0-9a-f]{12}$"));
		assertThat(records.getFirst().externalId()).startsWith("66108-");
		assertThat(records.getFirst().publishedAt()).isEqualTo(TODAY);
		assertThat(JSON.readTree(records.getFirst().payload()).path("buyerName").asString())
			.isEqualTo("MINISTERE DE L'INTERIEUR");
		assertThat(JSON.readTree(records.getFirst().payload()).path("collectedOn").asString())
			.isEqualTo("2026-10-21");
		server.verify();
	}

	@Test
	void keepsTheSameIdentifierForAnUnchangedProjectAndANewOneForAChange() {
		ObjectNode project = ApprochNoticeParserTests.project(0);
		String digest = ApprochClient.digest(project, "MINISTERE DE L'INTERIEUR");

		assertThat(ApprochClient.digest(project.deepCopy(), "MINISTERE DE L'INTERIEUR")).isEqualTo(digest);
		ObjectNode changed = project.deepCopy();
		changed.put("date_cible_de_remise_des_offres", "2026-12-15");
		assertThat(ApprochClient.digest(changed, "MINISTERE DE L'INTERIEUR")).isNotEqualTo(digest);
		assertThat(ApprochClient.digest(project, null)).isNotEqualTo(digest);
		ObjectNode unread = project.deepCopy();
		unread.put("nomenclature_hospitalier", "added later");
		assertThat(ApprochClient.digest(unread, "MINISTERE DE L'INTERIEUR")).isEqualTo(digest);
	}

	@Test
	void leavesTheNameOutForAnUnknownSiren() {
		server.expect(requestTo(Matchers.startsWith(EXPORT)))
			.andRespond(withSuccess(exportOf(ApprochNoticeParserTests.project(0)), MediaType.APPLICATION_JSON));
		server.expect(requestTo(Matchers.startsWith(COMPANIES)))
			.andRespond(withSuccess("""
					{"results": [{"siren": "999999999", "nom_complet": "AUTRE"}]}""", MediaType.APPLICATION_JSON));

		SourceRecord record = client.fetchCurrent(TODAY).getFirst();

		assertThat(JSON.readTree(record.payload()).path("buyerName").isNull()).isTrue();
	}

	@Test
	void returnsAProjectWithoutCodeAsInvalid() {
		ObjectNode project = ApprochNoticeParserTests.project(0);
		project.remove("code");
		project.remove("siren_de_l_entite_acheteuse");
		server.expect(requestTo(Matchers.startsWith(EXPORT)))
			.andRespond(withSuccess(exportOf(project), MediaType.APPLICATION_JSON));

		assertThat(client.fetchCurrent(TODAY)).extracting(SourceRecord::isValid).containsExactly(false);
	}

	@Test
	void triesTheExportAgainOnceAfterAServerError() {
		server.expect(once(), requestTo(Matchers.startsWith(EXPORT))).andRespond(withServerError());
		server.expect(once(), requestTo(Matchers.startsWith(EXPORT)))
			.andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

		assertThat(client.fetchCurrent(TODAY)).isEmpty();
		server.verify();
	}

	@Test
	void failsWhenTheServerErrorLasts() {
		server.expect(twice(), requestTo(Matchers.startsWith(EXPORT))).andRespond(withServerError());

		assertThatThrownBy(() -> client.fetchCurrent(TODAY)).isInstanceOf(HttpServerErrorException.class);
	}

	@Test
	void failsWhenTheNameOfABuyerCannotBeRead() {
		server.expect(requestTo(Matchers.startsWith(EXPORT)))
			.andRespond(withSuccess(exportOf(ApprochNoticeParserTests.project(0)), MediaType.APPLICATION_JSON));
		server.expect(twice(), requestTo(Matchers.startsWith(COMPANIES))).andRespond(withServerError());

		assertThatThrownBy(() -> client.fetchCurrent(TODAY)).isInstanceOf(HttpServerErrorException.class);
	}

	@Test
	void refusesAResponseThatIsNotAList() {
		server.expect(requestTo(Matchers.startsWith(EXPORT)))
			.andRespond(withSuccess("""
					{"error_code": "ODSQLError"}""", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> client.fetchCurrent(TODAY)).isInstanceOf(IllegalStateException.class);
	}

	private static String exportOf(ObjectNode... projects) {
		ArrayNode export = JSON.createArrayNode();
		for (ObjectNode project : projects) {
			export.add(project);
		}
		return export.toString();
	}

}
