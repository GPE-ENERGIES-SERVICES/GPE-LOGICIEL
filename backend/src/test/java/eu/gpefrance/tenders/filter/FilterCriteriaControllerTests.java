package eu.gpefrance.tenders.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import eu.gpefrance.tenders.tender.MarketType;

@WebMvcTest(FilterCriteriaController.class)
class FilterCriteriaControllerTests {

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private FilterService filterService;

	@MockitoBean
	private FilterCriteriaRepository criteria;

	@Test
	void replacesTheCriteriaWithCleanedValuesAndFiltersAgain() {
		given(filterService.replaceCriteria(any(), isNull())).willReturn(new FilterCriteria(List.of("2A"),
				List.of(MarketType.WORKS), null, List.of("4531"), List.of("borne"), List.of(), null, null,
				Instant.parse("2026-10-05T08:00:00Z")));

		assertThat(mvc.put().uri("/api/filter-criteria").contentType(MediaType.APPLICATION_JSON).content("""
				{"departments": [" 2a ", "2A"], "marketTypes": ["WORKS"], "cpvPrefixes": ["4531"],
				 "keywords": ["borne", "  "], "excludedKeywords": []}
				""")).hasStatusOk().bodyJson().extractingPath("$.departments").asArray().containsExactly("2A");

		ArgumentCaptor<FilterCriteriaRequest> request = ArgumentCaptor.forClass(FilterCriteriaRequest.class);
		verify(filterService).replaceCriteria(request.capture(), isNull());
		assertThat(request.getValue().cleanDepartments()).containsExactly("2A");
		assertThat(request.getValue().cleanKeywords()).containsExactly("borne");
		verify(filterService).filterAll();
	}

	@Test
	void rejectsInvalidCriteria() {
		assertThat(mvc.put().uri("/api/filter-criteria").contentType(MediaType.APPLICATION_JSON).content("""
				{"departments": ["Rhône"], "marketTypes": [], "cpvPrefixes": ["45A"], "keywords": [],
				 "excludedKeywords": [], "minAmount": 5000, "maxAmount": 1000}
				""")).hasStatus(HttpStatus.BAD_REQUEST);

		verifyNoInteractions(filterService);
	}

}
