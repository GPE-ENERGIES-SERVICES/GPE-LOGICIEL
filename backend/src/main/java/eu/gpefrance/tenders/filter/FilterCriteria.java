package eu.gpefrance.tenders.filter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import eu.gpefrance.tenders.tender.MarketType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Filtering criteria of the company. There is a single row. An empty list
 * or a {@code null} value means the criterion is not used.
 */
@Entity
@Table(name = "filter_criteria")
public class FilterCriteria {

	static final long SINGLE_ID = 1L;

	@Id
	private Long id;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(nullable = false)
	private List<String> departments = new ArrayList<>();

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "market_types", nullable = false)
	private List<String> marketTypes = new ArrayList<>();

	@Column(name = "min_days_before_deadline")
	private Integer minDaysBeforeDeadline;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "cpv_prefixes", nullable = false)
	private List<String> cpvPrefixes = new ArrayList<>();

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(nullable = false)
	private List<String> keywords = new ArrayList<>();

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "excluded_keywords", nullable = false)
	private List<String> excludedKeywords = new ArrayList<>();

	@Column(name = "min_amount")
	private BigDecimal minAmount;

	@Column(name = "max_amount")
	private BigDecimal maxAmount;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "updated_by")
	private String updatedBy;

	protected FilterCriteria() {
	}

	public FilterCriteria(List<String> departments, List<MarketType> marketTypes, Integer minDaysBeforeDeadline,
			List<String> cpvPrefixes, List<String> keywords, List<String> excludedKeywords, BigDecimal minAmount,
			BigDecimal maxAmount, Instant now) {
		this.id = SINGLE_ID;
		replace(departments, marketTypes, minDaysBeforeDeadline, cpvPrefixes, keywords, excludedKeywords, minAmount,
				maxAmount, null, now);
	}

	public final void replace(List<String> departments, List<MarketType> marketTypes, Integer minDaysBeforeDeadline,
			List<String> cpvPrefixes, List<String> keywords, List<String> excludedKeywords, BigDecimal minAmount,
			BigDecimal maxAmount, String updatedBy, Instant now) {
		this.departments = new ArrayList<>(departments);
		this.marketTypes = new ArrayList<>(marketTypes.stream().map(Enum::name).toList());
		this.minDaysBeforeDeadline = minDaysBeforeDeadline;
		this.cpvPrefixes = new ArrayList<>(cpvPrefixes);
		this.keywords = new ArrayList<>(keywords);
		this.excludedKeywords = new ArrayList<>(excludedKeywords);
		this.minAmount = minAmount;
		this.maxAmount = maxAmount;
		this.updatedBy = updatedBy;
		this.updatedAt = now;
	}

	public List<String> getDepartments() {
		return Collections.unmodifiableList(departments);
	}

	public List<MarketType> getMarketTypes() {
		return marketTypes.stream().map(MarketType::valueOf).toList();
	}

	public Integer getMinDaysBeforeDeadline() {
		return minDaysBeforeDeadline;
	}

	public List<String> getCpvPrefixes() {
		return Collections.unmodifiableList(cpvPrefixes);
	}

	public List<String> getKeywords() {
		return Collections.unmodifiableList(keywords);
	}

	public List<String> getExcludedKeywords() {
		return Collections.unmodifiableList(excludedKeywords);
	}

	public BigDecimal getMinAmount() {
		return minAmount;
	}

	public BigDecimal getMaxAmount() {
		return maxAmount;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public String getUpdatedBy() {
		return updatedBy;
	}

}
