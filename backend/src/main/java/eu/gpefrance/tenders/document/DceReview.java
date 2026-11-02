package eu.gpefrance.tenders.document;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A consultation file analysed on its own, without any tender of the database:
 * its dossiers and analyses are those of a tender, under the name given by the
 * user.
 */
@Entity
@Table(name = "dce_review")
public class DceReview {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	@Column(name = "created_by", updatable = false)
	private String createdBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected DceReview() {
	}

	public DceReview(String name, String createdBy, Instant now) {
		this.name = name;
		this.createdBy = createdBy;
		this.createdAt = now;
	}

	void rename(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getCreatedBy() {
		return createdBy;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
