package eu.gpefrance.tenders.collect;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Journal entry of one collection: when it ran, from which publication date,
 * how many notices it brought and why it failed if it did.
 */
@Entity
@Table(name = "collect_run")
public class CollectRun {

	private static final int MAX_ERROR_LENGTH = 2000;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, updatable = false)
	private String source;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private CollectTrigger trigger;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private CollectStatus status;

	@Column(name = "since_date", nullable = false, updatable = false)
	private LocalDate sinceDate;

	/** Last publication day asked; null for the runs made before it was recorded. */
	@Column(name = "until_date", updatable = false)
	private LocalDate untilDate;

	@Column(name = "started_at", nullable = false, updatable = false)
	private Instant startedAt;

	@Column(name = "finished_at")
	private Instant finishedAt;

	@Column(name = "notices_received", nullable = false)
	private int noticesReceived;

	@Column(name = "notices_new", nullable = false)
	private int noticesNew;

	@Column(name = "error_message")
	private String errorMessage;

	/** Notices received that were already stored: duplicates skipped. */
	@Column(name = "notices_known", nullable = false)
	private int noticesKnown;

	/** Notices received without identifier, not stored. */
	@Column(name = "notices_rejected", nullable = false)
	private int noticesRejected;

	@Column(name = "tenders_created", nullable = false)
	private int tendersCreated;

	@Column(name = "tenders_updated", nullable = false)
	private int tendersUpdated;

	@Column(name = "notices_ignored", nullable = false)
	private int noticesIgnored;

	@Column(name = "notice_errors", nullable = false)
	private int noticeErrors;

	protected CollectRun() {
	}

	public CollectRun(String source, CollectTrigger trigger, LocalDate sinceDate, Instant startedAt) {
		this(source, trigger, sinceDate, null, startedAt);
	}

	/**
	 * @param untilDate last publication day the run asks for
	 */
	public CollectRun(String source, CollectTrigger trigger, LocalDate sinceDate, LocalDate untilDate,
			Instant startedAt) {
		this.source = source;
		this.trigger = trigger;
		this.sinceDate = sinceDate;
		this.untilDate = untilDate;
		this.status = CollectStatus.RUNNING;
		this.startedAt = startedAt;
	}

	public void succeed(int received, int created, Instant finishedAt) {
		this.status = CollectStatus.SUCCESS;
		this.noticesReceived = received;
		this.noticesNew = created;
		this.finishedAt = finishedAt;
	}

	public void fail(int received, int created, String message, Instant finishedAt) {
		this.status = CollectStatus.FAILED;
		this.noticesReceived = received;
		this.noticesNew = created;
		this.errorMessage = message == null || message.length() <= MAX_ERROR_LENGTH
				? message
				: message.substring(0, MAX_ERROR_LENGTH);
		this.finishedAt = finishedAt;
	}

	/**
	 * Completes the run once its notices are converted to tenders. The
	 * conversion also covers the notices of the source left pending by
	 * earlier runs.
	 * @param rejected notices received without identifier
	 */
	public void report(int rejected, NormalizationReport normalization, Instant finishedAt) {
		this.noticesRejected = rejected;
		this.noticesKnown = Math.max(0, noticesReceived - noticesNew - rejected);
		this.tendersCreated = normalization.created();
		this.tendersUpdated = normalization.updated();
		this.noticesIgnored = normalization.ignored();
		this.noticeErrors = normalization.errors();
		this.finishedAt = finishedAt;
	}

	public Long getId() {
		return id;
	}

	public String getSource() {
		return source;
	}

	public CollectTrigger getTrigger() {
		return trigger;
	}

	public CollectStatus getStatus() {
		return status;
	}

	public LocalDate getSinceDate() {
		return sinceDate;
	}

	public LocalDate getUntilDate() {
		return untilDate;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getFinishedAt() {
		return finishedAt;
	}

	public int getNoticesReceived() {
		return noticesReceived;
	}

	public int getNoticesNew() {
		return noticesNew;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public int getNoticesKnown() {
		return noticesKnown;
	}

	public int getNoticesRejected() {
		return noticesRejected;
	}

	public int getTendersCreated() {
		return tendersCreated;
	}

	public int getTendersUpdated() {
		return tendersUpdated;
	}

	public int getNoticesIgnored() {
		return noticesIgnored;
	}

	public int getNoticeErrors() {
		return noticeErrors;
	}

}
