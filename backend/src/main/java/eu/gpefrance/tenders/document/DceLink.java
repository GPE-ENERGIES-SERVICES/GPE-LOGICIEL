package eu.gpefrance.tenders.document;

import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * An address where the consultation file of a tender can be obtained, as
 * given by a notice of a source. Only a link: the files obtained from it are
 * a {@link Dossier}.
 */
@Entity
@Table(name = "dce_link")
public class DceLink {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "tender_id", nullable = false)
	private Long tenderId;

	/** Source of the notice that gave the address. */
	@Column(nullable = false, updatable = false)
	private String source;

	@Column(nullable = false, updatable = false)
	private String url;

	/** Host of the address, without "www.", such as marches-publics.info. */
	@Column(updatable = false)
	private String platform;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private DceLinkStatus status;

	@Column(name = "status_detail")
	private String statusDetail;

	@Column(name = "first_seen_at", nullable = false, updatable = false)
	private Instant firstSeenAt;

	@Column(name = "last_seen_at", nullable = false)
	private Instant lastSeenAt;

	/** Last attempt to retrieve the files, if any. */
	@Column(name = "checked_at")
	private Instant checkedAt;

	/** Attempts of retrieval since the link was last queued. */
	@Column(nullable = false)
	private int attempts;

	/** When a pending or temporarily failed retrieval may be tried. */
	@Column(name = "next_attempt_at")
	private Instant nextAttemptAt;

	/** When a worker took the link; a lease older than the configured one is released. */
	@Column(name = "locked_at")
	private Instant lockedAt;

	@Column(name = "locked_by")
	private String lockedBy;

	@Column(name = "fetched_at")
	private Instant fetchedAt;

	/** Files kept by the last retrieval. */
	@Column(name = "file_count")
	private Integer fileCount;

	protected DceLink() {
	}

	/**
	 * Changes the status and its detail, and returns the change to record, or
	 * null when nothing changes.
	 */
	DceLinkEvent changeStatus(DceLinkStatus newStatus, String detail, Instant now) {
		if (newStatus == status && Objects.equals(detail, statusDetail)) {
			return null;
		}
		DceLinkEvent event = new DceLinkEvent(id, now, status, newStatus, detail);
		this.status = newStatus;
		this.statusDetail = detail;
		return event;
	}

	/** A new wording of the same status, without a change to record. */
	void describe(String detail) {
		this.statusDetail = detail;
	}

	/** Queued again from scratch: attempts start over. */
	void queue(Instant now) {
		this.attempts = 0;
		this.nextAttemptAt = now;
	}

	void retryAt(Instant when) {
		this.nextAttemptAt = when;
	}

	void attempted(Instant now) {
		this.attempts++;
		this.checkedAt = now;
	}

	void fetched(int files, Instant now) {
		this.fetchedAt = now;
		this.fileCount = files;
	}

	void lock(String worker, Instant now) {
		this.lockedBy = worker;
		this.lockedAt = now;
	}

	void unlock() {
		this.lockedAt = null;
		this.lockedBy = null;
	}

	DceLink(Long tenderId, String source, String url, String platform, Instant now) {
		this.tenderId = tenderId;
		this.source = source;
		this.url = url;
		this.platform = platform;
		this.status = DceLinkStatus.AVAILABLE;
		this.firstSeenAt = now;
		this.lastSeenAt = now;
	}

	/** Moves the link to another tender, as when a duplicate is merged into it. */
	public void moveTo(Long newTenderId) {
		this.tenderId = newTenderId;
	}

	void seenAgain(Instant now) {
		lastSeenAt = now;
	}

	public Long getId() {
		return id;
	}

	public Long getTenderId() {
		return tenderId;
	}

	public String getSource() {
		return source;
	}

	public String getUrl() {
		return url;
	}

	public String getPlatform() {
		return platform;
	}

	public DceLinkStatus getStatus() {
		return status;
	}

	public String getStatusDetail() {
		return statusDetail;
	}

	public Instant getFirstSeenAt() {
		return firstSeenAt;
	}

	public Instant getLastSeenAt() {
		return lastSeenAt;
	}

	public Instant getCheckedAt() {
		return checkedAt;
	}

	public int getAttempts() {
		return attempts;
	}

	public Instant getNextAttemptAt() {
		return nextAttemptAt;
	}

	public Instant getLockedAt() {
		return lockedAt;
	}

	public Instant getFetchedAt() {
		return fetchedAt;
	}

	public Integer getFileCount() {
		return fileCount;
	}

}
