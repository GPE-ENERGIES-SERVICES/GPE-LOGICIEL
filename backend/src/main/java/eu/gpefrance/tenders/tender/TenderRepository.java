package eu.gpefrance.tenders.tender;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TenderRepository extends JpaRepository<Tender, Long> {

	Optional<Tender> findBySourceAndExternalId(String source, String externalId);

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
