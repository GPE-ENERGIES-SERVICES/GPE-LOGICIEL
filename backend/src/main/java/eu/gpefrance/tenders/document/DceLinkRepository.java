package eu.gpefrance.tenders.document;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DceLinkRepository extends JpaRepository<DceLink, Long> {

	Optional<DceLink> findByTenderIdAndUrl(Long tenderId, String url);

	List<DceLink> findByTenderIdOrderByFirstSeenAtAscIdAsc(Long tenderId);

	List<DceLink> findByStatus(DceLinkStatus status);

	long countByStatus(DceLinkStatus status);

}
