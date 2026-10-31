package eu.gpefrance.tenders.estimate;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A cost of one unit of an item (« sous-détail ») : supply, labour, rental...
 * Its unit cost is kept here, as entered: a later change of a supplier price
 * never changes it.
 */
@Entity
@Table(name = "estimate_component")
public class EstimateComponent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "item_id")
	private EstimateItem item;

	@Column(nullable = false)
	private int position;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ComponentType type;

	@Column(nullable = false)
	private String label;

	@Column(nullable = false)
	private BigDecimal quantity;

	private String unit;

	@Column(name = "unit_cost")
	private BigDecimal unitCost;

	@Column(name = "waste_rate", nullable = false)
	private BigDecimal wasteRate;

	private BigDecimal coefficient;

	@Column(name = "supplier_name")
	private String supplierName;

	@Column(name = "supplier_ref")
	private String supplierRef;

	@Column(name = "manufacturer_ref")
	private String manufacturerRef;

	@Column(name = "cost_date")
	private LocalDate costDate;

	@Column(name = "rate_code")
	private String rateCode;

	@Column(name = "offer_id")
	private Long offerId;

	protected EstimateComponent() {
	}

	EstimateComponent(ComponentRequest request) {
		change(request);
	}

	/** A copy for a new version: the same cost, never copied again from its rate or offer. */
	EstimateComponent copy() {
		return new EstimateComponent(new ComponentRequest(type, label, quantity, unit, unitCost, wasteRate, coefficient,
				supplierName, supplierRef, manufacturerRef, costDate, rateCode, offerId));
	}

	final void change(ComponentRequest request) {
		this.type = request.type();
		this.label = request.label().strip();
		this.quantity = request.quantity();
		this.unit = ItemRequest.clean(request.unit());
		this.unitCost = request.unitCost();
		this.wasteRate = request.wasteRate() == null ? BigDecimal.ZERO : request.wasteRate();
		this.coefficient = request.coefficient();
		this.supplierName = ItemRequest.clean(request.supplierName());
		this.supplierRef = ItemRequest.clean(request.supplierRef());
		this.manufacturerRef = ItemRequest.clean(request.manufacturerRef());
		this.costDate = request.costDate();
		this.rateCode = request.rateCode();
		this.offerId = request.offerId();
	}

	void attach(EstimateItem item, int position) {
		this.item = item;
		this.position = position;
	}

	void setPosition(int position) {
		this.position = position;
	}

	EstimateCalculator.Cost cost() {
		return new EstimateCalculator.Cost(type, quantity, unitCost, wasteRate, coefficient);
	}

	public Long getId() {
		return id;
	}

	EstimateItem getItem() {
		return item;
	}

	public int getPosition() {
		return position;
	}

	public ComponentType getType() {
		return type;
	}

	public String getLabel() {
		return label;
	}

	public BigDecimal getQuantity() {
		return quantity;
	}

	public String getUnit() {
		return unit;
	}

	public BigDecimal getUnitCost() {
		return unitCost;
	}

	public BigDecimal getWasteRate() {
		return wasteRate;
	}

	public BigDecimal getCoefficient() {
		return coefficient;
	}

	public String getSupplierName() {
		return supplierName;
	}

	public String getSupplierRef() {
		return supplierRef;
	}

	public String getManufacturerRef() {
		return manufacturerRef;
	}

	public LocalDate getCostDate() {
		return costDate;
	}

	public String getRateCode() {
		return rateCode;
	}

	public Long getOfferId() {
		return offerId;
	}

	/** The cost copied again from its rate. */
	void setUnitCost(BigDecimal unitCost) {
		this.unitCost = unitCost;
	}

}
