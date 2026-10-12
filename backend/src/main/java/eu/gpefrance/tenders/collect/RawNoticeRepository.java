package eu.gpefrance.tenders.collect;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * Every query is limited to one source: notices of different sources never
 * meet, even with the same identifier.
 */
public interface RawNoticeRepository extends JpaRepository<RawNotice, Long> {

	/**
	 * Stores the notice unless it was already collected.
	 * @return 1 if the notice is new, 0 otherwise
	 */
	@Modifying
	@Query(nativeQuery = true, value = """
			INSERT INTO raw_notice (source, external_id, payload, published_at, collected_at)
			VALUES (:source, :externalId, CAST(:payload AS jsonb), :publishedAt, now())
			ON CONFLICT (source, external_id) DO NOTHING
			""")
	int insertIfAbsent(String source, String externalId, String payload, LocalDate publishedAt);

	List<RawNotice> findTop100BySourceAndNormalizedAtIsNullOrderByPublishedAtAscIdAsc(String source);

	List<RawNotice> findBySourceAndNormalizationNoteStartingWithOrderByPublishedAtAscIdAsc(String source,
			String prefix);

	/**
	 * Notices of the source set aside with the given note, one of whose linked
	 * notices is now attached to a tender of the same source, oldest
	 * publication first.
	 */
	@Query(nativeQuery = true, value = """
			SELECT r.id FROM raw_notice r
			WHERE r.source = :source AND r.normalization_note = :note
			  AND EXISTS (SELECT 1 FROM tender_notice n JOIN tender t ON t.id = n.tender_id
			              WHERE t.source = r.source AND n.source = r.source AND n.external_id = ANY (r.linked_ids))
			ORDER BY r.published_at, r.id
			""")
	List<Long> findWaitingForAKnownNotice(String source, String note);

}
