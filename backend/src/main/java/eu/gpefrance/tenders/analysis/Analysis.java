package eu.gpefrance.tenders.analysis;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * One extraction run on a dossier, kept with the model and the version of the
 * instructions used so that runs can be compared.
 */
@Entity
@Table(name = "analysis")
public class Analysis {

	private static final int MAX_ERROR_LENGTH = 2000;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "tender_id", nullable = false, updatable = false)
	private Long tenderId;

	@Column(name = "dossier_id", nullable = false, updatable = false)
	private Long dossierId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AnalysisStatus status;

	@Column(nullable = false, updatable = false)
	private String model;

	@Column(name = "prompt_version", nullable = false, updatable = false)
	private String promptVersion;

	@Column(name = "input_fingerprint", nullable = false, updatable = false)
	private String inputFingerprint;

	@ElementCollection
	@CollectionTable(name = "analysis_document", joinColumns = @JoinColumn(name = "analysis_id"))
	@Column(name = "document_id")
	private Set<Long> documentIds = new HashSet<>();

	@Column(name = "input_tokens")
	private Integer inputTokens;

	@Column(name = "output_tokens")
	private Integer outputTokens;

	@Column(name = "error_message")
	private String errorMessage;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "finished_at")
	private Instant finishedAt;

	@Column(name = "validated_by")
	private String validatedBy;

	@Column(name = "validated_at")
	private Instant validatedAt;

	@Column(updatable = false)
	private String provider;

	/** Who asked for it; null when started by itself after the reading of the dossier. */
	@Column(name = "requested_by", updatable = false)
	private String requestedBy;

	@Enumerated(EnumType.STRING)
	@Column(name = "error_kind")
	private FailureKind errorKind;

	@Column(nullable = false)
	private int attempts;

	@Column(name = "next_attempt_at")
	private Instant nextAttemptAt;

	@Column(name = "locked_at")
	private Instant lockedAt;

	@Column(name = "locked_by")
	private String lockedBy;

	@OneToMany(mappedBy = "analysis", cascade = CascadeType.ALL)
	@OrderBy("id")
	private List<ExtractedField> fields = new ArrayList<>();

	protected Analysis() {
	}

	/**
	 * @param documentIds the documents given to the model
	 * @param inputFingerprint identifies the documents, model and instructions
	 */
	public Analysis(Long tenderId, Long dossierId, String model, String promptVersion, Collection<Long> documentIds,
			String inputFingerprint, Instant now) {
		this(tenderId, dossierId, null, model, promptVersion, documentIds, inputFingerprint, null, now);
	}

	/**
	 * A queued analysis.
	 * @param requestedBy the user who asked for it, null when automatic
	 */
	public Analysis(Long tenderId, Long dossierId, String provider, String model, String promptVersion,
			Collection<Long> documentIds, String inputFingerprint, String requestedBy, Instant now) {
		this.tenderId = tenderId;
		this.dossierId = dossierId;
		this.provider = provider;
		this.model = model;
		this.promptVersion = promptVersion;
		this.documentIds.addAll(documentIds);
		this.inputFingerprint = inputFingerprint;
		this.requestedBy = requestedBy;
		this.createdAt = now;
		this.nextAttemptAt = now;
		this.status = AnalysisStatus.PENDING;
	}

	public ExtractedField addField(String fieldKey, String value, String quote, Long documentId, Integer pageNumber,
			FieldState state) {
		ExtractedField field = new ExtractedField(this, fieldKey, value, quote, documentId, pageNumber, state);
		fields.add(field);
		return field;
	}

	/**
	 * Taken by a worker, just before the call to the provider.
	 */
	void claim(String worker, Instant now) {
		status = AnalysisStatus.PROCESSING;
		attempts++;
		lockedBy = worker;
		lockedAt = now;
		nextAttemptAt = null;
	}

	/**
	 * A temporary failure: tried again at the given time.
	 */
	void retryAt(FailureKind kind, String message, Instant next) {
		status = AnalysisStatus.RETRY_WAITING;
		errorKind = kind;
		errorMessage = truncate(message);
		nextAttemptAt = next;
		unlock();
	}

	/**
	 * Interrupted while in progress (stop, lease expired): queued again.
	 */
	void release(Instant now) {
		status = AnalysisStatus.PENDING;
		nextAttemptAt = now;
		unlock();
	}

	/**
	 * Only an analysis not sent yet can be cancelled: a call in progress is
	 * paid anyway and its answer kept.
	 */
	void cancel(String user, Instant now) {
		if (status != AnalysisStatus.PENDING && status != AnalysisStatus.RETRY_WAITING) {
			throw new AnalysisRejectedException("Seule une analyse en attente peut être annulée.");
		}
		status = AnalysisStatus.CANCELLED;
		errorKind = null;
		errorMessage = "Annulée par " + user;
		nextAttemptAt = null;
		finishedAt = now;
	}

	boolean isUnfinished() {
		return status == AnalysisStatus.PENDING || status == AnalysisStatus.PROCESSING
				|| status == AnalysisStatus.RETRY_WAITING;
	}

	private void unlock() {
		lockedBy = null;
		lockedAt = null;
	}

	private static String truncate(String message) {
		return message == null || message.length() <= MAX_ERROR_LENGTH ? message
				: message.substring(0, MAX_ERROR_LENGTH);
	}

	/**
	 * Tokens are recorded to measure the cost of each dossier, even when the
	 * answer cannot be used.
	 */
	public void recordUsage(int inputTokens, int outputTokens) {
		this.inputTokens = inputTokens;
		this.outputTokens = outputTokens;
	}

	public void finish(int inputTokens, int outputTokens, Instant now) {
		recordUsage(inputTokens, outputTokens);
		status = AnalysisStatus.DONE;
		errorKind = null;
		errorMessage = null;
		finishedAt = now;
		unlock();
	}

	public void fail(String message, Instant now) {
		fail(FailureKind.UNEXPECTED, message, now);
	}

	public void fail(FailureKind kind, String message, Instant now) {
		status = AnalysisStatus.FAILED;
		errorKind = kind;
		errorMessage = truncate(message);
		nextAttemptAt = null;
		finishedAt = now;
		unlock();
	}

	/**
	 * A user states that the sheet has been checked.
	 */
	public void validate(String user, Instant now) {
		if (status != AnalysisStatus.DONE) {
			throw new AnalysisRejectedException("Seule une analyse terminée peut être validée.");
		}
		validatedBy = user;
		validatedAt = now;
	}

	void cancelValidation() {
		validatedBy = null;
		validatedAt = null;
	}

	public Long getId() {
		return id;
	}

	public String getValidatedBy() {
		return validatedBy;
	}

	public Instant getValidatedAt() {
		return validatedAt;
	}

	public Long getTenderId() {
		return tenderId;
	}

	public Long getDossierId() {
		return dossierId;
	}

	public AnalysisStatus getStatus() {
		return status;
	}

	public String getModel() {
		return model;
	}

	public String getProvider() {
		return provider;
	}

	public String getRequestedBy() {
		return requestedBy;
	}

	public FailureKind getErrorKind() {
		return errorKind;
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

	public String getLockedBy() {
		return lockedBy;
	}

	public String getPromptVersion() {
		return promptVersion;
	}

	public String getInputFingerprint() {
		return inputFingerprint;
	}

	public Set<Long> getDocumentIds() {
		return Collections.unmodifiableSet(documentIds);
	}

	public Integer getInputTokens() {
		return inputTokens;
	}

	public Integer getOutputTokens() {
		return outputTokens;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getFinishedAt() {
		return finishedAt;
	}

	public List<ExtractedField> getFields() {
		return Collections.unmodifiableList(fields);
	}

}
