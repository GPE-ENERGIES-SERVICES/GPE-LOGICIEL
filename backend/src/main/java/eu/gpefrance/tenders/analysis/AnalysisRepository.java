package eu.gpefrance.tenders.analysis;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisRepository extends JpaRepository<Analysis, Long> {

	List<Analysis> findByDossierIdOrderByIdDesc(Long dossierId);

}
