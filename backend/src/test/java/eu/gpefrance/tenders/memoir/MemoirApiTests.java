package eu.gpefrance.tenders.memoir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.time.Clock;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import eu.gpefrance.tenders.ClockConfiguration;
import eu.gpefrance.tenders.TestUsers;
import eu.gpefrance.tenders.TestcontainersConfiguration;
import eu.gpefrance.tenders.document.TestFiles;
import eu.gpefrance.tenders.document.TestImages;

/**
 * A memoir written by hand: its outline, its content kept safe and never
 * lost, the data of the company, its images, and the library; the AI is off.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MemoirApiTests {

	private static final Instant NOW = Instant.parse("2026-10-31T08:00:00Z");

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@TestBean
	private Clock clock;

	static Clock clock() {
		return Clock.fixed(NOW, ClockConfiguration.ZONE);
	}

	private long tenderId;

	@BeforeEach
	void cleanDatabase() {
		jdbc.execute("TRUNCATE memoir, memoir_library, company_activity, employee, lot, tender RESTART IDENTITY CASCADE");
		jdbc.update("UPDATE company_profile SET legal_name = NULL, trade_name = NULL, city = NULL");
		tenderId = jdbc.queryForObject("""
				INSERT INTO tender (source, external_id, title, status, created_at, updated_at)
				VALUES ('BOAMP', '26-1', 'Éclairage public de la commune', 'ACTIVE', now(), now()) RETURNING id""",
				Long.class);
		jdbc.update("INSERT INTO lot (tender_id, position, number, title) VALUES (?, 1, '2', 'Électricité')", tenderId);
	}

	@Test
	void buildsAndValidatesTheOutline() {
		assertThat(post("/api/memoirs", "{\"tenderId\": " + tenderId + ", \"lotNumber\": \"2\"}"))
			.hasStatus(HttpStatus.CREATED)
			.bodyJson()
			.isLenientlyEqualTo("""
					{"id": 1, "title": "Mémoire technique – Éclairage public de la commune", "lotNumber": "2",
					 "status": "DRAFT", "tenderTitle": "Éclairage public de la commune", "createdBy": "Marie Durand"}""");
		assertThat(post("/api/memoirs", "{\"tenderId\": " + tenderId + ", \"lotNumber\": \"9\"}"))
			.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(post("/api/memoirs", "{}")).hasStatus(HttpStatus.BAD_REQUEST);

		assertThat(post("/api/memoirs/1/outline/template", "")).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{"memoir": {"status": "OUTLINE_TO_VALIDATE", "sections": 8}}""");
		assertThat(mvc.get().uri("/api/memoirs/1").with(TestUsers.user())).bodyJson()
			.extractingPath("$.sections[0].title")
			.isEqualTo("Présentation de l'entreprise");
		assertThat(post("/api/memoirs/1/outline/template", "")).hasStatus(HttpStatus.CONFLICT);

		// A sub-part, renamed, then moved under part 3.
		post("/api/memoirs/1/sections", "{\"parentId\": 1, \"title\": \"Certifications\"}").exchange();
		assertThat(put("/api/memoir-sections/9", "{\"title\": \"Certifications et qualifications\", "
				+ "\"description\": \"Qualifelec, RGE\"}")).hasStatusOk()
			.bodyJson()
			.extractingPath("$.sections[1]")
			.asMap()
			.containsEntry("id", 9)
			.containsEntry("number", "1.1")
			.containsEntry("level", 2)
			.containsEntry("title", "Certifications et qualifications");
		String order = "{\"sections\": [" + java.util.stream.IntStream.rangeClosed(1, 8)
			.mapToObj(id -> "{\"id\": " + id + ", \"parentId\": null, \"position\": " + id + "}")
			.collect(java.util.stream.Collectors.joining(", ")) + ", {\"id\": 9, \"parentId\": 3, \"position\": 1}]}";
		assertThat(put("/api/memoirs/1/outline", order)).hasStatusOk()
			.bodyJson()
			.extractingPath("$.sections[*].number")
			.asArray()
			.containsExactly("1", "2", "3", "3.1", "4", "5", "6", "7", "8");
		assertThat(put("/api/memoirs/1/outline", order.replace("\"id\": 9, \"parentId\": 3", "\"id\": 9, \"parentId\": 9")))
			.hasStatus(HttpStatus.BAD_REQUEST);

		assertThat(post("/api/memoirs/1/outline/validation", "")).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("{\"memoir\": {\"status\": \"READY_TO_WRITE\", \"outlineValidatedBy\": \"Marie Durand\"}}");
		// Changed after its validation: to be validated again.
		assertThat(mvc.delete().uri("/api/memoir-sections/9").with(csrf()).with(TestUsers.user())).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("{\"memoir\": {\"status\": \"OUTLINE_TO_VALIDATE\", \"outlineValidatedAt\": null}}");

		assertThat(mvc.get().uri("/api/memoirs").with(TestUsers.admin())).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("[{\"id\": 1, \"sections\": 8, \"written\": 0}]");
	}

	@Test
	void keepsEveryContentWrittenAndCleansIt() {
		post("/api/memoirs", "{\"title\": \"Mémoire type\"}").exchange();
		post("/api/memoirs/1/sections", "{\"title\": \"Présentation\"}").exchange();

		assertThat(put("/api/memoir-sections/1/content", """
				{"html": "<h3>GPE</h3><p onclick=\\"x()\\">Entreprise <strong>électrique</strong></p><script>alert(1)</script><img src=\\"https://evil.test/a.png\\"><figure class=\\"placeholder\\" data-kind=\\"photo\\"><figcaption>Photo du chantier</figcaption></figure>"}"""))
			.hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("{\"contentOrigin\": \"MANUAL\"}");
		String content = jdbc.queryForObject("SELECT content FROM memoir_section WHERE id = 1", String.class);
		assertThat(content).contains("<h3>GPE</h3>", "<strong>électrique</strong>", "class=\"placeholder\"",
				"Photo du chantier").doesNotContain("script", "onclick", "evil.test");

		// The data of the company never replaces a text: it waits as a proposal.
		assertThat(post("/api/memoir-sections/1/company/PRESENTATION", "")).hasStatus(HttpStatus.BAD_REQUEST);
		jdbc.update("UPDATE company_profile SET legal_name = 'GPE ENERGIES ET SERVICES', legal_form = 'SAS', city = 'Vitrolles'");
		jdbc.update("""
				INSERT INTO company_activity (kind, label, description, updated_at)
				VALUES ('DOMAIN', 'Courants forts', 'Distribution électrique', now())""");
		assertThat(post("/api/memoir-sections/1/company/PRESENTATION", "")).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("{\"contentOrigin\": \"MANUAL\", \"proposalNote\": \"Données de l'entreprise\"}");
		assertThat(jdbc.queryForObject("SELECT proposal FROM memoir_section WHERE id = 1", String.class))
			.contains("GPE ENERGIES ET SERVICES", "Courants forts", "Vitrolles");

		assertThat(post("/api/memoir-sections/1/proposal/acceptance", "{\"append\": false}")).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("{\"contentOrigin\": \"COMPANY\", \"proposal\": null}");
		assertThat(mvc.get().uri("/api/memoir-sections/1/revisions").with(TestUsers.user())).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("[{\"id\": 1, \"origin\": \"MANUAL\"}]");
		assertThat(post("/api/memoir-sections/1/revisions/1/restoration", "")).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("{\"proposalNote\": \"Version précédente\"}");

		// The AI is off: nothing is sent.
		assertThat(post("/api/memoir-sections/1/ai", "{\"action\": \"IMPROVE\"}")).hasStatus(HttpStatus.CONFLICT);
		assertThat(post("/api/memoirs/1/analysis", "")).hasStatus(HttpStatus.CONFLICT);
		assertThat(mvc.get().uri("/api/memoirs/1").with(TestUsers.user())).bodyJson()
			.isLenientlyEqualTo("{\"ai\": {\"available\": false}, \"job\": null}");
	}

	@Test
	void keepsImagesAndFormerMemoirs() {
		post("/api/memoirs", "{\"title\": \"Mémoire type\"}").exchange();

		assertThat(upload("/api/memoirs/1/files", "chantier.png", TestImages.write(TestImages.marked(30, 20), "png")))
			.hasStatus(HttpStatus.CREATED)
			.bodyJson()
			.isLenientlyEqualTo("{\"id\": 1, \"url\": \"/api/memoirs/files/1\"}");
		assertThat(upload("/api/memoirs/1/files", "plan.pdf", TestFiles.pdf("Plan"))).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(mvc.get().uri("/api/memoirs/files/1").with(TestUsers.user())).hasStatusOk()
			.hasHeader("Content-Type", "image/png");

		assertThat(mvc.post().uri("/api/memoir-library").multipart()
			.file(new MockMultipartFile("file", "memoire-eclairage-2025.pdf", "application/pdf",
					TestFiles.pdf("Mémoire technique GPE : éclairage public, méthodologie de dépose et repose.")))
			.param("title", "Éclairage public Marignane")
			.param("writtenOn", "2025-03-01")
			.param("activityCode", "PUBLIC_LIGHTING")
			.with(csrf())
			.with(TestUsers.user())).hasStatus(HttpStatus.CREATED)
			.bodyJson()
			.isLenientlyEqualTo("{\"id\": 1, \"title\": \"Éclairage public Marignane\", \"useForAi\": true, "
					+ "\"activityCode\": \"PUBLIC_LIGHTING\"}");
		assertThat(jdbc.queryForObject("SELECT text FROM memoir_library WHERE id = 1", String.class))
			.contains("méthodologie de dépose");
		assertThat(put("/api/memoir-library/1", "{\"title\": \"Éclairage public Marignane\", \"useForAi\": false}"))
			.hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("{\"useForAi\": false}");
		assertThat(mvc.delete().uri("/api/memoir-library/1").with(csrf()).with(TestUsers.user()))
			.hasStatus(HttpStatus.NO_CONTENT);

		assertThat(mvc.delete().uri("/api/memoirs/1").with(csrf()).with(TestUsers.user())).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM memoir_file", Long.class)).isZero();
	}

	@Test
	void exportsTheMemoirAsAWordDocument() throws java.io.IOException {
		post("/api/memoirs", "{\"tenderId\": " + tenderId + "}").exchange();
		post("/api/memoirs/1/outline/template", "").exchange();
		assertThat(put("/api/memoir-sections/1/content", "{\"html\": \"<p>Notre <strong>société</strong></p>\"}")).hasStatusOk();

		MvcTestResult result = mvc.get().uri("/api/memoirs/1/export").with(TestUsers.user()).exchange();

		assertThat(result).hasStatusOk();
		assertThat(result.getResponse().getHeader("Content-Disposition")).contains("attachment").contains(".docx");
		try (var word = new org.apache.poi.xwpf.usermodel.XWPFDocument(
				new java.io.ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
			assertThat(word.getParagraphs()).extracting(org.apache.poi.xwpf.usermodel.XWPFParagraph::getText)
				.contains("1 Présentation de l'entreprise", "Notre société", "[Partie à rédiger]");
		}
		assertThat(mvc.get().uri("/api/memoirs/99/export").with(TestUsers.user())).hasStatus(HttpStatus.NOT_FOUND);
	}

	private MvcTestResult upload(String uri, String name, byte[] content) {
		return mvc.post()
			.uri(uri)
			.multipart()
			.file(new MockMultipartFile("file", name, "application/octet-stream", content))
			.with(csrf())
			.with(TestUsers.user())
			.exchange();
	}

	private MockMvcTester.MockMvcRequestBuilder post(String uri, String body) {
		return mvc.post().uri(uri).with(csrf()).with(TestUsers.user()).contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private MockMvcTester.MockMvcRequestBuilder put(String uri, String body) {
		return mvc.put().uri(uri).with(csrf()).with(TestUsers.user()).contentType(MediaType.APPLICATION_JSON).content(body);
	}

}
