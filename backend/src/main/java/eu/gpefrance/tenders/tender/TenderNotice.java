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

	/** Source that published the notice: the tender's own, or another one publishing the same consultation. */
	@Column(nullable = false, updatable = false)
	private String source;

	@Column(name = "raw_notice_id", nullable = false, updatable = false)
	private Long rawNoticeId;

	@Column(name = "external_id", nullable = false, updatable = false)
	private String externalId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private NoticeKind kind;

	@Column(name = "published_at", updatable = false)
	private LocalDate publishedAt;

	@Column(updatable = false)
	private Instant deadline;

	@Column(updatable = false)
	private String details;

	protected TenderNotice() {
	}

	TenderNotice(Tender tender, String source, Long rawNoticeId, String externalId, NoticeKind kind,
			LocalDate publishedAt, Instant deadline, String details) {
		this.tender = tender;
		this.source = source;
		this.rawNoticeId = rawNoticeId;
		this.externalId = externalId;
		this.kind = kind;
		this.publishedAt = publishedAt;
		this.deadline = deadline;
		this.details = details;
	}

	/**
	 * Gives the notice the kind it has when read again by the rules of today,
	 * as when a duplicate made before a fix of its source is merged.
	 */
	public void relabel(NoticeKind newKind) {
		this.kind = newKind;
	}

	void moveTo(Tender newTender) {
		this.tender = newTender;
	}

	public Long getId() {
		return id;
	}

	public Tender getTender() {
		return tender;
	}

	public String getSource() {
		return source;
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
