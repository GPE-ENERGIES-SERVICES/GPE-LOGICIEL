package eu.gpefrance.tenders.collect;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CollectRunRepository extends JpaRepository<CollectRun, Long> {

	Optional<CollectRun> findFirstBySourceAndStatusOrderByStartedAtDescIdDesc(String source, CollectStatus status);

	Optional<CollectRun> findFirstBySourceOrderByStartedAtDescIdDesc(String source);

	List<CollectRun> findAllByOrderByStartedAtDesc(Pageable pageable);

	List<CollectRun> findAllByStatus(CollectStatus status);

	/**
	 * Whether the source has a run started since the given time, other than
	 * one interrupted by a restart of the application.
	 */
	@Query("""
			select count(r) > 0 from CollectRun r
			where r.source = :source and r.startedAt >= :since
			  and (r.errorMessage is null or r.errorMessage <> :interrupted)
			""")
	boolean existsAttemptSince(String source, Instant since, String interrupted);

}
