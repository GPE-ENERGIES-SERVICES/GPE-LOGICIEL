package eu.gpefrance.tenders.estimate;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EstimateSuggestionRepository extends JpaRepository<EstimateSuggestion, Long> {

	List<EstimateSuggestion> findByEstimateIdOrderByIdAsc(Long estimateId);

	/** The proposals not decided yet, replaced by new ones. */
	@Modifying
	@Query("""
			delete from EstimateSuggestion s where s.estimateId = :estimateId and s.kind = :kind
			  and (:itemId is null or s.itemId = :itemId)
			  and s.status = eu.gpefrance.tenders.estimate.EstimateSuggestion.Status.PENDING""")
	void deletePending(@Param("estimateId") Long estimateId, @Param("kind") EstimateSuggestion.Kind kind,
			@Param("itemId") Long itemId);

}
