package eu.gpefrance.tenders.tender;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TenderRepository extends JpaRepository<Tender, Long>, JpaSpecificationExecutor<Tender> {

	Optional<Tender> findBySourceAndExternalId(String source, String externalId);

	List<Tender> findBySourceAndProcedureId(String source, String procedureId);

	/**
	 * Tenders of the source made by a complete rectification received before
	 * its initial notice, whose rectification cites the given notice
	 * explicitly (raw_notice.linked_ids).
	 */
	@Query(nativeQuery = true, value = """
			SELECT t.id FROM tender t
			JOIN tender_notice n ON n.tender_id = t.id AND n.external_id = t.external_id AND n.kind = 'RECTIFICATION'
			JOIN raw_notice r ON r.id = n.raw_notice_id AND r.source = t.source
			WHERE t.source = :source AND CAST(:externalId AS VARCHAR) = ANY (r.linked_ids)
			ORDER BY t.id
			""")
	List<Long> findMadeByARectificationCiting(String source, String externalId);

	/**
	 * Tenders never filtered, or changed by a notice since they were.
	 */
	@Query("select t from Tender t where t.filteredAt is null or t.updatedAt > t.filteredAt order by t.id")
	List<Tender> findToFilter(Pageable pageable);

	List<Tender> findByIdGreaterThanOrderById(Long id, Pageable pageable);

	@Modifying
	@Query("""
			update Tender t set t.status = eu.gpefrance.tenders.tender.TenderStatus.CLOSED, t.updatedAt = :now
			where t.status = eu.gpefrance.tenders.tender.TenderStatus.ACTIVE and t.deadline < :now
			""")
	int closeExpired(Instant now);

}
