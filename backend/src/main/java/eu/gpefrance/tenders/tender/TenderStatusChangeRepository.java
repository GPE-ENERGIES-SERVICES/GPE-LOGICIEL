package eu.gpefrance.tenders.tender;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TenderStatusChangeRepository extends JpaRepository<TenderStatusChange, Long> {

	List<TenderStatusChange> findByTenderIdOrderByChangedAtDescIdDesc(Long tenderId);

}
