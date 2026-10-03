package eu.gpefrance.tenders.analysis;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisRepository extends JpaRepository<Analysis, Long> {

	@EntityGraph(attributePaths = { "fields", "documentIds" })
	Optional<Analysis> findWithFieldsById(Long id);

	List<Analysis> findByDossierIdOrderByIdDesc(Long dossierId);

	List<Analysis> findByTenderIdOrderByIdDesc(Long tenderId);

	List<Analysis> findByStatusIn(Collection<AnalysisStatus> statuses);

	Optional<Analysis> findFirstByTenderIdAndStatusInOrderByIdDesc(Long tenderId, Collection<AnalysisStatus> statuses);

	Optional<Analysis> findFirstByTenderIdAndInputFingerprintAndStatusOrderByIdDesc(Long tenderId, String fingerprint,
			AnalysisStatus status);

}
