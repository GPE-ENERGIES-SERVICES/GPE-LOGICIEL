package eu.gpefrance.tenders.alert;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import eu.gpefrance.tenders.tender.MarketType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A saved search (« veille ») of a user: the open tenders meeting all its
 * criteria. Within a criterion, the values are alternatives: one keyword, one
 * department, one market type, one activity or one CPV prefix is enough. Only
 * its owner reads or changes it. An active saved search makes the
 * consultation files of the tenders it matches eligible for retrieval.
 */
@Entity
@Table(name = "alert")
public class Alert {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private Long userId;

	@Column(nullable = false)
	private String name;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(nullable = false)
	private List<String> keywords = new ArrayList<>();

	private String region;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(nullable = false)
	private List<String> departments = new ArrayList<>();

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "market_types", nullable = false)
	private List<String> marketTypes = new ArrayList<>();

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(nullable = false)
	private List<String> activities = new ArrayList<>();

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "cpv_prefixes", nullable = false)
	private List<String> cpvPrefixes = new ArrayList<>();

	@Column(name = "min_amount")
	private BigDecimal minAmount;

	@Column(name = "max_amount")
	private BigDecimal maxAmount;

	@Column(nullable = false)
	private boolean active;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "last_viewed_at")
	private Instant lastViewedAt;

	protected Alert() {
	}

	public Alert(Long userId, AlertCriteria criteria, boolean active, Instant now) {
		this.userId = userId;
		this.createdAt = now;
		replace(criteria, active, now);
	}

	public final void replace(AlertCriteria criteria, boolean active, Instant now) {
		this.name = criteria.name();
		this.keywords = new ArrayList<>(criteria.keywords());
		this.region = criteria.region();
		this.departments = new ArrayList<>(criteria.departments());
		this.marketTypes = new ArrayList<>(criteria.marketTypes().stream().map(Enum::name).toList());
		this.activities = new ArrayList<>(criteria.activities());
		this.cpvPrefixes = new ArrayList<>(criteria.cpvPrefixes());
		this.minAmount = criteria.minAmount();
		this.maxAmount = criteria.maxAmount();
		this.active = active;
		this.updatedAt = now;
	}

	public void markViewed(Instant now) {
		this.lastViewedAt = now;
	}

	/** The criteria, as they are evaluated against a tender. */
	public AlertCriteria criteria() {
		return new AlertCriteria(name, keywords, region, departments,
				marketTypes.stream().map(MarketType::valueOf).toList(), activities, cpvPrefixes, minAmount, maxAmount);
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public String getName() {
		return name;
	}

	public boolean isActive() {
		return active;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public Instant getLastViewedAt() {
		return lastViewedAt;
	}

}
