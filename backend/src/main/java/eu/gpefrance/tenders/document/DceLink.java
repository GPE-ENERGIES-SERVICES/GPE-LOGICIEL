package eu.gpefrance.tenders.document;

import java.time.Instant;

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

	@Column(name = "tender_id", nullable = false, updatable = false)
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

	protected DceLink() {
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

}
