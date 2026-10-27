package eu.gpefrance.tenders.analysis;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import eu.gpefrance.tenders.ClockConfiguration;
import eu.gpefrance.tenders.analysis.AnalysisRepository.TokenTotals;

/**
 * The only place that decides whether a document may be sent to an AI
 * provider: checked when an analysis is asked and again just before each
 * call. In order: the analysis is enabled, the configuration is consistent,
 * the chosen provider exists and has its key, the transmission of the
 * documents is allowed, the monthly ceiling is not reached. Until all of this
 * holds, no client is ever called: no network, no cost.
 */
@Component
public class AnalysisGate {

	/** Rough number of characters per token, on the safe side, to estimate a call before it is made. */
	static final int CHARACTERS_PER_TOKEN = 3;

	private static final Logger log = LoggerFactory.getLogger(AnalysisGate.class);

	private final List<ExtractionClient> clients;

	private final AnalysisProperties properties;

	private final AnalysisRepository analyses;

	private final Clock clock;

	public AnalysisGate(ObjectProvider<ExtractionClient> clients, AnalysisProperties properties,
			AnalysisRepository analyses, Clock clock) {
		this.clients = clients.orderedStream().toList();
		this.properties = properties;
		this.analyses = analyses;
		this.clock = clock;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void logState() {
		Status status = status();
		if (status.state() == State.DISABLED || status.state() == State.READY) {
			log.info("AI analysis: {} ({})", status.state(), status.message());
		}
		else {
			log.warn("AI analysis: {}, kept off: {} {}", status.state(), status.message(), status.problems());
		}
	}

	/**
	 * Where the AI analysis stands, the budget included.
	 */
	public Status status() {
		Status configuration = configurationStatus();
		if (configuration.state() != State.READY) {
			return configuration;
		}
		if (budgetLeft().map(left -> left.signum() <= 0).orElse(false)) {
			return new Status(State.BUDGET_REACHED, "Le plafond mensuel de dépense est atteint : aucune analyse "
					+ "n'est lancée avant le mois prochain.", List.of(), configuration.client());
		}
		return configuration;
	}

	/**
	 * @return the client, only when everything allows a call
	 */
	public Optional<ExtractionClient> readyClient() {
		Status status = status();
		return status.state() == State.READY ? Optional.of(status.client()) : Optional.empty();
	}

	/**
	 * @return the identifiers of the providers present in the application
	 */
	public List<String> availableProviders() {
		return clients.stream().map(ExtractionClient::provider).sorted().toList();
	}

	/**
	 * Whether an analysis of this size still fits in the monthly ceiling.
	 */
	public boolean fitsBudget(long characters) {
		return budgetLeft().map(left -> {
			BigDecimal estimate = properties.cost(characters / CHARACTERS_PER_TOKEN, properties.maxOutputTokens());
			return estimate == null || estimate.compareTo(left) <= 0;
		}).orElse(true);
	}

	/**
	 * Tokens of the analyses created since the first day of the month (Paris),
	 * failed ones included: they were paid.
	 */
	public TokenTotals monthToDate() {
		Instant start = ZonedDateTime.now(clock.withZone(ClockConfiguration.ZONE))
			.withDayOfMonth(1)
			.truncatedTo(ChronoUnit.DAYS)
			.toInstant();
		return analyses.sumTokensSince(start);
	}

	/**
	 * @return what remains of the monthly ceiling in euros, empty without
	 * ceiling
	 */
	private Optional<BigDecimal> budgetLeft() {
		if (properties.monthlyBudget() == null) {
			return Optional.empty();
		}
		TokenTotals spent = monthToDate();
		BigDecimal cost = properties.cost(spent.getInput(), spent.getOutput());
		return Optional.of(properties.monthlyBudget().subtract(cost == null ? BigDecimal.ZERO : cost));
	}

	private Status configurationStatus() {
		if (!properties.enabled()) {
			return new Status(State.DISABLED, "L'analyse par IA est préparée mais désactivée sur ce serveur "
					+ "(analysis.enabled=false) : aucun document n'est envoyé.", List.of(), null);
		}
		List<String> problems = new ArrayList<>();
		if (AnalysisProperties.NO_PROVIDER.equals(properties.provider())) {
			problems.add("analysis.provider n'est pas choisi");
		}
		if (properties.monthlyBudget() != null && !properties.pricesSet()) {
			problems.add("analysis.monthly-budget exige les prix du modèle (ANALYSIS_INPUT_PRICE, ANALYSIS_OUTPUT_PRICE)");
		}
		List<ExtractionClient> matching = clients.stream()
			.filter(client -> properties.provider().equalsIgnoreCase(client.provider()))
			.toList();
		if (!AnalysisProperties.NO_PROVIDER.equals(properties.provider()) && matching.isEmpty()) {
			problems.add("aucun fournisseur « " + properties.provider() + " » dans l'application");
		}
		if (matching.size() > 1) {
			problems.add("plusieurs fournisseurs « " + properties.provider() + " »");
		}
		if (!problems.isEmpty()) {
			return new Status(State.MISCONFIGURED, "Configuration de l'analyse par IA incohérente : analyse désactivée.",
					problems, null);
		}
		ExtractionClient client = matching.getFirst();
		if (client.model() == null) {
			return new Status(State.NOT_CONFIGURED, "Le fournisseur « " + properties.provider()
					+ " » n'a pas de clé ou de modèle configuré : analyse désactivée.", List.of(), null);
		}
		if (!properties.externalTransmissionAllowed()) {
			return new Status(State.TRANSMISSION_NOT_ALLOWED, "L'envoi des documents à un fournisseur externe n'est "
					+ "pas autorisé (analysis.external-transmission-allowed=false) : analyse désactivée.", List.of(),
					null);
		}
		return new Status(State.READY, "Analyse par IA disponible.", List.of(), client);
	}

	public enum State {

		/** analysis.enabled is false: the default. */
		DISABLED,

		/** Enabled, but the configuration does not hold together. */
		MISCONFIGURED,

		/** The chosen provider has no key or model. */
		NOT_CONFIGURED,

		/** The transmission of the documents is not allowed. */
		TRANSMISSION_NOT_ALLOWED,

		/** The monthly ceiling is reached. */
		BUDGET_REACHED,

		/** Analyses may be started. */
		READY

	}

	/**
	 * @param message explanation for the interface, never a secret
	 * @param problems inconsistencies of the configuration
	 * @param client the client to call, when ready
	 */
	public record Status(State state, String message, List<String> problems, ExtractionClient client) {

		public boolean ready() {
			return state == State.READY;
		}

		FailureKind failureKind() {
			return state == State.BUDGET_REACHED ? FailureKind.BUDGET_REACHED : FailureKind.NOT_CONFIGURED;
		}

	}

}
