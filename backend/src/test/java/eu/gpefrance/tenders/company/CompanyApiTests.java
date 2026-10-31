package eu.gpefrance.tenders.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.nio.file.Files;
import java.nio.file.Path;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import eu.gpefrance.tenders.ClockConfiguration;
import eu.gpefrance.tenders.TestUsers;
import eu.gpefrance.tenders.TestcontainersConfiguration;
import eu.gpefrance.tenders.document.TestFiles;
import eu.gpefrance.tenders.document.TestImages;

/**
 * The company through the API: its identity, its lists, its files and what
 * expires; read by every user, changed by an administrator only.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CompanyApiTests {

	/** 31/10/2026: the warning days (60) reach 30/12/2026. */
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

	@BeforeEach
	void cleanDatabase() {
		jdbc.execute("TRUNCATE company_file, company_contact, company_activity, employee_credential, employee, equipment, "
				+ "company_certification, company_reference, company_document, company_economic_parameter "
				+ "RESTART IDENTITY CASCADE");
		jdbc.update("DELETE FROM company_profile");
		jdbc.update("INSERT INTO company_profile (id) VALUES (1)");
	}

	@Test
	void theIdentityIsChangedByAnAdministratorAndReadByEveryone() {
		String identity = """
				{"legalName": "GPE Énergies & Services", "siren": "123456789", "siret": "12345678900012",
				 "vatNumber": "FR12123456789", "apeCode": "43.21A", "city": "Marseille",
				 "interventionDepartments": ["13", "83", "2A"]}""";

		assertThat(put("/api/company/profile", identity, TestUsers.user())).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(put("/api/company/profile", identity, TestUsers.admin())).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{"legalName": "GPE Énergies & Services", "siret": "12345678900012",
					 "interventionDepartments": ["13", "83", "2A"], "updatedBy": "Admin GPE", "logo": null}""");
		assertThat(mvc.get().uri("/api/company/profile").with(TestUsers.user())).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("{\"siren\": \"123456789\"}");

		assertThat(put("/api/company/profile", identity.replace("12345678900012", "99945678900012"), TestUsers.admin()))
			.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(put("/api/company/profile", identity.replace("123456789\"", "12345\""), TestUsers.admin()))
			.hasStatus(HttpStatus.BAD_REQUEST);

		// A logo is an image.
		assertThat(upload("/api/company/profile/logo", "logo.pdf", TestFiles.pdf("Logo"))).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(upload("/api/company/profile/logo", "logo.png",
				TestImages.write(TestImages.marked(40, 20), "png"))).hasStatus(HttpStatus.CREATED);
		assertThat(mvc.get().uri("/api/company/profile").with(TestUsers.user())).bodyJson()
			.isLenientlyEqualTo("{\"logo\": {\"fileName\": \"logo.png\"}}");
	}

	@Test
	void followsTheHabilitationsOfTheStaffAndTheirEnd() {
		assertThat(post("/api/company/employees", "{\"firstName\": \"Karim\", \"lastName\": \"Benali\", "
				+ "\"jobTitle\": \"Chef d'équipe\"}", TestUsers.admin())).hasStatus(HttpStatus.CREATED)
			.bodyJson()
			.isLenientlyEqualTo("{\"id\": 1, \"firstName\": \"Karim\", \"active\": true, \"validity\": null}");
		post("/api/company/credentials", credential("B2V", "2026-12-01"), TestUsers.admin()).exchange();
		post("/api/company/credentials", credential("H0B0", "2026-10-01"), TestUsers.admin()).exchange();
		post("/api/company/credentials", credential("CACES R486", "2029-05-01"), TestUsers.admin()).exchange();

		assertThat(mvc.get().uri("/api/company/credentials").with(TestUsers.user())).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					[{"label": "H0B0", "validity": "EXPIRED", "files": []},
					 {"label": "B2V", "validity": "EXPIRING"},
					 {"label": "CACES R486", "validity": "VALID"}]""");
		assertThat(mvc.get().uri("/api/company/overview").with(TestUsers.user())).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{"warningDays": 60, "counts": {"employees": 1},
					 "expiries": [{"kind": "CREDENTIAL", "title": "H0B0", "detail": "Karim Benali", "validity": "EXPIRED"},
					              {"kind": "CREDENTIAL", "title": "B2V", "validity": "EXPIRING"}]}""");

		assertThat(post("/api/company/credentials", credential("BR", "2027-01-01").replace("\"employeeId\": 1",
				"\"employeeId\": 99"), TestUsers.admin())).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(post("/api/company/credentials", credential("BR", "2020-01-01"), TestUsers.admin()))
			.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(post("/api/company/employees", "{\"firstName\": \"X\"}", TestUsers.admin()))
			.hasStatus(HttpStatus.BAD_REQUEST);

		assertThat(mvc.delete().uri("/api/company/employees/1").with(csrf()).with(TestUsers.admin()))
			.hasStatus(HttpStatus.NO_CONTENT);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM employee_credential", Long.class)).isZero();
	}

	@Test
	void keepsTheOfficialDocumentsWithTheirFilesAndHistory() throws Exception {
		assertThat(post("/api/company/documents", """
				{"category": "VIGILANCE", "name": "Attestation de vigilance URSSAF", "issuedOn": "2026-08-01",
				 "expiresOn": "2026-11-30"}""", TestUsers.user())).hasStatus(HttpStatus.FORBIDDEN);
		post("/api/company/documents", """
				{"category": "VIGILANCE", "name": "Attestation de vigilance URSSAF", "issuedOn": "2026-08-01",
				 "expiresOn": "2026-11-30"}""", TestUsers.admin()).exchange();

		assertThat(upload("/api/company/documents/1/files", "vigilance.pdf", TestFiles.pdf("Attestation 1")))
			.hasStatus(HttpStatus.CREATED)
			.bodyJson()
			.isLenientlyEqualTo("{\"id\": 1, \"fileName\": \"vigilance.pdf\", \"uploadedBy\": \"Admin GPE\"}");
		assertThat(mvc.get().uri("/api/company/overview").with(TestUsers.user())).bodyJson()
			.isLenientlyEqualTo("{\"documentsWithoutFile\": 0, \"expiries\": [{\"kind\": \"DOCUMENT\", "
					+ "\"detail\": \"VIGILANCE\", \"validity\": \"EXPIRING\"}]}");

		// Replaced: the new file becomes the current one, the former stays as history.
		upload("/api/company/documents/1/files", "vigilance 2027.pdf", TestFiles.pdf("Attestation 2"));
		put("/api/company/documents/1", """
				{"category": "VIGILANCE", "name": "Attestation de vigilance URSSAF", "issuedOn": "2026-10-30",
				 "expiresOn": "2027-04-30"}""", TestUsers.admin()).exchange();
		assertThat(mvc.get().uri("/api/company/documents").with(TestUsers.user())).bodyJson()
			.isLenientlyEqualTo("""
					[{"name": "Attestation de vigilance URSSAF", "validity": "VALID",
					  "files": [{"id": 2, "fileName": "vigilance 2027.pdf"}, {"id": 1, "fileName": "vigilance.pdf"}]}]""");
		assertThat(mvc.get().uri("/api/company/files/2").with(TestUsers.user())).hasStatusOk()
			.hasHeader("Content-Type", "application/pdf");

		// No archive, nothing but what the content is.
		assertThat(upload("/api/company/documents/1/files", "pieces.zip", zip())).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(upload("/api/company/documents/1/files", "faux.pdf", "<html>connexion</html>".getBytes()))
			.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(upload("/api/company/documents/9/files", "a.pdf", TestFiles.pdf("x"))).hasStatus(HttpStatus.NOT_FOUND);

		Path stored = Path.of("target/test-storage")
			.resolve(jdbc.queryForObject("SELECT storage_path FROM company_file WHERE id = 2", String.class));
		assertThat(stored).exists();
		assertThat(mvc.delete().uri("/api/company/documents/1").with(csrf()).with(TestUsers.admin()))
			.hasStatus(HttpStatus.NO_CONTENT);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM company_file", Long.class)).isZero();
		assertThat(Files.exists(stored)).isFalse();
	}

	@Test
	void theActivitiesUseTheTypesOfWorksOfTheTenders() {
		assertThat(post("/api/company/activities", """
				{"kind": "DOMAIN", "label": "Électricité courants forts", "activityCode": "ELECTRICITY",
				 "cpvPrefixes": ["4531", "4531"]}""", TestUsers.admin())).hasStatus(HttpStatus.CREATED)
			.bodyJson()
			.isLenientlyEqualTo("{\"activityCode\": \"ELECTRICITY\", \"cpvPrefixes\": [\"4531\"]}");
		assertThat(post("/api/company/activities", "{\"kind\": \"DOMAIN\", \"label\": \"x\", \"activityCode\": \"COOKING\"}",
				TestUsers.admin())).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(post("/api/company/economic-parameters", "{\"label\": \"Frais généraux\", \"value\": 0.12, "
				+ "\"unit\": \"part du déboursé\"}", TestUsers.admin())).hasStatus(HttpStatus.CREATED);
		assertThat(mvc.get().uri("/api/company/economic-parameters").with(TestUsers.user())).bodyJson()
			.isLenientlyEqualTo("[{\"label\": \"Frais généraux\", \"value\": 0.1200}]");
		assertThat(mvc.get().uri("/api/company/activities")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	private static String credential(String label, String expiresOn) {
		return "{\"employeeId\": 1, \"kind\": \"HABILITATION\", \"label\": \"%s\", \"obtainedOn\": \"2024-01-01\", "
				.formatted(label) + "\"expiresOn\": \"%s\"}".formatted(expiresOn);
	}

	private static byte[] zip() throws Exception {
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(out)) {
			zip.putNextEntry(new java.util.zip.ZipEntry("a.txt"));
			zip.write("a".getBytes());
			zip.closeEntry();
		}
		return out.toByteArray();
	}

	private MvcTestResult upload(String uri, String name, byte[] content) {
		return mvc.post()
			.uri(uri)
			.multipart()
			.file(new MockMultipartFile("file", name, "application/octet-stream", content))
			.with(csrf())
			.with(TestUsers.admin())
			.exchange();
	}

	private MockMvcTester.MockMvcRequestBuilder post(String uri, String body, RequestPostProcessor user) {
		return mvc.post().uri(uri).with(csrf()).with(user).contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private MockMvcTester.MockMvcRequestBuilder put(String uri, String body, RequestPostProcessor user) {
		return mvc.put().uri(uri).with(csrf()).with(user).contentType(MediaType.APPLICATION_JSON).content(body);
	}

}
