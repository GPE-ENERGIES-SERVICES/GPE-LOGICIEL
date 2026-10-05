package eu.gpefrance.tenders.collect;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

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

	List<RawNotice> findTop100ByNormalizedAtIsNullOrderByPublishedAtAscIdAsc();

	List<RawNotice> findByNormalizationNoteStartingWithOrderByPublishedAtAscIdAsc(String prefix);

	/**
	 * Notices set aside with the given note whose linked notice
	 * ({@code annonce_lie}, {@code annonces_anterieures}) is now attached to a
	 * tender, oldest publication first. The linked identifiers are joined to
	 * tender_notice, so its index on external_id is used.
	 */
	@Query(nativeQuery = true, value = """
			SELECT r.id FROM raw_notice r
			WHERE r.id IN (
			    SELECT o.id FROM raw_notice o
			    CROSS JOIN LATERAL (
			        SELECT jsonb_array_elements_text(CASE jsonb_typeof(o.payload -> 'annonce_lie')
			            WHEN 'array' THEN o.payload -> 'annonce_lie'
			            WHEN 'string' THEN jsonb_build_array(o.payload -> 'annonce_lie')
			            ELSE CAST('[]' AS jsonb) END) AS linked
			        UNION ALL
			        SELECT jsonb_array_elements_text(CASE jsonb_typeof(o.payload -> 'annonces_anterieures')
			            WHEN 'array' THEN o.payload -> 'annonces_anterieures'
			            WHEN 'string' THEN jsonb_build_array(o.payload -> 'annonces_anterieures')
			            ELSE CAST('[]' AS jsonb) END)) l
			    JOIN tender_notice n ON n.external_id = l.linked
			    WHERE o.normalization_note = :note)
			ORDER BY r.published_at, r.id
			""")
	List<Long> findWaitingForAKnownNotice(String note);

}
