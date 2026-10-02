package eu.gpefrance.tenders.collect;

import java.time.Instant;
import java.time.LocalDate;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A notice exactly as received from its source. It is never modified, so the
 * conversion to the common format can be replayed at any time.
 */
@Entity
@Table(name = "raw_notice")
public class RawNotice {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, updatable = false)
	private String source;

	@Column(name = "external_id", nullable = false, updatable = false)
	private String externalId;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, updatable = false)
	private String payload;

	@Column(name = "published_at", updatable = false)
	private LocalDate publishedAt;

	@Column(name = "collected_at", nullable = false, updatable = false)
	private Instant collectedAt;

	protected RawNotice() {
	}

	public RawNotice(String source, String externalId, String payload, LocalDate publishedAt) {
		this.source = source;
		this.externalId = externalId;
		this.payload = payload;
		this.publishedAt = publishedAt;
		this.collectedAt = Instant.now();
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

	public String getPayload() {
		return payload;
	}

	public LocalDate getPublishedAt() {
		return publishedAt;
	}

	public Instant getCollectedAt() {
		return collectedAt;
	}

}
