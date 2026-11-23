package eu.gpefrance.tenders.analysis;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import eu.gpefrance.tenders.analysis.ExtractionClient.ModelAnswer;

/**
 * The ledger of the AI: every call to a provider, analyses and assistance
 * alike, is one line of ai_call, written when it starts and closed when it
 * ends, with its tender when it has one and its cost. Each write has its own
 * transaction, so that a call that failed stays counted when the work that
 * asked for it is rolled back. The totals of the application read only this
 * ledger: a call is counted once.
 */
@Component
public class AiLedger {

	private static final Logger log = LoggerFactory.getLogger(AiLedger.class);

	private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

	private final JdbcTemplate jdbc;

	private final TransactionTemplate apart;

	private final AnalysisProperties properties;

	private final AiPrices prices;

	private final Clock clock;

	public AiLedger(JdbcTemplate jdbc, PlatformTransactionManager transactions, AnalysisProperties properties,
			AiPrices prices, Clock clock) {
		this.jdbc = jdbc;
		this.apart = new TransactionTemplate(transactions);
		this.apart.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.properties = properties;
		this.prices = prices;
		this.clock = clock;
	}

	/**
	 * A call about to be sent for a part of the application.
	 * @param tenderId the tender it works for, null when it works for none
	 * @return the line of the call, to close with {@link #done} or {@link #failed}
	 */
	public long start(String kind, String model, Long tenderId, String user) {
		return insert("""
				INSERT INTO ai_call (kind, model, input_tokens, output_tokens, created_by, created_at, tender_id, status)
				VALUES (?, ?, 0, 0, ?, ?, ?, 'RUNNING')""", kind, model, user, Timestamp.from(clock.instant()), tenderId);
	}

	/** An attempt of an analysis about to be sent: its tender and who asked come from the analysis. */
	public long startAnalysis(Long analysisId, String model) {
		return insert("""
				INSERT INTO ai_call (kind, model, input_tokens, output_tokens, created_by, created_at, tender_id,
				                     analysis_id, status)
				SELECT a.kind, ?, 0, 0, a.requested_by, ?, a.tender_id, a.id, 'RUNNING' FROM analysis a WHERE a.id = ?""",
				model, Timestamp.from(clock.instant()), analysisId);
	}

	/** The provider answered: its tokens and their cost. */
	public void done(long callId, ModelAnswer usage) {
		close(callId, "DONE", null, usage);
	}

	/**
	 * The call or the reading of its answer failed.
	 * @param usage the tokens the provider counted anyway, null when unknown
	 */
	public void failed(long callId, String errorKind, ModelAnswer usage) {
		close(callId, "FAILED", errorKind, usage);
	}

	/**
	 * Calls left running by a stop of the server are interrupted, not failed;
	 * lines without cost (made before the prices, or copied from the former
	 * analyses) are priced with the grid of now, still as an estimate.
	 */
	@EventListener(ApplicationReadyEvent.class)
	public void tidy() {
		int interrupted = jdbc.update("UPDATE ai_call SET status = 'INTERRUPTED' WHERE status = 'RUNNING'");
		int priced = 0;
		for (var row : jdbc.queryForList("""
				SELECT id, model, input_tokens, output_tokens FROM ai_call
				WHERE cost IS NULL AND (input_tokens > 0 OR output_tokens > 0)""")) {
			BigDecimal cost = cost((String) row.get("model"), ((Number) row.get("input_tokens")).longValue(),
					((Number) row.get("output_tokens")).longValue());
			if (cost != null) {
				priced += jdbc.update("UPDATE ai_call SET cost = ? WHERE id = ?", cost, row.get("id"));
			}
		}
		if (interrupted + priced > 0) {
			log.info("AI ledger: {} calls interrupted by a stop, {} calls priced", interrupted, priced);
		}
	}

	/**
	 * The estimated cost of tokens: the price of the model when it has one,
	 * otherwise the prices of the analysis; null when no price is set.
	 */
	public BigDecimal cost(String model, long inputTokens, long outputTokens) {
		AiPrices.ModelPrice price = model == null ? null : prices.models().get(model);
		BigDecimal input = price != null ? price.input() : properties.inputPricePerMillion();
		BigDecimal output = price != null ? price.output() : properties.outputPricePerMillion();
		if (input.signum() == 0 && output.signum() == 0) {
			return null;
		}
		return input.multiply(BigDecimal.valueOf(inputTokens))
			.add(output.multiply(BigDecimal.valueOf(outputTokens)))
			.divide(MILLION, 6, RoundingMode.HALF_UP);
	}

	/** What the calls of the calendar month cost so far, in euros. */
	public BigDecimal spentSince(Instant since) {
		return jdbc.queryForObject("SELECT coalesce(sum(cost), 0) FROM ai_call WHERE created_at >= ?",
				BigDecimal.class, Timestamp.from(since));
	}

	private void close(long callId, String status, String errorKind, ModelAnswer usage) {
		apart.executeWithoutResult(tx -> {
			Instant now = clock.instant();
			if (usage == null) {
				jdbc.update("""
						UPDATE ai_call SET status = ?, error_kind = coalesce(?, error_kind),
						       duration_ms = coalesce(duration_ms, (extract(epoch from (? - created_at)) * 1000)::int)
						WHERE id = ?""", status, errorKind, Timestamp.from(now), callId);
				return;
			}
			String model = jdbc.queryForObject("SELECT model FROM ai_call WHERE id = ?", String.class, callId);
			jdbc.update("""
					UPDATE ai_call SET status = ?, error_kind = ?, input_tokens = ?, output_tokens = ?, cost = ?,
					       duration_ms = (extract(epoch from (? - created_at)) * 1000)::int
					WHERE id = ?""", status, errorKind, usage.inputTokens(), usage.outputTokens(),
					cost(model, usage.inputTokens(), usage.outputTokens()), Timestamp.from(now), callId);
		});
	}

	private long insert(String sql, Object... arguments) {
		Long id = apart.execute(tx -> {
			GeneratedKeyHolder key = new GeneratedKeyHolder();
			jdbc.update(connection -> {
				var statement = connection.prepareStatement(sql, new String[] { "id" });
				for (int i = 0; i < arguments.length; i++) {
					statement.setObject(i + 1, arguments[i]);
				}
				return statement;
			}, key);
			return key.getKey().longValue();
		});
		return id;
	}

}
