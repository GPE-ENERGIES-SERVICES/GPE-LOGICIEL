package eu.gpefrance.tenders.estimate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;

/**
 * The VAT rate and the sale coefficients given to a new estimate: a single
 * row, edited by an administrator.
 */
@Entity
@Table(name = "pricing_settings")
public class PricingSettings {

	static final short ID = 1;

	@Id
	private Short id;

	@Column(name = "vat_rate", nullable = false)
	private BigDecimal vatRate;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "pricing_coefficient", joinColumns = @JoinColumn(name = "settings_id"))
	@MapKeyEnumerated(EnumType.STRING)
	@MapKeyColumn(name = "component_type")
	@Column(name = "coefficient", nullable = false)
	private Map<ComponentType, BigDecimal> coefficients = new EnumMap<>(ComponentType.class);

	@Column(name = "agency_fee_rate", nullable = false)
	private BigDecimal agencyFeeRate;

	@Column(name = "overhead_rate", nullable = false)
	private BigDecimal overheadRate;

	@Column(name = "service_fee_rate", nullable = false)
	private BigDecimal serviceFeeRate;

	/** Annual rate updating a price of the library from its year: 0.03 for 3 % a year. */
	@Column(name = "price_index_rate", nullable = false)
	private BigDecimal priceIndexRate;

	@Column(name = "updated_by")
	private String updatedBy;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected PricingSettings() {
	}

	void change(SettingsRequest request, String user, Instant now) {
		if (request.vatRate() != null) {
			this.vatRate = request.vatRate();
		}
		if (request.agencyFeeRate() != null) {
			this.agencyFeeRate = request.agencyFeeRate();
		}
		if (request.overheadRate() != null) {
			this.overheadRate = request.overheadRate();
		}
		if (request.serviceFeeRate() != null) {
			this.serviceFeeRate = request.serviceFeeRate();
		}
		if (request.priceIndexRate() != null) {
			this.priceIndexRate = request.priceIndexRate();
		}
		this.coefficients.putAll(request.coefficientsOrEmpty());
		this.updatedBy = user;
		this.updatedAt = now;
	}

	public BigDecimal getVatRate() {
		return vatRate;
	}

	public Map<ComponentType, BigDecimal> getCoefficients() {
		return Map.copyOf(coefficients);
	}

	public BigDecimal getAgencyFeeRate() {
		return agencyFeeRate;
	}

	public BigDecimal getOverheadRate() {
		return overheadRate;
	}

	public BigDecimal getServiceFeeRate() {
		return serviceFeeRate;
	}

	public BigDecimal getPriceIndexRate() {
		return priceIndexRate;
	}

	public String getUpdatedBy() {
		return updatedBy;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
