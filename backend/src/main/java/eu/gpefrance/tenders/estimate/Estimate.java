package eu.gpefrance.tenders.estimate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.hibernate.annotations.BatchSize;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * A version of the estimate (« chiffrage ») of a tender, or of one of its
 * lots: its items, the sale coefficients and the VAT rate it uses, and its
 * totals, computed again at every change. Only a draft may change.
 */
@Entity
@Table(name = "estimate")
public class Estimate {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "tender_id", nullable = false, updatable = false)
	private Long tenderId;

	@Column(name = "lot_number", updatable = false)
	private String lotNumber;

	@Column(nullable = false, updatable = false)
	private int version;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private EstimateStatus status;

	private String label;

	@Column(name = "vat_rate", nullable = false)
	private BigDecimal vatRate;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "estimate_coefficient", joinColumns = @JoinColumn(name = "estimate_id"))
	@MapKeyEnumerated(EnumType.STRING)
	@MapKeyColumn(name = "component_type")
	@Column(name = "coefficient", nullable = false)
	private Map<ComponentType, BigDecimal> coefficients = new EnumMap<>(ComponentType.class);

	@Column(name = "total_cost")
	private BigDecimal totalCost;

	@Column(name = "total_ht", nullable = false)
	private BigDecimal totalHt = BigDecimal.ZERO;

	@Column(name = "total_vat", nullable = false)
	private BigDecimal totalVat = BigDecimal.ZERO;

	@Column(name = "total_ttc", nullable = false)
	private BigDecimal totalTtc = BigDecimal.ZERO;

	@Column(name = "options_ht", nullable = false)
	private BigDecimal optionsHt = BigDecimal.ZERO;

	@Column(name = "incomplete_items", nullable = false)
	private int incompleteItems;

	@Column(name = "created_by", updatable = false)
	private String createdBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_by")
	private String updatedBy;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "validated_by")
	private String validatedBy;

	@Column(name = "validated_at")
	private Instant validatedAt;

	@OneToMany(mappedBy = "estimate", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("position")
	@BatchSize(size = 50)
	private List<EstimateItem> items = new ArrayList<>();

	protected Estimate() {
	}

	Estimate(Long tenderId, String lotNumber, int version, String label, BigDecimal vatRate,
			Map<ComponentType, BigDecimal> coefficients, String user, Instant now) {
		this.tenderId = tenderId;
		this.lotNumber = lotNumber;
		this.version = version;
		this.status = EstimateStatus.DRAFT;
		this.createdBy = user;
		this.createdAt = now;
		change(label, vatRate, coefficients, user, now);
	}

	/**
	 * @param coefficients the coefficients to change; the types not given keep
	 * theirs, 1 for a new estimate
	 */
	final void change(String label, BigDecimal vatRate, Map<ComponentType, BigDecimal> coefficients, String user,
			Instant now) {
		this.label = label;
		this.vatRate = vatRate;
		for (ComponentType type : ComponentType.values()) {
			this.coefficients.putIfAbsent(type, BigDecimal.ONE);
		}
		this.coefficients.putAll(coefficients);
		touched(user, now);
	}

	void touched(String user, Instant now) {
		this.updatedBy = user;
		this.updatedAt = now;
	}

	boolean isDraft() {
		return status == EstimateStatus.DRAFT;
	}

	/** Frozen from now on, by a user. */
	void validate(String user, Instant now) {
		status = EstimateStatus.VALIDATED;
		validatedBy = user;
		validatedAt = now;
		touched(user, now);
	}

	/** A later version was validated. */
	void supersede() {
		status = EstimateStatus.SUPERSEDED;
	}

	/**
	 * A new draft holding the same items and costs, as they were: nothing is
	 * read again from the rates or the offers.
	 */
	Estimate copy(int newVersion, String user, Instant now) {
		Estimate copy = new Estimate(tenderId, lotNumber, newVersion, label, vatRate, coefficients, user, now);
		items.forEach(item -> copy.add(item.copy()));
		copy.recalculate();
		return copy;
	}

	void add(EstimateItem item) {
		item.attach(this, items.size() + 1);
		items.add(item);
	}

	void remove(EstimateItem item) {
		items.remove(item);
		renumber();
	}

	/** Puts the items in this order: every item exactly once. */
	void reorder(List<Long> itemIds) {
		List<EstimateItem> ordered = itemIds.stream()
			.map(id -> items.stream().filter(item -> id.equals(item.getId())).findFirst().orElse(null))
			.toList();
		if (ordered.size() != items.size() || ordered.contains(null) || ordered.stream().distinct().count() != items.size()) {
			throw EstimateRuleException.invalid("L'ordre doit citer chaque poste du chiffrage une seule fois.");
		}
		items.clear();
		items.addAll(ordered);
		renumber();
	}

	private void renumber() {
		for (int i = 0; i < items.size(); i++) {
			items.get(i).setPosition(i + 1);
		}
	}

	/** Computes again every figure of the items and the totals. */
	void recalculate() {
		EstimateCalculator.Result result = EstimateCalculator.compute(
				items.stream().map(EstimateItem::line).toList(), coefficients, vatRate);
		for (int i = 0; i < items.size(); i++) {
			items.get(i).apply(result.lines().get(i));
		}
		totalCost = result.totalCost();
		totalHt = result.totalHt();
		totalVat = result.totalVat();
		totalTtc = result.totalTtc();
		optionsHt = result.optionsHt();
		incompleteItems = result.incompleteItems();
	}

	public Long getId() {
		return id;
	}

	public Long getTenderId() {
		return tenderId;
	}

	public String getLotNumber() {
		return lotNumber;
	}

	public int getVersion() {
		return version;
	}

	public EstimateStatus getStatus() {
		return status;
	}

	public String getLabel() {
		return label;
	}

	public BigDecimal getVatRate() {
		return vatRate;
	}

	public Map<ComponentType, BigDecimal> getCoefficients() {
		return Map.copyOf(coefficients);
	}

	public BigDecimal getTotalCost() {
		return totalCost;
	}

	public BigDecimal getTotalHt() {
		return totalHt;
	}

	public BigDecimal getTotalVat() {
		return totalVat;
	}

	public BigDecimal getTotalTtc() {
		return totalTtc;
	}

	public BigDecimal getOptionsHt() {
		return optionsHt;
	}

	public int getIncompleteItems() {
		return incompleteItems;
	}

	public String getCreatedBy() {
		return createdBy;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public String getUpdatedBy() {
		return updatedBy;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public String getValidatedBy() {
		return validatedBy;
	}

	public Instant getValidatedAt() {
		return validatedAt;
	}

	public List<EstimateItem> getItems() {
		return List.copyOf(items);
	}

}
