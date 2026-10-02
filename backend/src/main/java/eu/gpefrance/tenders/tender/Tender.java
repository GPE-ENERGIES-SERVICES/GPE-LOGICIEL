package eu.gpefrance.tenders.tender;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * A call for tenders in the common format. It groups the initial notice and
 * the notices published afterwards (rectifications, award, cancellation).
 */
@Entity
@Table(name = "tender")
public class Tender {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, updatable = false)
	private String source;

	@Column(name = "external_id", nullable = false, updatable = false)
	private String externalId;

	@Column(nullable = false)
	private String title;

	private String description;

	private String buyer;

	private String reference;

	private String location;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(nullable = false)
	private List<String> departments = new ArrayList<>();

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "market_types", nullable = false)
	private List<String> marketTypes = new ArrayList<>();

	@Enumerated(EnumType.STRING)
	@Column(name = "procedure_type")
	private ProcedureType procedureType;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "cpv_codes", nullable = false)
	private List<String> cpvCodes = new ArrayList<>();

	@Column(name = "estimated_amount")
	private BigDecimal estimatedAmount;

	@Column(name = "published_at")
	private LocalDate publishedAt;

	private Instant deadline;

	@Column(name = "notice_url")
	private String noticeUrl;

	@Column(name = "platform_url")
	private String platformUrl;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TenderStatus status;

	private Boolean retained;

	@Column(name = "filter_reason")
	private String filterReason;

	@Column(name = "filtered_at")
	private Instant filteredAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "follow_up_status", nullable = false)
	private FollowUpStatus followUpStatus = FollowUpStatus.NEW;

	@Column(name = "follow_up_comment")
	private String followUpComment;

	@Column(name = "follow_up_by")
	private String followUpBy;

	@Column(name = "follow_up_at")
	private Instant followUpAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@OneToMany(mappedBy = "tender", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("position")
	private List<Lot> lots = new ArrayList<>();

	@OneToMany(mappedBy = "tender", cascade = CascadeType.ALL)
	@OrderBy("publishedAt, id")
	private List<TenderNotice> notices = new ArrayList<>();

	protected Tender() {
	}

	public Tender(String source, String externalId, TenderDetails details, Instant now) {
		this.source = source;
		this.externalId = externalId;
		this.status = TenderStatus.ACTIVE;
		this.createdAt = now;
		this.title = details.title();
		update(details, now);
	}

	/**
	 * Applies the information of a more recent notice. Information the notice
	 * does not give is kept: a rectification never erases what was known.
	 */
	public void update(TenderDetails details, Instant now) {
		title = firstNonNull(details.title(), title);
		description = firstNonNull(details.description(), description);
		buyer = firstNonNull(details.buyer(), buyer);
		reference = firstNonNull(details.reference(), reference);
		location = firstNonNull(details.location(), location);
		if (!details.departments().isEmpty()) {
			departments = new ArrayList<>(details.departments());
		}
		if (!details.marketTypes().isEmpty()) {
			marketTypes = new ArrayList<>(details.marketTypes().stream().map(Enum::name).toList());
		}
		procedureType = firstNonNull(details.procedureType(), procedureType);
		if (!details.cpvCodes().isEmpty()) {
			cpvCodes = new ArrayList<>(details.cpvCodes());
		}
		estimatedAmount = firstNonNull(details.estimatedAmount(), estimatedAmount);
		publishedAt = firstNonNull(publishedAt, details.publishedAt());
		deadline = firstNonNull(details.deadline(), deadline);
		noticeUrl = firstNonNull(noticeUrl, details.noticeUrl());
		platformUrl = firstNonNull(details.platformUrl(), platformUrl);
		if (!details.lots().isEmpty()) {
			lots.clear();
			for (int i = 0; i < details.lots().size(); i++) {
				lots.add(new Lot(this, i + 1, details.lots().get(i)));
			}
		}
		updatedAt = now;
	}

	public TenderNotice addNotice(Long rawNoticeId, String externalId, NoticeKind kind, LocalDate publishedAt,
			Instant deadline, String details) {
		TenderNotice notice = new TenderNotice(this, rawNoticeId, externalId, kind, publishedAt, deadline, details);
		notices.add(notice);
		return notice;
	}

	/**
	 * A new deadline announced by a rectification prevails; the former one
	 * stays visible in the notice history.
	 */
	public void changeDeadline(Instant newDeadline, Instant now) {
		deadline = newDeadline;
		updatedAt = now;
	}

	/**
	 * Records the outcome of the filtering criteria and its reason.
	 */
	public void applyFilter(boolean retained, String reason, Instant now) {
		this.retained = retained;
		this.filterReason = reason;
		this.filteredAt = now;
	}

	public void followUp(FollowUpStatus status, String comment, String user, Instant now) {
		this.followUpStatus = status;
		this.followUpComment = comment;
		this.followUpBy = user;
		this.followUpAt = now;
	}

	public void cancel(Instant now) {
		changeStatus(TenderStatus.CANCELLED, now);
	}

	/**
	 * Closes an active tender, after its award or once its deadline has passed.
	 */
	public void close(Instant now) {
		if (status == TenderStatus.ACTIVE) {
			changeStatus(TenderStatus.CLOSED, now);
		}
	}

	private void changeStatus(TenderStatus newStatus, Instant now) {
		status = newStatus;
		updatedAt = now;
	}

	private static <T> T firstNonNull(T first, T second) {
		return first != null ? first : second;
	}

	public Long getId() {
		return id;
	}

	public String getSource() {
		return source;
	}

	public String getExternalId() {
		return externalId;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public String getBuyer() {
		return buyer;
	}

	public String getReference() {
		return reference;
	}

	public String getLocation() {
		return location;
	}

	public List<String> getDepartments() {
		return Collections.unmodifiableList(departments);
	}

	public List<MarketType> getMarketTypes() {
		return marketTypes.stream().map(MarketType::valueOf).toList();
	}

	public ProcedureType getProcedureType() {
		return procedureType;
	}

	public List<String> getCpvCodes() {
		return Collections.unmodifiableList(cpvCodes);
	}

	public BigDecimal getEstimatedAmount() {
		return estimatedAmount;
	}

	public LocalDate getPublishedAt() {
		return publishedAt;
	}

	public Instant getDeadline() {
		return deadline;
	}

	public String getNoticeUrl() {
		return noticeUrl;
	}

	public String getPlatformUrl() {
		return platformUrl;
	}

	public TenderStatus getStatus() {
		return status;
	}

	/**
	 * @return {@code null} while the tender has not been filtered yet
	 */
	public Boolean getRetained() {
		return retained;
	}

	public String getFilterReason() {
		return filterReason;
	}

	public Instant getFilteredAt() {
		return filteredAt;
	}

	public FollowUpStatus getFollowUpStatus() {
		return followUpStatus;
	}

	public String getFollowUpComment() {
		return followUpComment;
	}

	public String getFollowUpBy() {
		return followUpBy;
	}

	public Instant getFollowUpAt() {
		return followUpAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public List<Lot> getLots() {
		return Collections.unmodifiableList(lots);
	}

	public List<TenderNotice> getNotices() {
		return Collections.unmodifiableList(notices);
	}

}
