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

}
