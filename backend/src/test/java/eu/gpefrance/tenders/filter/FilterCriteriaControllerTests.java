package eu.gpefrance.tenders.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import eu.gpefrance.tenders.ApiExceptionHandler;
import eu.gpefrance.tenders.SecurityConfiguration;
import eu.gpefrance.tenders.TestUsers;
import eu.gpefrance.tenders.tender.MarketType;
import eu.gpefrance.tenders.user.AppUserDetailsService;

@WebMvcTest(FilterCriteriaController.class)
@Import({ SecurityConfiguration.class, ApiExceptionHandler.class })
class FilterCriteriaControllerTests {

	private static final String VALID = """
			{"departments": [" 2a ", "2A"], "marketTypes": ["WORKS"], "cpvPrefixes": ["4531"],
			 "keywords": ["borne", "  "], "excludedKeywords": []}
			""";

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private AppUserDetailsService userDetailsService;

	@MockitoBean
	private FilterService filterService;

	@MockitoBean
	private FilterCriteriaRepository criteria;

	@Test
	void replacesTheCriteriaWithCleanedValuesAndFiltersAgain() {
		given(filterService.replaceCriteria(any(), eq("Admin GPE"))).willReturn(new FilterCriteria(List.of("2A"),
				List.of(MarketType.WORKS), null, List.of("4531"), List.of("borne"), List.of(), null, null,
				Instant.parse("2026-10-05T08:00:00Z")));

		assertThat(put(VALID).with(TestUsers.admin()).with(csrf())).hasStatusOk()
			.bodyJson()
			.extractingPath("$.departments")
			.asArray()
			.containsExactly("2A");

		ArgumentCaptor<FilterCriteriaRequest> request = ArgumentCaptor.forClass(FilterCriteriaRequest.class);
		verify(filterService).replaceCriteria(request.capture(), eq("Admin GPE"));
		assertThat(request.getValue().cleanDepartments()).containsExactly("2A");
		assertThat(request.getValue().cleanKeywords()).containsExactly("borne");
		verify(filterService).filterAll();
	}

	@Test
	void rejectsInvalidCriteriaWithReadableMessages() {
		assertThat(put("""
				{"departments": ["Rhône"], "marketTypes": [], "cpvPrefixes": ["45A"], "keywords": [],
				 "excludedKeywords": [], "minAmount": 5000, "maxAmount": 1000}
				""").with(TestUsers.admin()).with(csrf())).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.detail")
			.asString()
			.contains("département invalide", "début de code CPV invalide", "le montant minimum dépasse le maximum");

		verifyNoInteractions(filterService);
	}

	@Test
	void reservesTheChangeToAdministrators() {
		assertThat(put(VALID).with(TestUsers.user()).with(csrf())).hasStatus(HttpStatus.FORBIDDEN);

		verifyNoInteractions(filterService);
	}

	private MockMvcTester.MockMvcRequestBuilder put(String body) {
		return mvc.put().uri("/api/filter-criteria").contentType(MediaType.APPLICATION_JSON).content(body);
	}

}
