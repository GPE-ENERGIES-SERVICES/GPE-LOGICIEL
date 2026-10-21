package eu.gpefrance.tenders.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
 * The state of the real database on 21/10/2026: last successful BOAMP and
 * TED collections on 12/10/2026 at 16:24 (Paris), from 11/10, then the
 * application stopped until after the 13:00 slot of 21/10. The next scheduled
 * collection, on 22/10 at 7:00, asks every publication day from 11/10 to
 * 22/10, once each.
 */
@SpringBootTest(properties = "collect.disabled-sources=APPROCH,AWS")
@Import(TestcontainersConfiguration.class)
class ResumeAfterOutageTests {

	@Autowired
	private CollectService service;

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoBean
	private BoampClient boampClient;

	@MockitoBean
	private TedClient tedClient;

	@TestBean
	private Clock clock;

	static Clock clock() {
		return Clock.fixed(Instant.parse("2026-10-22T05:00:00Z"), ClockConfiguration.ZONE);
	}

	@BeforeEach
	void realState() {
		jdbc.execute("TRUNCATE collect_run, tender_follow, tender_notice, lot, tender, raw_notice RESTART IDENTITY CASCADE");
		jdbc.update("""
				INSERT INTO collect_run (source, trigger, status, since_date, started_at, finished_at)
				VALUES ('BOAMP', 'MANUAL', 'SUCCESS', DATE '2026-10-11', TIMESTAMPTZ '2026-10-12 14:24:20.621478+00',
				        TIMESTAMPTZ '2026-10-12 14:24:29.475264+00'),
				       ('TED', 'MANUAL', 'SUCCESS', DATE '2026-10-11', TIMESTAMPTZ '2026-10-12 14:24:29.486111+00',
				        TIMESTAMPTZ '2026-10-12 14:24:31.846366+00')""");
		given(boampClient.fetchPublishedOn(any())).willReturn(List.of());
		given(tedClient.fetchPublishedOn(any())).willReturn(List.of());
	}

	@Test
	void theNextScheduledCollectionAsksEveryDaySinceTheDayBeforeTheLastSuccess() {
		List<CollectRun> runs = service.collectActive(CollectTrigger.SCHEDULED);

		List<LocalDate> expected = Stream.iterate(LocalDate.of(2026, 10, 11), day -> day.plusDays(1))
			.limit(12)
			.toList();
		assertThat(runs).extracting(CollectRun::getSource, CollectRun::getSinceDate, CollectRun::getStatus)
			.containsExactly(org.assertj.core.groups.Tuple.tuple("BOAMP", expected.getFirst(), CollectStatus.SUCCESS),
					org.assertj.core.groups.Tuple.tuple("TED", expected.getFirst(), CollectStatus.SUCCESS));
		assertThat(daysAsked(boampClient)).containsExactlyElementsOf(expected);
		assertThat(daysAskedTed()).containsExactlyElementsOf(expected);
	}

	private List<LocalDate> daysAsked(BoampClient client) {
		ArgumentCaptor<LocalDate> days = ArgumentCaptor.forClass(LocalDate.class);
		verify(client, atLeastOnce()).fetchPublishedOn(days.capture());
		return days.getAllValues();
	}

	private List<LocalDate> daysAskedTed() {
		ArgumentCaptor<LocalDate> days = ArgumentCaptor.forClass(LocalDate.class);
		verify(tedClient, atLeastOnce()).fetchPublishedOn(days.capture());
		return days.getAllValues();
	}

}
