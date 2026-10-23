package eu.gpefrance.tenders.eligibility;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TenderEligibilityRepository extends JpaRepository<TenderEligibility, Long> {

	/**
	 * Tenders, merged duplicates left out, whose eligibility is missing or
	 * out of date: the tender changed after it, or a saved search changed.
	 */
	@Query(nativeQuery = true, value = """
			SELECT t.id FROM tender t
			LEFT JOIN tender_eligibility e ON e.tender_id = t.id
			WHERE t.merged_into_id IS NULL
			  AND (e.tender_id IS NULL OR e.alert_revision < :revision OR e.evaluated_at < t.updated_at)
			  AND t.id > :afterId
			ORDER BY t.id
			LIMIT :limit
			""")
	List<Long> findOutOfDate(long revision, long afterId, int limit);

	@Query(nativeQuery = true, value = """
			SELECT count(*) FROM tender t
			LEFT JOIN tender_eligibility e ON e.tender_id = t.id
			WHERE t.merged_into_id IS NULL
			  AND (e.tender_id IS NULL OR e.alert_revision < :revision OR e.evaluated_at < t.updated_at)
			""")
	long countOutOfDate(long revision);

	long countByState(EligibilityState state);

	/** A new address of the consultation file: the links of the tender are reviewed at the next evaluation. */
	@Modifying
	@Query(nativeQuery = true, value = "UPDATE tender_eligibility SET alert_revision = -1 WHERE tender_id = :tenderId")
	int markOutOfDate(Long tenderId);

	/**
	 * The saved search no longer counts (its account was disabled): only the
	 * tenders it matched, or could not decide, may change.
	 * @return the number marked out of date
	 */
	@Modifying
	@Query(nativeQuery = true, value = """
			UPDATE tender_eligibility SET alert_revision = -1
			WHERE :alertId = ANY (matched_alert_ids) OR :alertId = ANY (indeterminate_alert_ids)
			""")
	int markOutOfDateReferencing(long alertId);

	/**
	 * Saved searches count again (their account was enabled): only an open
	 * tender can become eligible or indeterminate.
	 * @return the number marked out of date
	 */
	@Modifying
	@Query(nativeQuery = true, value = """
			UPDATE tender_eligibility e SET alert_revision = -1
			FROM tender t
			WHERE t.id = e.tender_id AND t.status = 'ACTIVE' AND t.merged_into_id IS NULL
			""")
	int markOpenTendersOutOfDate();

	@Query(nativeQuery = true, value = """
			SELECT count(*) FROM tender_eligibility e JOIN tender t ON t.id = e.tender_id
			WHERE :alertId = ANY (e.matched_alert_ids) AND t.status = 'ACTIVE' AND t.merged_into_id IS NULL
			""")
	long countMatching(long alertId);

	@Query(nativeQuery = true, value = """
			SELECT count(*) FROM tender_eligibility e JOIN tender t ON t.id = e.tender_id
			WHERE :alertId = ANY (e.matched_alert_ids) AND t.status = 'ACTIVE' AND t.merged_into_id IS NULL
			  AND t.published_at > CAST(:after AS date)
			""")
	long countMatchingPublishedAfter(long alertId, java.time.LocalDate after);

	@Query(nativeQuery = true, value = """
			SELECT count(*) FROM tender_eligibility e JOIN tender t ON t.id = e.tender_id
			WHERE :alertId = ANY (e.indeterminate_alert_ids) AND t.status = 'ACTIVE' AND t.merged_into_id IS NULL
			""")
	long countIndeterminate(long alertId);

}
