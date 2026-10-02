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

	protected CollectRun() {
	}

	public CollectRun(String source, CollectTrigger trigger, LocalDate sinceDate) {
		this.source = source;
		this.trigger = trigger;
		this.sinceDate = sinceDate;
		this.status = CollectStatus.RUNNING;
		this.startedAt = Instant.now();
	}

	public void succeed(int received, int created) {
		this.status = CollectStatus.SUCCESS;
		this.noticesReceived = received;
		this.noticesNew = created;
		this.finishedAt = Instant.now();
	}

	public void fail(int received, int created, String message) {
		this.status = CollectStatus.FAILED;
		this.noticesReceived = received;
		this.noticesNew = created;
		this.errorMessage = message == null || message.length() <= MAX_ERROR_LENGTH
				? message
				: message.substring(0, MAX_ERROR_LENGTH);
		this.finishedAt = Instant.now();
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

}
