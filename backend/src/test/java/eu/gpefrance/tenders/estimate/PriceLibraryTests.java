package eu.gpefrance.tenders.estimate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import eu.gpefrance.tenders.TestUsers;
import eu.gpefrance.tenders.TestcontainersConfiguration;

/**
 * A price schedule GPE sent, kept in the price library and searched; only an
 * administrator fills it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PriceLibraryTests {

	/** As the frame of Istres: the header again on the second page, titles, a subtotal, a line included. */
	static final String DPGF = """
			N°;Désignation;U;Quant.;Quant. Entreprise;Prix U. H.T.;Prix H.T.
			3.1.7;APPAREILLAGE;;;;;
			;- PC 2x10/16A encastrée;U;73;73;28,70;2095,10
			;- Inter VV encastré;U;12;12;20,03;240,36
			;- Accessoires + divers;Incl;1;1;;
			;Sous total  Chapitre 3.1.7;;;;;2335,46
			N°;Désignation;U;Quant.;Quant. Entreprise;Prix U. H.T.;Prix H.T.
			3.1.8;Câblage;;;;;
			;* 3 x 1,5 mm²;ml;3550;3000;2,90;8700,00
			""";

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void cleanDatabase() {
		jdbc.execute("TRUNCATE price_library_import RESTART IDENTITY CASCADE");
	}

	@Test
	void keepsThePricesOfAScheduleAndFindsThemByTheirWords() {
		assertThat(upload(TestUsers.user())).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(upload(TestUsers.admin())).hasStatus(HttpStatus.CREATED)
			.bodyJson()
			.isLenientlyEqualTo("{\"label\": \"Chambre funéraire Istres\", \"priceYear\": 2020, \"itemCount\": 3}");

		assertThat(mvc.get().uri("/api/price-library?q=pc encastree").with(TestUsers.user())).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					[{"designation": "- PC 2x10/16A encastrée", "unit": "U", "unitPrice": 28.7000,
					  "chapter": "3.1.7 APPAREILLAGE", "label": "Chambre funéraire Istres", "priceYear": 2020}]""");
		assertThat(mvc.get().uri("/api/price-library?q=1,5&unit=ML").with(TestUsers.user())).bodyJson()
			.extractingPath("$[0].unitPrice")
			.isEqualTo(2.9);

		assertThat(mvc.delete().uri("/api/price-library/imports/1").with(csrf()).with(TestUsers.admin()))
			.hasStatus(HttpStatus.NO_CONTENT);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM price_library_item", Long.class)).isZero();
	}

	private MvcTestResult upload(RequestPostProcessor user) {
		return mvc.post()
			.uri("/api/price-library/imports?label=Chambre funéraire Istres&priceYear=2020&domain=ELECTRICITY")
			.multipart()
			.file(new MockMultipartFile("file", "dpgf.csv", "text/csv", DPGF.getBytes(StandardCharsets.UTF_8)))
			.with(csrf())
			.with(user)
			.exchange();
	}

}
