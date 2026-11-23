package eu.gpefrance.tenders.analysis;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the AI cost, read from its ledger only (ai_call), so that each call is
 * counted once: by service, by tender, call by call, and how the processing
 * goes. A cost is an estimate from the tokens and the price grid, null where
 * no price was set: never taken as zero.
 */
@Service
public class AiUsageService {

	/** A tender is over when it is closed, its deadline passed, or its answer sent or dropped. */
	private static final String FINISHED = """
			(t.status <> 'ACTIVE' OR t.follow_up_status IN ('SUBMITTED', 'DISMISSED')
			 OR (t.deadline IS NOT NULL AND t.deadline < ?))""";

	private final JdbcTemplate jdbc;

	private final AnalysisGate gate;

	private final AnalysisProperties properties;

	private final Clock clock;

	public AiUsageService(JdbcTemplate jdbc, AnalysisGate gate, AnalysisProperties properties, Clock clock) {
		this.jdbc = jdbc;
		this.gate = gate;
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * @param spent what the calls of the month cost so far
	 * @param budget the monthly ceiling, null without one
	 */
	public record Month(BigDecimal spent, BigDecimal budget, boolean pricesSet) {
	}

	/**
	 * @param calls ended calls (done, failed or interrupted) of the period
	 * @param averageCost cost divided by calls, null without cost
	 * @param share part of the cost of all the services, null without cost
	 */
	public record ServiceUse(String kind, long calls, long failed, BigDecimal cost, BigDecimal averageCost,
			BigDecimal share) {
	}

	/**
	 * Tenders that cost something, their total and its average by tender.
	 * @param average null when none of their calls was priced
	 */
	public record TenderGroup(long tenders, BigDecimal cost, BigDecimal average) {
	}

	/**
	 * @param finished tenders whose processing is over: their cost is final
	 * @param ongoing tenders still processed: their cost may still grow
	 * @param unattributedCost calls made for no tender (a library, a model memoir)
	 */
	public record PerTender(TenderGroup finished, TenderGroup ongoing, long unattributedCalls,
			BigDecimal unattributedCost) {
	}

	/**
	 * The processing of now: analyses running or waiting, calls failed.
	 * @param state GREEN, ORANGE (waiting, or errors this week), RED (errors in 24 h)
	 */
	public record Health(String state, long running, long waiting, long failedDay, long failedWeek, long doneWeek) {
	}

	public record Overview(Month month, Instant since, List<ServiceUse> services, long calls, BigDecimal cost,
			PerTender perTender, Health health) {
	}

	/** One tender and what its processing cost, service by service. */
	public record TenderCost(Long tenderId, String title, boolean finished, long calls, BigDecimal cost,
			List<ServiceUse> services) {
	}

	/** One call to the AI, as the ledger holds it. */
	public record Call(Long id, String kind, String status, String errorKind, String model, Long tenderId,
			String tenderTitle, Long analysisId, Instant createdAt, Integer durationMs, int inputTokens,
			int outputTokens, BigDecimal cost, boolean costConfirmed, String createdBy) {
	}

	public record PageOf<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

		static <T> PageOf<T> of(List<T> content, int page, int size, long total) {
			return new PageOf<>(content, page, size, total, (int) Math.ceil(total / (double) size));
		}

	}

	/**
	 * @param since start of the period of the services; null for all the calls
	 */
	@Transactional(readOnly = true)
	public Overview overview(Instant since) {
		Instant now = clock.instant();
		Instant from = since == null ? Instant.EPOCH : since;
		List<ServiceUse> services = services("c.created_at >= ?", Timestamp.from(from));
		BigDecimal cost = sum(services.stream().map(ServiceUse::cost).toList());
		long calls = services.stream().mapToLong(ServiceUse::calls).sum();
		List<ServiceUse> shared = services.stream()
			.map(use -> new ServiceUse(use.kind(), use.calls(), use.failed(), use.cost(), use.averageCost(),
					share(use.cost(), cost)))
			.toList();
		return new Overview(new Month(gate.monthCost(), properties.monthlyBudget(), properties.pricesSet()), since,
				shared, calls, cost, perTender(now), health(now));
	}

	/** Tenders by what they cost, the most expensive first. */
	@Transactional(readOnly = true)
	public PageOf<TenderCost> tenders(int page, int size) {
		Instant now = clock.instant();
		long total = jdbc.queryForObject(
				"SELECT count(DISTINCT tender_id) FROM ai_call WHERE tender_id IS NOT NULL AND status <> 'RUNNING'",
				Long.class);
		List<TenderCost> rows = jdbc.query("""
				SELECT t.id, t.title, %s AS finished, count(*) AS calls, sum(c.cost) AS cost
				FROM ai_call c JOIN tender t ON t.id = c.tender_id
				WHERE c.status <> 'RUNNING'
				GROUP BY t.id, t.title, t.status, t.follow_up_status, t.deadline
				ORDER BY sum(c.cost) DESC NULLS LAST, count(*) DESC, t.id
				LIMIT ? OFFSET ?""".formatted(FINISHED), (row, n) -> new TenderCost(row.getLong("id"),
				row.getString("title"), row.getBoolean("finished"), row.getLong("calls"), row.getBigDecimal("cost"),
				List.of()), Timestamp.from(now), size, (long) page * size);
		List<TenderCost> detailed = rows.stream()
			.map(row -> new TenderCost(row.tenderId(), row.title(), row.finished(), row.calls(), row.cost(),
					services("c.tender_id = ?", row.tenderId())))
			.toList();
		return PageOf.of(detailed, page, size, total);
	}

	/**
	 * The calls, filtered, the latest or the most expensive first.
	 * @param byCost the most expensive first; otherwise the latest first
	 */
	@Transactional(readOnly = true)
	public PageOf<Call> calls(String kind, String status, Long tenderId, Instant from, Instant to, boolean byCost,
			int page, int size) {
		StringBuilder where = new StringBuilder("WHERE true");
		List<Object> arguments = new ArrayList<>();
		if (kind != null) {
			where.append(" AND c.kind = ?");
			arguments.add(kind);
		}
		if (status != null) {
			where.append(" AND c.status = ?");
			arguments.add(status);
		}
		if (tenderId != null) {
			where.append(" AND c.tender_id = ?");
			arguments.add(tenderId);
		}
		if (from != null) {
			where.append(" AND c.created_at >= ?");
			arguments.add(Timestamp.from(from));
		}
		if (to != null) {
			where.append(" AND c.created_at < ?");
			arguments.add(Timestamp.from(to));
		}
		long total = jdbc.queryForObject("SELECT count(*) FROM ai_call c " + where, Long.class, arguments.toArray());
		List<Object> paged = new ArrayList<>(arguments);
		paged.add(size);
		paged.add((long) page * size);
		List<Call> calls = jdbc.query("""
				SELECT c.*, t.title FROM ai_call c LEFT JOIN tender t ON t.id = c.tender_id
				%s ORDER BY %s LIMIT ? OFFSET ?""".formatted(where,
				byCost ? "c.cost DESC NULLS LAST, c.id DESC" : "c.id DESC"), this::call, paged.toArray());
		return PageOf.of(calls, page, size, total);
	}

	private Call call(ResultSet row, int n) throws SQLException {
		Integer duration = row.getObject("duration_ms") == null ? null : row.getInt("duration_ms");
		return new Call(row.getLong("id"), row.getString("kind"), row.getString("status"),
				row.getString("error_kind"), row.getString("model"), (Long) row.getObject("tender_id"),
				row.getString("title"), (Long) row.getObject("analysis_id"),
				row.getTimestamp("created_at").toInstant(), duration, row.getInt("input_tokens"),
				row.getInt("output_tokens"), row.getBigDecimal("cost"), row.getBoolean("cost_confirmed"),
				row.getString("created_by"));
	}

	/** The ended calls matching a condition, by kind, the most expensive first. */
	private List<ServiceUse> services(String condition, Object argument) {
		return jdbc.query("""
				SELECT c.kind, count(*) AS calls, count(*) FILTER (WHERE c.status = 'FAILED') AS failed,
				       sum(c.cost) AS cost
				FROM ai_call c WHERE c.status <> 'RUNNING' AND %s
				GROUP BY c.kind ORDER BY sum(c.cost) DESC NULLS LAST, count(*) DESC""".formatted(condition),
				(row, n) -> {
					long calls = row.getLong("calls");
					BigDecimal cost = row.getBigDecimal("cost");
					return new ServiceUse(row.getString("kind"), calls, row.getLong("failed"), cost,
							average(cost, calls), null);
				}, argument);
	}

	private PerTender perTender(Instant now) {
		Map<Boolean, List<BigDecimal>> costs = new LinkedHashMap<>();
		costs.put(true, new ArrayList<>());
		costs.put(false, new ArrayList<>());
		jdbc.query("""
				SELECT %s AS finished, sum(c.cost) AS cost
				FROM ai_call c JOIN tender t ON t.id = c.tender_id
				WHERE c.status <> 'RUNNING'
				GROUP BY t.id, t.status, t.follow_up_status, t.deadline""".formatted(FINISHED),
				(ResultSet row) -> {
					costs.get(row.getBoolean("finished")).add(row.getBigDecimal("cost"));
				}, Timestamp.from(now));
		Map<String, Object> alone = jdbc.queryForMap("""
				SELECT count(*) AS calls, sum(cost) AS cost FROM ai_call
				WHERE tender_id IS NULL AND status <> 'RUNNING'""");
		return new PerTender(group(costs.get(true)), group(costs.get(false)), ((Number) alone.get("calls")).longValue(),
				(BigDecimal) alone.get("cost"));
	}

	/** A tender whose calls were never priced has no known cost: it is left out of the average, not taken as 0. */
	private static TenderGroup group(List<BigDecimal> costs) {
		List<BigDecimal> priced = costs.stream().filter(cost -> cost != null).toList();
		BigDecimal total = priced.isEmpty() ? null : sum(priced);
		return new TenderGroup(costs.size(), total, total == null ? null : average(total, priced.size()));
	}

	private Health health(Instant now) {
		Map<String, Object> analyses = jdbc.queryForMap("""
				SELECT count(*) FILTER (WHERE status = 'PROCESSING') AS running,
				       count(*) FILTER (WHERE status IN ('PENDING', 'RETRY_WAITING')) AS waiting
				FROM analysis""");
		Map<String, Object> calls = jdbc.queryForMap("""
				SELECT count(*) FILTER (WHERE status = 'RUNNING' AND analysis_id IS NULL) AS running,
				       count(*) FILTER (WHERE status = 'FAILED' AND created_at >= ?) AS failed_day,
				       count(*) FILTER (WHERE status = 'FAILED' AND created_at >= ?) AS failed_week,
				       count(*) FILTER (WHERE status = 'DONE' AND created_at >= ?) AS done_week
				FROM ai_call""", Timestamp.from(now.minus(Duration.ofDays(1))),
				Timestamp.from(now.minus(Duration.ofDays(7))), Timestamp.from(now.minus(Duration.ofDays(7))));
		long running = number(analyses, "running") + number(calls, "running");
		long waiting = number(analyses, "waiting");
		long failedDay = number(calls, "failed_day");
		long failedWeek = number(calls, "failed_week");
		String state = failedDay > 0 ? "RED" : waiting > 0 || failedWeek > 0 ? "ORANGE" : "GREEN";
		return new Health(state, running, waiting, failedDay, failedWeek, number(calls, "done_week"));
	}

	private static long number(Map<String, Object> row, String key) {
		return ((Number) row.get(key)).longValue();
	}

	private static BigDecimal sum(List<BigDecimal> values) {
		List<BigDecimal> known = values.stream().filter(value -> value != null).toList();
		return known.isEmpty() ? null : known.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private static BigDecimal average(BigDecimal cost, long count) {
		return cost == null || count == 0 ? null : cost.divide(BigDecimal.valueOf(count), 6, RoundingMode.HALF_UP);
	}

	private static BigDecimal share(BigDecimal cost, BigDecimal total) {
		return cost == null || total == null || total.signum() == 0 ? null
				: cost.divide(total, 4, RoundingMode.HALF_UP);
	}

}
