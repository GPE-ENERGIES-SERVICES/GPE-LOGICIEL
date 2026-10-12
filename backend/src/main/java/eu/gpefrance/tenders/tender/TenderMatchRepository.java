package eu.gpefrance.tenders.tender;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TenderMatchRepository extends JpaRepository<TenderMatch, Long> {

	boolean existsByTenderIdAndMatchedTenderId(Long tenderId, Long matchedTenderId);

	/** The matches of a tender, whichever side it is on. */
	List<TenderMatch> findByTenderIdOrMatchedTenderIdOrderByIdAsc(Long tenderId, Long matchedTenderId);

}
