package eu.gpefrance.tenders.estimate;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EstimateEventRepository extends JpaRepository<EstimateEvent, Long> {

	List<EstimateEvent> findByEstimateIdOrderByIdDesc(Long estimateId);

}
