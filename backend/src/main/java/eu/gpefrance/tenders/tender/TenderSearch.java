package eu.gpefrance.tenders.tender;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * Quick filters of the tender list, on top of the company's criteria.
 * @param department a department code, or {@code null}
 * @param text words searched in the title, buyer, reference and description
 * @param minDaysLeft minimum number of days before the deadline
 * @param maxAmount tenders above this amount are hidden; unknown amounts stay visible
 */
public record TenderSearch(TenderTab tab, String department, String text, Integer minDaysLeft,
		BigDecimal maxAmount) {

	Specification<Tender> toSpecification(Instant now) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>(tabPredicates(root, cb));
			if (department != null && !department.isBlank()) {
				HibernateCriteriaBuilder hcb = (HibernateCriteriaBuilder) cb;
				predicates.add(hcb.collectionContains(root.get("departments"), department.strip()));
			}
			if (text != null && !text.isBlank()) {
				predicates.add(textPredicate(root, cb, text.strip()));
			}
			if (minDaysLeft != null) {
				predicates.add(cb.greaterThanOrEqualTo(root.get("deadline"), now.plus(Duration.ofDays(minDaysLeft))));
			}
			if (maxAmount != null) {
				predicates.add(cb.or(cb.isNull(root.get("estimatedAmount")),
						cb.lessThanOrEqualTo(root.get("estimatedAmount"), maxAmount)));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

	private List<Predicate> tabPredicates(Root<Tender> root, CriteriaBuilder cb) {
		Predicate active = cb.equal(root.get("status"), TenderStatus.ACTIVE);
		return switch (tab) {
			case NEW -> List.of(cb.isTrue(root.get("retained")), followUp(root, cb, FollowUpStatus.NEW), active);
			case FILTERED_OUT -> List.of(cb.isFalse(root.get("retained")), followUp(root, cb, FollowUpStatus.NEW),
					active);
			case TO_STUDY -> List.of(followUp(root, cb, FollowUpStatus.TO_STUDY), active);
			case RESPONDING -> List.of(followUp(root, cb, FollowUpStatus.RESPONDING));
			case DISMISSED -> List.of(followUp(root, cb, FollowUpStatus.DISMISSED));
		};
	}

	private static Predicate followUp(Root<Tender> root, CriteriaBuilder cb, FollowUpStatus status) {
		return cb.equal(root.get("followUpStatus"), status);
	}

	/**
	 * Every word must appear in one of the searched fields, ignoring case and
	 * accents.
	 */
	private static Predicate textPredicate(Root<Tender> root, CriteriaBuilder cb, String text) {
		List<Predicate> words = new ArrayList<>();
		for (String word : text.toLowerCase(Locale.ROOT).split("\\s+")) {
			Expression<String> pattern = unaccent(cb, cb.literal("%" + escape(word) + "%"));
			words.add(cb.or(like(cb, root.get("title"), pattern), like(cb, root.get("buyer"), pattern),
					like(cb, root.get("reference"), pattern), like(cb, root.get("description"), pattern)));
		}
		return cb.and(words.toArray(Predicate[]::new));
	}

	private static Predicate like(CriteriaBuilder cb, Expression<String> field, Expression<String> pattern) {
		return cb.like(unaccent(cb, cb.lower(field)), pattern, '\\');
	}

	private static Expression<String> unaccent(CriteriaBuilder cb, Expression<String> value) {
		return cb.function("unaccent", String.class, value);
	}

	private static String escape(String word) {
		return word.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

}
