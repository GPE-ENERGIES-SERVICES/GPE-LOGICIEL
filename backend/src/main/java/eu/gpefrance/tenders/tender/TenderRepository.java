package eu.gpefrance.tenders.tender;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TenderRepository extends JpaRepository<Tender, Long> {

	Optional<Tender> findBySourceAndExternalId(String source, String externalId);

}
