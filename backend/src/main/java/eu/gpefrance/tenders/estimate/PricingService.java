package eu.gpefrance.tenders.estimate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The settings of GPE for its estimates: read by every user, changed by an
 * administrator only (see the security configuration).
 */
@Service
public class PricingService {

	private static final Logger log = LoggerFactory.getLogger(PricingService.class);

	private final PricingSettingsRepository settings;

	private final PricingRateRepository rates;

	private final Clock clock;

	public PricingService(PricingSettingsRepository settings, PricingRateRepository rates, Clock clock) {
		this.settings = settings;
		this.rates = rates;
		this.clock = clock;
	}

	public record SettingsResponse(BigDecimal vatRate, Map<ComponentType, BigDecimal> coefficients, String updatedBy,
			Instant updatedAt) {

		static SettingsResponse from(PricingSettings settings) {
			return new SettingsResponse(settings.getVatRate(), settings.getCoefficients(), settings.getUpdatedBy(),
					settings.getUpdatedAt());
		}

	}

	public record RateResponse(Long id, String code, String label, ComponentType type, String unit, BigDecimal cost,
			boolean active, String updatedBy, Instant updatedAt) {

		static RateResponse from(PricingRate rate) {
			return new RateResponse(rate.getId(), rate.getCode(), rate.getLabel(), rate.getType(), rate.getUnit(),
					rate.getCost(), rate.isActive(), rate.getUpdatedBy(), rate.getUpdatedAt());
		}

	}

	@Transactional(readOnly = true)
	public SettingsResponse settings() {
		return SettingsResponse.from(settings.current());
	}

	/** Changes the values given only: the VAT rate when given, the coefficients listed. */
	@Transactional
	public SettingsResponse changeSettings(EstimateRequest request, String user) {
		PricingSettings current = settings.current();
		current.change(request.vatRate(), request.coefficientsOrEmpty(), user, clock.instant());
		log.info("Pricing settings changed by {}", user);
		return SettingsResponse.from(settings.save(current));
	}

	@Transactional(readOnly = true)
	public List<RateResponse> rates() {
		return rates.findAllByOrderByTypeAscLabelAsc().stream().map(RateResponse::from).toList();
	}

	@Transactional
	public RateResponse createRate(RateRequest request, String user) {
		if (rates.existsByCode(request.code().strip().toUpperCase())) {
			throw EstimateRuleException.conflict("Ce code de taux existe déjà.");
		}
		PricingRate rate = rates.save(new PricingRate(request, user, clock.instant()));
		log.info("Pricing rate {} created by {}", rate.getCode(), user);
		return RateResponse.from(rate);
	}

	@Transactional
	public RateResponse updateRate(Long id, RateRequest request, String user) {
		PricingRate rate = rates.findById(id).orElseThrow(PricingService::rateNotFound);
		if (rates.existsByCodeAndIdNot(request.code().strip().toUpperCase(), id)) {
			throw EstimateRuleException.conflict("Ce code de taux existe déjà.");
		}
		rate.change(request, user, clock.instant());
		log.info("Pricing rate {} changed by {}", rate.getCode(), user);
		return RateResponse.from(rates.save(rate));
	}

	/** The estimates keep the costs they copied: deleting a rate changes none. */
	@Transactional
	public void deleteRate(Long id, String user) {
		PricingRate rate = rates.findById(id).orElseThrow(PricingService::rateNotFound);
		rates.delete(rate);
		log.info("Pricing rate {} deleted by {}", rate.getCode(), user);
	}

	private static EstimateRuleException rateNotFound() {
		return EstimateRuleException.notFound("Taux introuvable.");
	}

}
