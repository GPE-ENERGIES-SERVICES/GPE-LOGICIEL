package eu.gpefrance.tenders.tender;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TenderFollowRepository extends JpaRepository<TenderFollow, TenderFollow.Key> {

	/**
	 * Among the given tenders, those the user follows.
	 */
	@Query("select f.tenderId from TenderFollow f where f.userId = :userId and f.tenderId in :tenderIds")
	List<Long> findFollowed(Long userId, Collection<Long> tenderIds);

	boolean existsByTenderId(Long tenderId);

}
