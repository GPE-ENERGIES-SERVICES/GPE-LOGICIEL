package eu.gpefrance.tenders.tender;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TenderAwardRepository extends JpaRepository<TenderAward, Long> {

	List<TenderAward> findByTenderIdOrderByLotNumberAscIdAsc(Long tenderId);

	@Query("select a from TenderAward a where a.tender.id = :tenderId and coalesce(a.lotNumber, '') = coalesce(:lotNumber, '')")
	Optional<TenderAward> findOne(Long tenderId, String lotNumber);

	@Query("select a from TenderAward a join fetch a.tender")
	List<TenderAward> findAllWithTender();

}
