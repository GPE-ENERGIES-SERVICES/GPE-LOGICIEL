package eu.gpefrance.tenders.document;

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
 * One upload of the consultation file of a tender.
 */
@Entity
@Table(name = "dossier")
public class Dossier {

	private static final int MAX_ERROR_LENGTH = 2000;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "tender_id", nullable = false, updatable = false)
	private Long tenderId;

	@Column(nullable = false, updatable = false)
	private int version;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private DossierStatus status;

	@Column(name = "error_message")
	private String errorMessage;

	@Column(name = "uploaded_by", updatable = false)
	private String uploadedBy;

	@Column(name = "uploaded_at", nullable = false, updatable = false)
	private Instant uploadedAt;

	@Column(name = "processed_at")
	private Instant processedAt;

	@OneToMany(mappedBy = "dossier", cascade = CascadeType.ALL)
	@OrderBy("fileName")
	private List<Document> documents = new ArrayList<>();

	protected Dossier() {
	}

	public Dossier(Long tenderId, int version, String uploadedBy, Instant now) {
		this.tenderId = tenderId;
		this.version = version;
		this.uploadedBy = uploadedBy;
		this.uploadedAt = now;
		this.status = DossierStatus.PENDING;
	}

	public Document addDocument(String fileName, String storagePath, long sizeBytes, String sha256,
			PieceType pieceType) {
		Document document = new Document(this, fileName, storagePath, sizeBytes, sha256, pieceType);
		documents.add(document);
		return document;
	}

	public void startProcessing() {
		status = DossierStatus.PROCESSING;
		errorMessage = null;
	}

	public void finish(Instant now) {
		status = DossierStatus.DONE;
		processedAt = now;
	}

	public void fail(String message, Instant now) {
		status = DossierStatus.FAILED;
		errorMessage = message == null || message.length() <= MAX_ERROR_LENGTH ? message
				: message.substring(0, MAX_ERROR_LENGTH);
		processedAt = now;
	}

	public Long getId() {
		return id;
	}

	public Long getTenderId() {
		return tenderId;
	}

	public int getVersion() {
		return version;
	}

	public DossierStatus getStatus() {
		return status;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public String getUploadedBy() {
		return uploadedBy;
	}

	public Instant getUploadedAt() {
		return uploadedAt;
	}

	public Instant getProcessedAt() {
		return processedAt;
	}

	public List<Document> getDocuments() {
		return Collections.unmodifiableList(documents);
	}

}
