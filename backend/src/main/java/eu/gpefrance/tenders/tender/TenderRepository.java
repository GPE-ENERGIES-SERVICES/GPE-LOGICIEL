package eu.gpefrance.tenders.tender;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TenderRepository extends JpaRepository<Tender, Long> {

	Optional<Tender> findBySourceAndExternalId(String source, String externalId);

	@Modifying
	@Query("""
			update Tender t set t.status = eu.gpefrance.tenders.tender.TenderStatus.CLOSED, t.updatedAt = :now
			where t.status = eu.gpefrance.tenders.tender.TenderStatus.ACTIVE and t.deadline < :now
			""")
	int closeExpired(Instant now);

}
