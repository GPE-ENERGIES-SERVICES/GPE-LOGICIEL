package eu.gpefrance.tenders.tender;

import java.io.Serializable;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * A tender followed by a user. The follow is personal; the follow-up status
 * of the tender is shared by the team.
 */
@Entity
@Table(name = "tender_follow")
@IdClass(TenderFollow.Key.class)
public class TenderFollow {

	@Id
	@Column(name = "user_id")
	private Long userId;

	@Id
	@Column(name = "tender_id")
	private Long tenderId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected TenderFollow() {
	}

	TenderFollow(Long userId, Long tenderId, Instant now) {
		this.userId = userId;
		this.tenderId = tenderId;
		this.createdAt = now;
	}

	public record Key(Long userId, Long tenderId) implements Serializable {

		public Key() {
			this(null, null);
		}

	}

}
