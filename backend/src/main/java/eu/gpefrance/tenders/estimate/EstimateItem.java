package eu.gpefrance.tenders.estimate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.BatchSize;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * An item sold (« poste »), as in the price schedule: its quantity, how its
 * unit price is set, and the costs of one of its units.
 */
@Entity
@Table(name = "estimate_item")
public class EstimateItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "estimate_id")
	private Estimate estimate;

	@Column(nullable = false)
	private int position;

	private String chapter;

	private String reference;

	@Column(nullable = false)
	private String designation;

	private String unit;

	private BigDecimal quantity;

	@Enumerated(EnumType.STRING)
	@Column(name = "price_mode", nullable = false)
	private PriceMode priceMode;

	@Column(name = "fixed_unit_price")
	private BigDecimal fixedUnitPrice;

	@Column(name = "text_price")
	private String textPrice;

	@Column(name = "is_option", nullable = false)
	private boolean option;

	@Column(name = "unit_cost")
	private BigDecimal unitCost;

	@Column(name = "unit_price")
	private BigDecimal unitPrice;

	private BigDecimal amount;

	@Column(name = "source_document_id")
	private Long sourceDocumentId;

	@Column(name = "source_sheet")
	private String sourceSheet;

	@Column(name = "source_row")
	private Integer sourceRow;

	@Column(name = "to_check", nullable = false)
	private boolean toCheck;

	@OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("position")
	@BatchSize(size = 50)
	private List<EstimateComponent> components = new ArrayList<>();

	protected EstimateItem() {
	}

	EstimateItem(ItemRequest request) {
		change(request);
	}

	/** An item read from a price schedule. */
	EstimateItem(ItemRequest request, Long documentId, String sheet, Integer row) {
		change(request);
		this.sourceDocumentId = documentId;
		this.sourceSheet = sheet;
		this.sourceRow = row;
	}

	/** A copy, with copies of its costs, for a new version. */
	EstimateItem copy() {
		EstimateItem copy = new EstimateItem(request(), sourceDocumentId, sourceSheet, sourceRow);
		components.forEach(component -> copy.add(component.copy()));
		return copy;
	}

	ItemRequest request() {
		return new ItemRequest(chapter, reference, designation, unit, quantity, priceMode, fixedUnitPrice, textPrice,
				option, toCheck);
	}

	final void change(ItemRequest request) {
		this.chapter = ItemRequest.clean(request.chapter());
		this.reference = ItemRequest.clean(request.reference());
		this.designation = request.designation().strip();
		this.unit = ItemRequest.clean(request.unit());
		this.quantity = request.quantity();
		this.priceMode = request.priceMode();
		this.fixedUnitPrice = priceMode == PriceMode.FIXED ? request.fixedUnitPrice() : null;
		this.textPrice = priceMode == PriceMode.TEXT ? ItemRequest.clean(request.textPrice()) : null;
		this.option = Boolean.TRUE.equals(request.option());
		this.toCheck = Boolean.TRUE.equals(request.toCheck());
	}

	void attach(Estimate estimate, int position) {
		this.estimate = estimate;
		this.position = position;
	}

	void setPosition(int position) {
		this.position = position;
	}

	void add(EstimateComponent component) {
		component.attach(this, components.size() + 1);
		components.add(component);
	}

	void remove(EstimateComponent component) {
		components.remove(component);
		for (int i = 0; i < components.size(); i++) {
			components.get(i).setPosition(i + 1);
		}
	}

	EstimateCalculator.Line line() {
		return new EstimateCalculator.Line(priceMode, quantity, fixedUnitPrice, option,
				components.stream().map(EstimateComponent::cost).toList());
	}

	void apply(EstimateCalculator.LineResult result) {
		this.unitCost = result.unitCost();
		this.unitPrice = result.unitPrice();
		this.amount = result.amount();
	}

	public Long getId() {
		return id;
	}

	Estimate getEstimate() {
		return estimate;
	}

	public int getPosition() {
		return position;
	}

	public String getChapter() {
		return chapter;
	}

	public String getReference() {
		return reference;
	}

	public String getDesignation() {
		return designation;
	}

	public String getUnit() {
		return unit;
	}

	public BigDecimal getQuantity() {
		return quantity;
	}

	public PriceMode getPriceMode() {
		return priceMode;
	}

	public BigDecimal getFixedUnitPrice() {
		return fixedUnitPrice;
	}

	public String getTextPrice() {
		return textPrice;
	}

	public boolean isOption() {
		return option;
	}

	public BigDecimal getUnitCost() {
		return unitCost;
	}

	public BigDecimal getUnitPrice() {
		return unitPrice;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public Long getSourceDocumentId() {
		return sourceDocumentId;
	}

	public String getSourceSheet() {
		return sourceSheet;
	}

	public Integer getSourceRow() {
		return sourceRow;
	}

	public boolean isToCheck() {
		return toCheck;
	}

	public List<EstimateComponent> getComponents() {
		return List.copyOf(components);
	}

}
