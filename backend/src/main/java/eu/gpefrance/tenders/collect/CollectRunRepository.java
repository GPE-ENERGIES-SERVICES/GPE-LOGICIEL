package eu.gpefrance.tenders.collect;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectRunRepository extends JpaRepository<CollectRun, Long> {

	Optional<CollectRun> findFirstBySourceAndStatusOrderByStartedAtDesc(String source, CollectStatus status);

	Optional<CollectRun> findFirstBySourceOrderByStartedAtDesc(String source);

	List<CollectRun> findAllByOrderByStartedAtDesc(Pageable pageable);

	List<CollectRun> findAllByStatus(CollectStatus status);

}
