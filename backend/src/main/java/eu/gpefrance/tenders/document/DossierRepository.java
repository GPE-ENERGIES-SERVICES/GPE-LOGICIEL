package eu.gpefrance.tenders.document;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DossierRepository extends JpaRepository<Dossier, Long> {

	List<Dossier> findByTenderIdOrderByVersionDesc(Long tenderId);

	boolean existsByTenderId(Long tenderId);

	@Query("select coalesce(max(d.version), 0) from Dossier d where d.tenderId = :tenderId")
	int findLastVersion(Long tenderId);

	List<Dossier> findByStatusIn(Collection<DossierStatus> statuses);

}
