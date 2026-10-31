package eu.gpefrance.tenders.estimate;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PricingSettingsRepository extends JpaRepository<PricingSettings, Short> {

	default PricingSettings current() {
		return findById(PricingSettings.ID).orElseThrow();
	}

}
