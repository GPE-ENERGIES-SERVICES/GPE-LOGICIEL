package eu.gpefrance.tenders.response;

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
 * A piece of the answer to a tender. What a user decided on it (label, what is
 * joined, not needed, checked) is never changed by a later analysis.
 */
@Entity
@Table(name = "response_piece")
class ResponsePiece {

	enum Category {

		APPLICATION, OFFER

	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "tender_id", nullable = false, updatable = false)
	private Long tenderId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private Category category;

	@Column(nullable = false)
	private String label;

	@Column(name = "label_key", nullable = false, updatable = false)
	private String labelKey;

	@Column(name = "from_analysis", nullable = false, updatable = false)
	private boolean fromAnalysis;

	@Column(name = "company_document_id")
	private Long companyDocumentId;

	@Column(name = "file_name")
	private String fileName;

	@Column(name = "storage_path")
	private String storagePath;

	@Column(name = "size_bytes")
	private Long sizeBytes;

	/** The text written in the application (HTML), null for a piece joined as a file. */
	private String content;

	/** AI while the draft of the AI was not changed, USER otherwise. */
	@Column(name = "content_origin")
	private String contentOrigin;

	/** What the AI could not find, one per line. */
	@Column(name = "content_missing")
	private String contentMissing;

	@Column(name = "not_needed", nullable = false)
	private boolean notNeeded;

	@Column(name = "checked_by")
	private String checkedBy;

	@Column(name = "checked_at")
	private Instant checkedAt;

	@Column(name = "updated_by")
	private String updatedBy;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected ResponsePiece() {
	}

	ResponsePiece(Long tenderId, Category category, String label, boolean fromAnalysis, Long companyDocumentId,
			String user, Instant now) {
		this.tenderId = tenderId;
		this.category = category;
		this.label = label;
		this.labelKey = keyOf(label);
		this.fromAnalysis = fromAnalysis;
		this.companyDocumentId = companyDocumentId;
		this.updatedBy = user;
		this.updatedAt = now;
	}

	static String keyOf(String label) {
		return label.strip().toLowerCase().replaceAll("\\s+", " ");
	}

	void change(String label, boolean notNeeded, Long companyDocumentId, String user, Instant now) {
		// What was checked is no longer what is joined.
		if (companyDocumentId == null ? this.companyDocumentId != null : !companyDocumentId.equals(this.companyDocumentId)) {
			uncheck();
		}
		this.label = label;
		this.notNeeded = notNeeded;
		this.companyDocumentId = companyDocumentId;
		touch(user, now);
	}

	void attach(String fileName, String storagePath, long sizeBytes, String user, Instant now) {
		this.fileName = fileName;
		this.storagePath = storagePath;
		this.sizeBytes = sizeBytes;
		uncheck();
		touch(user, now);
	}

	void detach(String user, Instant now) {
		this.fileName = null;
		this.storagePath = null;
		this.sizeBytes = null;
		uncheck();
		touch(user, now);
	}

	/** A text written in the application; its Word file is joined with {@link #attach}. */
	void write(String html, String origin, String missing, String user, Instant now) {
		this.content = html;
		this.contentOrigin = origin;
		this.contentMissing = missing;
		uncheck();
		touch(user, now);
	}

	String getContent() {
		return content;
	}

	String getContentOrigin() {
		return contentOrigin;
	}

	String getContentMissing() {
		return contentMissing;
	}

	void check(boolean checked, String user, Instant now) {
		this.checkedBy = checked ? user : null;
		this.checkedAt = checked ? now : null;
		touch(user, now);
	}

	private void uncheck() {
		checkedBy = null;
		checkedAt = null;
	}

	private void touch(String user, Instant now) {
		updatedBy = user;
		updatedAt = now;
	}

	Long getId() {
		return id;
	}

	Long getTenderId() {
		return tenderId;
	}

	Category getCategory() {
		return category;
	}

	String getLabel() {
		return label;
	}

	boolean isFromAnalysis() {
		return fromAnalysis;
	}

	Long getCompanyDocumentId() {
		return companyDocumentId;
	}

	String getFileName() {
		return fileName;
	}

	String getStoragePath() {
		return storagePath;
	}

	Long getSizeBytes() {
		return sizeBytes;
	}

	boolean isNotNeeded() {
		return notNeeded;
	}

	String getCheckedBy() {
		return checkedBy;
	}

	Instant getCheckedAt() {
		return checkedAt;
	}

}
