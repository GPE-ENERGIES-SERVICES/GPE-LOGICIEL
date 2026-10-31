package eu.gpefrance.tenders.estimate;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PricingRateRepository extends JpaRepository<PricingRate, Long> {

	List<PricingRate> findAllByOrderByTypeAscLabelAsc();

	Optional<PricingRate> findByCode(String code);

	boolean existsByCodeAndIdNot(String code, Long id);

	boolean existsByCode(String code);

}
