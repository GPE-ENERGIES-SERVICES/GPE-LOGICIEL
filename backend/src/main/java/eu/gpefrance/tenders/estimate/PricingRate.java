package eu.gpefrance.tenders.estimate;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A cost of GPE picked in an estimate: an hour of a profile (électricien,
 * chef d'équipe), a kilometre, a day of rental... Picking it copies its cost
 * in the estimate.
 */
@Entity
@Table(name = "pricing_rate")
public class PricingRate {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String code;

	@Column(nullable = false)
	private String label;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ComponentType type;

	@Column(nullable = false)
	private String unit;

	@Column(nullable = false)
	private BigDecimal cost;

	@Column(nullable = false)
	private boolean active;

	@Column(name = "updated_by")
	private String updatedBy;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected PricingRate() {
	}

	PricingRate(RateRequest request, String user, Instant now) {
		change(request, user, now);
	}

	final void change(RateRequest request, String user, Instant now) {
		this.code = request.code().strip().toUpperCase();
		this.label = request.label().strip();
		this.type = request.type();
		this.unit = request.unit().strip();
		this.cost = request.cost();
		this.active = request.active() == null || request.active();
		this.updatedBy = user;
		this.updatedAt = now;
	}

	public Long getId() {
		return id;
	}

	public String getCode() {
		return code;
	}

	public String getLabel() {
		return label;
	}

	public ComponentType getType() {
		return type;
	}

	public String getUnit() {
		return unit;
	}

	public BigDecimal getCost() {
		return cost;
	}

	public boolean isActive() {
		return active;
	}

	public String getUpdatedBy() {
		return updatedBy;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
