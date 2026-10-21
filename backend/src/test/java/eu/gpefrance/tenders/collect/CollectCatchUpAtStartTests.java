package eu.gpefrance.tenders.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import eu.gpefrance.tenders.ClockConfiguration;
import eu.gpefrance.tenders.TestcontainersConfiguration;
import eu.gpefrance.tenders.collect.boamp.BoampClient;
import eu.gpefrance.tenders.collect.ted.TedClient;

/**
 * Started after the 7:00 collection of a Tuesday that it missed, the
 * application catches up on its own, outside the thread of the scheduler.
 */
@SpringBootTest(properties = { "collect.disabled-sources=APPROCH,AWS", "collect.cron=0 0 7,13 * * MON-FRI",
		"collect.catch-up.enabled=true", "collect.catch-up.delay=0s" })
@Import(TestcontainersConfiguration.class)
class CollectCatchUpAtStartTests {

	private static final Set<String> THREADS = ConcurrentHashMap.newKeySet();

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private CollectCatchUp catchUp;

	@TestBean
	private BoampClient boampClient;

	@MockitoBean
	private TedClient tedClient;

	@TestBean
	private Clock clock;

	/** Records the threads it is called from, from the start of the application. */
	static BoampClient boampClient() {
		return org.mockito.Mockito.mock(BoampClient.class, invocation -> {
			THREADS.add(Thread.currentThread().getName());
			return List.of();
		});
	}

	static Clock clock() {
		return Clock.fixed(Instant.parse("2027-01-05T08:00:00Z"), ClockConfiguration.ZONE);
	}

	@Test
	void catchesUpEverySourceInTheBackgroundAfterTheStart() {
		given(tedClient.fetchPublishedOn(any())).willReturn(List.of());

		long end = System.nanoTime() + Duration.ofSeconds(60).toNanos();
		while (System.nanoTime() < end && (catchUp.isRunning() || caughtUp("TED") == 0)) {
			Thread.onSpinWait();
		}

		assertThat(caughtUp("BOAMP")).isPositive();
		assertThat(caughtUp("TED")).isPositive();
		assertThat(THREADS).isNotEmpty().noneMatch(name -> name.startsWith("scheduling"));
	}

	private int caughtUp(String source) {
		return jdbc.queryForObject(
				"SELECT count(*) FROM collect_run WHERE source = ? AND trigger = 'CATCH_UP' AND started_at >= ?",
				Integer.class, source, java.sql.Timestamp.from(Instant.parse("2027-01-05T08:00:00Z")));
	}

}
