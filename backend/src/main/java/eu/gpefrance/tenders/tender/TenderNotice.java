package eu.gpefrance.tenders.tender;

import java.time.Instant;
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
 * One publication attached to a tender, with the deadline it announced.
 */
@Entity
@Table(name = "tender_notice")
public class TenderNotice {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tender_id")
	private Tender tender;

	@Column(name = "raw_notice_id", nullable = false, updatable = false)
	private Long rawNoticeId;

	@Column(name = "external_id", nullable = false, updatable = false)
	private String externalId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private NoticeKind kind;

	@Column(name = "published_at", updatable = false)
	private LocalDate publishedAt;

	@Column(updatable = false)
	private Instant deadline;

	@Column(updatable = false)
	private String details;

	protected TenderNotice() {
	}

	TenderNotice(Tender tender, Long rawNoticeId, String externalId, NoticeKind kind, LocalDate publishedAt,
			Instant deadline, String details) {
		this.tender = tender;
		this.rawNoticeId = rawNoticeId;
		this.externalId = externalId;
		this.kind = kind;
		this.publishedAt = publishedAt;
		this.deadline = deadline;
		this.details = details;
	}

	public Long getId() {
		return id;
	}

	public Tender getTender() {
		return tender;
	}

	public Long getRawNoticeId() {
		return rawNoticeId;
	}

	public String getExternalId() {
		return externalId;
	}

	public NoticeKind getKind() {
		return kind;
	}

	public LocalDate getPublishedAt() {
		return publishedAt;
	}

	public Instant getDeadline() {
		return deadline;
	}

	public String getDetails() {
		return details;
	}

}
