package eu.gpefrance.tenders.analysis;

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

	Optional<Analysis> findFirstByTenderIdAndStatusInOrderByIdDesc(Long tenderId, Collection<AnalysisStatus> statuses);

	Optional<Analysis> findFirstByTenderIdAndInputFingerprintAndStatusOrderByIdDesc(Long tenderId, String fingerprint,
			AnalysisStatus status);

	List<Analysis> findAllByOrderByIdDesc(Pageable pageable);

	long countByStatus(AnalysisStatus status);

	/**
	 * Tokens consumed by every analysis, failed ones included: they were paid.
	 */
	@Query("select coalesce(sum(a.inputTokens), 0) as input, coalesce(sum(a.outputTokens), 0) as output from Analysis a")
	TokenTotals sumTokens();

	interface TokenTotals {

		long getInput();

		long getOutput();

	}

}
