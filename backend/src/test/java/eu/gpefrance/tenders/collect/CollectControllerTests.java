package eu.gpefrance.tenders.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import eu.gpefrance.tenders.SecurityConfiguration;
import eu.gpefrance.tenders.TestUsers;
import eu.gpefrance.tenders.user.AppUserDetailsService;

@WebMvcTest(CollectController.class)
@Import(SecurityConfiguration.class)
class CollectControllerTests {

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private AppUserDetailsService userDetailsService;

	@MockitoBean
	private CollectService collectService;

	@MockitoBean
	private CollectRunRepository runs;

	@Test
	void returnsTheJournalWithTheRunningFlag() {
		CollectRun run = new CollectRun("BOAMP", CollectTrigger.SCHEDULED, LocalDate.of(2026, 10, 5),
				Instant.parse("2026-10-05T05:00:00Z"));
		run.fail(12, 3, "IllegalStateException: API down", Instant.parse("2026-10-05T05:02:00Z"));
		given(runs.findAllByOrderByStartedAtDesc(any(Pageable.class))).willReturn(List.of(run));
		given(collectService.isRunning()).willReturn(true);

		assertThat(mvc.get().uri("/api/collect-runs").with(TestUsers.user()))
			.hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{"running": true, "runs": [{"status": "FAILED", "trigger": "SCHEDULED",
					"sinceDate": "2026-10-05", "noticesReceived": 12, "noticesNew": 3,
					"errorMessage": "IllegalStateException: API down"}]}
					""");
	}

	@Test
	void requiresAnAuthenticatedUser() {
		assertThat(mvc.get().uri("/api/collect-runs")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void letsAnAdministratorStartAManualCollection() {
		assertThat(mvc.post().uri("/api/collect-runs").with(TestUsers.admin()).with(csrf()))
			.hasStatus(HttpStatus.ACCEPTED);

		verify(collectService).startInBackground(CollectTrigger.MANUAL);
	}

	@Test
	void refusesAManualCollectionToAUser() {
		assertThat(mvc.post().uri("/api/collect-runs").with(TestUsers.user()).with(csrf()))
			.hasStatus(HttpStatus.FORBIDDEN);

		verifyNoInteractions(collectService);
	}

	@Test
	void refusesARequestWithoutCsrfToken() {
		assertThat(mvc.post().uri("/api/collect-runs").with(TestUsers.admin())).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void refusesASecondCollectionWhileOneIsRunning() {
		willThrow(new CollectAlreadyRunningException()).given(collectService)
			.startInBackground(CollectTrigger.MANUAL);

		assertThat(mvc.post().uri("/api/collect-runs").with(TestUsers.admin()).with(csrf()))
			.hasStatus(HttpStatus.CONFLICT)
			.bodyJson()
			.extractingPath("$.detail")
			.isEqualTo("Une collecte est déjà en cours");
	}

}
