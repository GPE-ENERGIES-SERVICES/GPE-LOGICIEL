package eu.gpefrance.tenders.analysis;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AnalysisRepository extends JpaRepository<Analysis, Long> {

	@EntityGraph(attributePaths = { "fields", "documentIds" })
	Optional<Analysis> findWithFieldsById(Long id);

	List<Analysis> findByDossierIdOrderByIdDesc(Long dossierId);

	List<Analysis> findByTenderIdOrderByIdDesc(Long tenderId);

	List<Analysis> findByStatusIn(Collection<AnalysisStatus> statuses);

	List<Analysis> findByTenderIdAndKindOrderByIdDesc(Long tenderId, AnalysisKind kind);

	Optional<Analysis> findFirstByTenderIdAndKindAndStatusInOrderByIdDesc(Long tenderId, AnalysisKind kind,
			Collection<AnalysisStatus> statuses);

	Optional<Analysis> findFirstByTenderIdAndKindAndInputFingerprintAndStatusOrderByIdDesc(Long tenderId,
			AnalysisKind kind, String fingerprint, AnalysisStatus status);

	List<Analysis> findAllByOrderByIdDesc(Pageable pageable);

	/** The latest finished analyses of a kind, to tell how long they take. */
	List<Analysis> findTop20ByKindAndStatusAndStartedAtNotNullOrderByIdDesc(AnalysisKind kind,
			AnalysisStatus status);

	long countByStatus(AnalysisStatus status);

	/**
	 * Tokens consumed by every analysis, failed ones included: they were paid.
	 */
	@Query("select coalesce(sum(a.inputTokens), 0) as input, coalesce(sum(a.outputTokens), 0) as output from Analysis a")
	TokenTotals sumTokens();

	/**
	 * Tokens of the analyses created since the given time, failed ones
	 * included.
	 */
	@Query("""
			select coalesce(sum(a.inputTokens), 0) as input, coalesce(sum(a.outputTokens), 0) as output
			from Analysis a where a.createdAt >= :since""")
	TokenTotals sumTokensSince(Instant since);

	List<Analysis> findByStatus(AnalysisStatus status);

	interface TokenTotals {

		long getInput();

		long getOutput();

	}

}
