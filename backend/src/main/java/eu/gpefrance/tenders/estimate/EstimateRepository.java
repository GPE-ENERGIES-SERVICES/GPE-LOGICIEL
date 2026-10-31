package eu.gpefrance.tenders.estimate;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface EstimateRepository extends JpaRepository<Estimate, Long> {

	@Query("select e from Estimate e where e.tenderId = :tenderId order by e.lotNumber nulls first, e.version desc")
	List<Estimate> findByTender(@Param("tenderId") Long tenderId);

	/**
	 * The estimate, locked until the end of the transaction: its changes are
	 * made one after the other, each on the totals left by the previous one.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select e from Estimate e where e.id = :id")
	Optional<Estimate> lockById(@Param("id") Long id);

	@Query("""
			select coalesce(max(e.version), 0) from Estimate e
			where e.tenderId = :tenderId and (e.lotNumber = :lotNumber or (:lotNumber is null and e.lotNumber is null))
			""")
	int lastVersion(@Param("tenderId") Long tenderId, @Param("lotNumber") String lotNumber);

	@Query("""
			select count(e) > 0 from Estimate e
			where e.tenderId = :tenderId and (e.lotNumber = :lotNumber or (:lotNumber is null and e.lotNumber is null))
			  and e.status = eu.gpefrance.tenders.estimate.EstimateStatus.DRAFT
			""")
	boolean hasDraft(@Param("tenderId") Long tenderId, @Param("lotNumber") String lotNumber);

	@Query("""
			select e from Estimate e
			where e.tenderId = :tenderId and (e.lotNumber = :lotNumber or (:lotNumber is null and e.lotNumber is null))
			  and e.status = eu.gpefrance.tenders.estimate.EstimateStatus.VALIDATED and e.id <> :id
			""")
	List<Estimate> otherValidated(@Param("tenderId") Long tenderId, @Param("lotNumber") String lotNumber,
			@Param("id") Long id);

	@Query("select i.estimate.id from EstimateItem i where i.id = :itemId")
	Optional<Long> estimateOfItem(@Param("itemId") Long itemId);

	@Query("select c.item.estimate.id from EstimateComponent c where c.id = :componentId")
	Optional<Long> estimateOfComponent(@Param("componentId") Long componentId);

}
