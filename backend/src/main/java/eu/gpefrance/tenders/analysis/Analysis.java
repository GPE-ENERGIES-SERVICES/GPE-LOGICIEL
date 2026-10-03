package eu.gpefrance.tenders.analysis;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

	@Column(name = "dossier_id", nullable = false, updatable = false)
	private Long dossierId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AnalysisStatus status;

	@Column(nullable = false, updatable = false)
	private String model;

	@Column(name = "prompt_version", nullable = false, updatable = false)
	private String promptVersion;

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

	@OneToMany(mappedBy = "analysis", cascade = CascadeType.ALL)
	@OrderBy("id")
	private List<ExtractedField> fields = new ArrayList<>();

	protected Analysis() {
	}

	public Analysis(Long dossierId, String model, String promptVersion, Instant now) {
		this.dossierId = dossierId;
		this.model = model;
		this.promptVersion = promptVersion;
		this.createdAt = now;
		this.status = AnalysisStatus.PENDING;
	}

	public ExtractedField addField(String fieldKey, String value, String quote, Long documentId, Integer pageNumber,
			FieldState state) {
		ExtractedField field = new ExtractedField(this, fieldKey, value, quote, documentId, pageNumber, state);
		fields.add(field);
		return field;
	}

	public void startProcessing() {
		status = AnalysisStatus.PROCESSING;
		errorMessage = null;
	}

	/**
	 * Tokens are recorded to measure the cost of each dossier.
	 */
	public void finish(int inputTokens, int outputTokens, Instant now) {
		this.inputTokens = inputTokens;
		this.outputTokens = outputTokens;
		status = AnalysisStatus.DONE;
		finishedAt = now;
	}

	public void fail(String message, Instant now) {
		status = AnalysisStatus.FAILED;
		errorMessage = message == null || message.length() <= MAX_ERROR_LENGTH ? message
				: message.substring(0, MAX_ERROR_LENGTH);
		finishedAt = now;
	}

	public Long getId() {
		return id;
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

	public String getPromptVersion() {
		return promptVersion;
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
