package eu.gpefrance.tenders.tender;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.springframework.data.jpa.domain.Specification;

import eu.gpefrance.tenders.ClockConfiguration;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Filters of the tender list, all combinable, as sent in the request
 * parameters. A list matches when one of its values does. Information missing
 * from a notice never hides a tender for an amount.
 * @param q words searched in the title, buyer, reference, description, place
 * and CPV codes
 * @param department department codes
 * @param region name of a region, see {@link Regions}
 * @param city text contained in the place of execution
 * @param minDaysLeft minimum number of days before the deadline
 * @param maxDaysLeft deadline not passed and at most this number of days away
 * @param publishedWithinDays published at most this number of days ago
 * @param hasLots {@code true} with at least one lot, {@code false} without
 * @param buyer text contained in the name of the buyer
 * @param year year of publication
 * @param result outcomes of the consultation, see {@link Result}
 */
public record TenderSearch(String q, List<String> department, String region, String city,
		List<MarketType> marketType, List<ProcedureType> procedure, List<FollowUpStatus> status,
		@PositiveOrZero Integer minDaysLeft, @PositiveOrZero Integer maxDaysLeft,
		@PositiveOrZero Integer publishedWithinDays, @PositiveOrZero BigDecimal minAmount,
		@PositiveOrZero BigDecimal maxAmount, Boolean hasLots, String buyer, @Positive Integer year,
		List<Result> result) {

	/**
	 * Outcome of a consultation, read as in the detail: AWARDED once an award
	 * notice is published, otherwise CANCELLED or UNSUCCESSFUL from the status
	 * of the consultation, UNKNOWN for the others. It never depends on the
	 * follow-up status of the team.
	 */
	public enum Result {

		AWARDED, CANCELLED, UNSUCCESSFUL, UNKNOWN

	}

	static TenderSearch none() {
		return new TenderSearch(null, null, null, null, null, null, null, null, null, null, null, null, null, null,
				null, null);
	}

	TenderSearch withStatus(List<FollowUpStatus> statuses) {
		return new TenderSearch(q, department, region, city, marketType, procedure, statuses, minDaysLeft,
				maxDaysLeft, publishedWithinDays, minAmount, maxAmount, hasLots, buyer, year, result);
	}

	/**
	 * @param userId the logged-in user, whose followed tenders make the
	 * FOLLOWED tab
	 */
	Specification<Tender> toSpecification(TenderTab tab, Long userId, Instant now) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			predicates.add(tabPredicate(tab, userId, root, query, cb));
			HibernateCriteriaBuilder hcb = (HibernateCriteriaBuilder) cb;
			if (q != null && !q.isBlank()) {
				predicates.add(textPredicate(root, cb, q.strip()));
			}
			if (department != null && !department.isEmpty()) {
				predicates.add(anyDepartment(root, hcb, department));
			}
			if (region != null && !region.isBlank()) {
				predicates.add(anyDepartment(root, hcb, Regions.departmentsOf(region)));
			}
			if (city != null && !city.isBlank()) {
				predicates.add(like(cb, root.get("location"), unaccent(cb, cb.literal(pattern(city.strip())))));
			}
			if (marketType != null && !marketType.isEmpty()) {
				predicates.add(cb.or(marketType.stream()
					.map(type -> hcb.collectionContains(root.<List<String>>get("marketTypes"), type.name()))
					.toArray(Predicate[]::new)));
			}
			if (procedure != null && !procedure.isEmpty()) {
				predicates.add(root.get("procedureType").in(procedure));
			}
			if (status != null && !status.isEmpty()) {
				predicates.add(root.get("followUpStatus").in(status));
			}
			if (minDaysLeft != null) {
				predicates.add(cb.greaterThanOrEqualTo(root.get("deadline"), now.plus(Duration.ofDays(minDaysLeft))));
			}
			if (maxDaysLeft != null) {
				predicates.add(cb.between(root.get("deadline"), now, now.plus(Duration.ofDays(maxDaysLeft))));
			}
			if (publishedWithinDays != null) {
				LocalDate since = LocalDate.ofInstant(now, ClockConfiguration.ZONE).minusDays(publishedWithinDays);
				predicates.add(cb.greaterThanOrEqualTo(root.get("publishedAt"), since));
			}
			if (minAmount != null) {
				predicates.add(cb.or(cb.isNull(root.get("estimatedAmount")),
						cb.greaterThanOrEqualTo(root.get("estimatedAmount"), minAmount)));
			}
			if (maxAmount != null) {
				predicates.add(cb.or(cb.isNull(root.get("estimatedAmount")),
						cb.lessThanOrEqualTo(root.get("estimatedAmount"), maxAmount)));
			}
			if (hasLots != null) {
				predicates.add(hasLots ? cb.isNotEmpty(root.get("lots")) : cb.isEmpty(root.get("lots")));
			}
			if (buyer != null && !buyer.isBlank()) {
				predicates.add(like(cb, root.get("buyer"), unaccent(cb, cb.literal(pattern(buyer.strip())))));
			}
			if (year != null) {
				predicates.add(cb.between(root.get("publishedAt"), LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)));
			}
			if (result != null && !result.isEmpty()) {
				predicates.add(resultPredicate(result, root, query, cb));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

	private static Predicate tabPredicate(TenderTab tab, Long userId, Root<Tender> root,
			CriteriaQuery<?> query, CriteriaBuilder cb) {
		Predicate active = cb.equal(root.get("status"), TenderStatus.ACTIVE);
		Expression<FollowUpStatus> followUp = root.get("followUpStatus");
		// Excluded by the filter and no decision yet; a tender not filtered yet is not excluded.
		Predicate notFilteredOut = cb.or(cb.isNull(root.get("retained")), cb.isTrue(root.get("retained")),
				cb.notEqual(followUp, FollowUpStatus.NEW));
		return switch (tab) {
			case NEW -> cb.and(cb.isTrue(root.get("retained")), cb.equal(followUp, FollowUpStatus.NEW), active);
			case TO_PROCESS -> cb.and(followUp.in(FollowUpStatus.TO_STUDY, FollowUpStatus.RESPONDING), active);
			case FOLLOWED -> {
				Subquery<Long> follows = query.subquery(Long.class);
				Root<TenderFollow> follow = follows.from(TenderFollow.class);
				follows.select(follow.get("tenderId"))
					.where(cb.equal(follow.get("tenderId"), root.get("id")), cb.equal(follow.get("userId"), userId));
				yield cb.exists(follows);
			}
			case ALL -> cb.and(notFilteredOut, active);
			case DISMISSED -> cb.and(cb.equal(followUp, FollowUpStatus.DISMISSED), active);
			case ARCHIVED -> cb.notEqual(root.get("status"), TenderStatus.ACTIVE);
			case FILTERED_OUT -> cb.and(cb.not(notFilteredOut), active);
		};
	}

	private static Predicate resultPredicate(List<Result> results, Root<Tender> root, CriteriaQuery<?> query,
			CriteriaBuilder cb) {
		Subquery<Long> awards = query.subquery(Long.class);
		Root<TenderNotice> notice = awards.from(TenderNotice.class);
		awards.select(notice.get("id"))
			.where(cb.equal(notice.get("tender"), root), cb.equal(notice.get("kind"), NoticeKind.AWARD),
					cb.equal(notice.get("source"), root.get("source")));
		Predicate awarded = cb.exists(awards);
		Expression<TenderStatus> status = root.get("status");
		return cb.or(results.stream().map(result -> switch (result) {
			case AWARDED -> awarded;
			case CANCELLED -> cb.and(cb.not(awarded), cb.equal(status, TenderStatus.CANCELLED));
			case UNSUCCESSFUL -> cb.and(cb.not(awarded), cb.equal(status, TenderStatus.UNSUCCESSFUL));
			case UNKNOWN -> cb.and(cb.not(awarded), status.in(TenderStatus.CANCELLED, TenderStatus.UNSUCCESSFUL).not());
		}).toArray(Predicate[]::new));
	}

	/**
	 * Order of the results: by {@code sort} ({@code deadline}, {@code published}
	 * or {@code amount}, with a leading "-" for descending), missing values last.
	 * By default the closest deadline first, the passed deadlines after the
	 * others, the most recent first. Applied to the select query only.
	 */
	static Specification<Tender> orderedBy(String sort, Instant now) {
		return (root, query, cb) -> {
			if (!Long.class.equals(query.getResultType())) {
				query.orderBy(orders(sort, root, (HibernateCriteriaBuilder) cb, now));
			}
			return null;
		};
	}

	private static List<Order> orders(String sort, Root<Tender> root, HibernateCriteriaBuilder cb, Instant now) {
		String key = sort == null ? "" : sort.replaceFirst("^-", "");
		boolean descending = sort != null && sort.startsWith("-");
		List<Order> orders = new ArrayList<>();
		switch (key) {
			case "published" -> orders.add(descending ? cb.desc(root.get("publishedAt"), false)
					: cb.asc(root.get("publishedAt"), false));
			case "amount" -> orders.add(descending ? cb.desc(root.get("estimatedAmount"), false)
					: cb.asc(root.get("estimatedAmount"), false));
			default -> {
				Expression<Instant> deadline = root.get("deadline");
				if (descending) {
					orders.add(cb.desc(deadline, false));
				}
				else {
					Predicate passed = cb.lessThan(deadline, now);
					orders.add(cb.asc(cb.<Integer>selectCase().when(passed, 1).otherwise(0)));
					orders.add(cb.asc(cb.<Instant>selectCase().when(passed, cb.nullLiteral(Instant.class))
						.otherwise(deadline), false));
					orders.add(cb.desc(deadline, false));
				}
			}
		}
		orders.add(cb.desc(root.get("id")));
		return orders;
	}

	private static Predicate anyDepartment(Root<Tender> root, HibernateCriteriaBuilder cb, List<String> codes) {
		return cb.or(codes.stream()
			.map(code -> cb.collectionContains(root.<List<String>>get("departments"), code.strip()))
			.toArray(Predicate[]::new));
	}

	/**
	 * Every word must appear in one of the searched fields, ignoring case and
	 * accents.
	 */
	private static Predicate textPredicate(Root<Tender> root, CriteriaBuilder cb, String text) {
		List<Predicate> words = new ArrayList<>();
		Expression<String> cpvCodes = cb.function("array_to_string", String.class, root.get("cpvCodes"),
				cb.literal(" "));
		for (String word : text.toLowerCase(Locale.ROOT).split("\\s+")) {
			Expression<String> pattern = unaccent(cb, cb.literal(pattern(word)));
			words.add(cb.or(like(cb, root.get("title"), pattern), like(cb, root.get("buyer"), pattern),
					like(cb, root.get("reference"), pattern), like(cb, root.get("description"), pattern),
					like(cb, root.get("location"), pattern), like(cb, cpvCodes, pattern)));
		}
		return cb.and(words.toArray(Predicate[]::new));
	}

	private static String pattern(String word) {
		return "%" + word.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
	}

	private static Predicate like(CriteriaBuilder cb, Expression<String> field, Expression<String> pattern) {
		return cb.like(unaccent(cb, cb.lower(field)), pattern, '\\');
	}

	private static Expression<String> unaccent(CriteriaBuilder cb, Expression<String> value) {
		return cb.function("unaccent", String.class, value);
	}

}
