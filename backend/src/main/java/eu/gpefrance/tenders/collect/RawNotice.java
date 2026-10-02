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
 * A notice exactly as received from its source. It is inserted by
 * {@link RawNoticeRepository#insertIfAbsent} and its content is never
 * modified, so the conversion to the common format can be replayed at any
 * time. Only the outcome of that conversion is recorded on it.
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

	@Column(name = "normalized_at")
	private Instant normalizedAt;

	@Column(name = "normalization_note")
	private String normalizationNote;

	protected RawNotice() {
	}

	/**
	 * @param note why the notice did not create or update a tender, if so
	 */
	public void markNormalized(String note, Instant now) {
		this.normalizedAt = now;
		this.normalizationNote = note;
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

	public Instant getNormalizedAt() {
		return normalizedAt;
	}

	public String getNormalizationNote() {
		return normalizationNote;
	}

}
