package eu.gpefrance.tenders.document;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DceLinkEventRepository extends JpaRepository<DceLinkEvent, Long> {

	List<DceLinkEvent> findByDceLinkIdOrderByOccurredAtDescIdDesc(Long dceLinkId);

	List<DceLinkEvent> findAllByOrderByOccurredAtDescIdDesc(Pageable pageable);

}
