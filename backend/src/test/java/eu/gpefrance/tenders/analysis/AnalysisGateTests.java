package eu.gpefrance.tenders.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import eu.gpefrance.tenders.ClockConfiguration;
import eu.gpefrance.tenders.analysis.AnalysisGate.State;
import eu.gpefrance.tenders.analysis.AnalysisRepository.TokenTotals;

/**
 * Every condition before a document may leave the server, checked in order,
 * and the validation of the settings at start.
 */
class AnalysisGateTests {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-23T10:00:00Z"), ClockConfiguration.ZONE);

	private final ExtractionClient client = client("test", "test-model");

	@Test
	void isDisabledByDefaultAndNeverCallsTheProvider() {
		AnalysisGate gate = gate(properties(false, "test", true, null, 0), List.of(client), 0);

		assertThat(gate.status().state()).isEqualTo(State.DISABLED);
		assertThat(gate.readyClient()).isEmpty();
		verify(client, never()).complete(any());
	}

	@Test
	void refusesAnInconsistentConfiguration() {
		assertThat(gate(properties(true, "none", true, null, 0), List.of(client), 0).status())
			.satisfies(status -> assertThat(status.state()).isEqualTo(State.MISCONFIGURED))
			.satisfies(status -> assertThat(status.problems()).containsExactly("analysis.provider n'est pas choisi"));
		assertThat(gate(properties(true, "mistral", true, null, 0), List.of(client), 0).status().problems())
			.containsExactly("aucun fournisseur « mistral » dans l'application");
		assertThat(gate(properties(true, "test", true, BigDecimal.TEN, 0), List.of(client), 0).status().problems())
			.singleElement()
			.asString()
			.contains("analysis.monthly-budget exige les prix");
		assertThat(gate(properties(true, "test", true, null, 0), List.of(client, client("test", "other")), 0)
			.status()
			.state()).isEqualTo(State.MISCONFIGURED);
	}

	@Test
	void staysOffForAProviderWithoutKey() {
		AnalysisGate gate = gate(properties(true, "test", true, null, 0), List.of(client("test", null)), 0);

		assertThat(gate.status().state()).isEqualTo(State.NOT_CONFIGURED);
		assertThat(gate.readyClient()).isEmpty();
	}

	@Test
	void neverSendsOnTheKeyAlone() {
		AnalysisGate gate = gate(properties(true, "test", false, null, 0), List.of(client), 0);

		assertThat(gate.status().state()).isEqualTo(State.TRANSMISSION_NOT_ALLOWED);
		assertThat(gate.readyClient()).isEmpty();
	}

	@Test
	void choosesTheProviderOfTheConfiguration() {
		ExtractionClient other = client("other", "other-model");
		AnalysisGate gate = gate(properties(true, "TEST", true, null, 0), List.of(other, client), 0);

		assertThat(gate.status().state()).isEqualTo(State.READY);
		assertThat(gate.readyClient()).containsSame(client);
		assertThat(gate.availableProviders()).containsExactly("other", "test");
	}

	@Test
	void stopsAtTheMonthlyCeiling() {
		// 3 € per million input tokens, ceiling 3 €.
		BigDecimal budget = BigDecimal.valueOf(3);
		assertThat(gate(properties(true, "test", true, budget, 3), List.of(client), 1_000_000).status().state())
			.isEqualTo(State.BUDGET_REACHED);

		AnalysisGate gate = gate(properties(true, "test", true, budget, 3), List.of(client), 0);
		assertThat(gate.status().state()).isEqualTo(State.READY);
		assertThat(gate.fitsBudget(30_000)).isTrue();
		// 3 million characters: 1 million tokens, 3 €: exactly what is left.
		assertThat(gate.fitsBudget(3_000_000)).isTrue();
		assertThat(gate.fitsBudget(6_000_000)).isFalse();
	}

	@Test
	void validatesTheSettingsAtStart() {
		assertThat(properties(false, " ", false, null, 0).provider()).isEqualTo("none");
		assertThatIllegalArgumentException().isThrownBy(() -> new AnalysisProperties(true, "test", true, 400000, 10,
				8000, 0, 3, Duration.ofMinutes(2), Duration.ofHours(1), Duration.ofMinutes(5), Duration.ofMinutes(15),
				Duration.ofSeconds(30), null, BigDecimal.ZERO, BigDecimal.ZERO));
		assertThatIllegalArgumentException().isThrownBy(() -> new AnalysisProperties(true, "test", true, 400000, 10,
				8000, 1, 3, Duration.ofMinutes(2), Duration.ofHours(1), Duration.ofMinutes(20), Duration.ofMinutes(15),
				Duration.ofSeconds(30), null, BigDecimal.ZERO, BigDecimal.ZERO))
			.withMessageContaining("lease");
		assertThatIllegalArgumentException().isThrownBy(() -> properties(true, "test", true, BigDecimal.ONE.negate(), 0));
	}

	@Test
	void waitsLongerAfterEachTemporaryErrorWithJitter() {
		AnalysisWorker worker = new AnalysisWorker(null, null, null, null, null, null, null,
				properties(true, "test", true, null, 0), null, null, CLOCK);
		try {
			assertThat(worker.delayAfter(1, 0)).isEqualTo(Duration.ofSeconds(90));
			assertThat(worker.delayAfter(1, 1)).isEqualTo(Duration.ofSeconds(150));
			assertThat(worker.delayAfter(2, 0.5)).isEqualTo(Duration.ofMinutes(4));
			assertThat(worker.delayAfter(20, 1)).isEqualTo(Duration.ofHours(1));
		}
		finally {
			worker.stop();
		}
	}

	static AnalysisProperties properties(boolean enabled, String provider, boolean transmission, BigDecimal budget,
			int inputPrice) {
		return new AnalysisProperties(enabled, provider, transmission, 400000, 10, 8000, 1, 3, Duration.ofMinutes(2),
				Duration.ofHours(1), Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofSeconds(30), budget,
				BigDecimal.valueOf(inputPrice), BigDecimal.ZERO);
	}

	@SuppressWarnings("unchecked")
	private static AnalysisGate gate(AnalysisProperties properties, List<ExtractionClient> clients, long inputTokens) {
		ObjectProvider<ExtractionClient> provider = mock(ObjectProvider.class);
		given(provider.orderedStream()).willAnswer(invocation -> clients.stream());
		AnalysisRepository analyses = mock(AnalysisRepository.class);
		given(analyses.sumTokensSince(any())).willReturn(new TokenTotals() {

			@Override
			public long getInput() {
				return inputTokens;
			}

			@Override
			public long getOutput() {
				return 0;
			}

		});
		return new AnalysisGate(provider, properties, analyses, CLOCK);
	}

	private static ExtractionClient client(String provider, String model) {
		ExtractionClient client = mock(ExtractionClient.class);
		given(client.provider()).willReturn(provider);
		given(client.model()).willReturn(model);
		return client;
	}

}
