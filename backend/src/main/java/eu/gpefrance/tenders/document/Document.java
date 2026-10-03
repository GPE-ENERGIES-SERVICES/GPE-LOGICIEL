package eu.gpefrance.tenders.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One file of a dossier. The file stays in the storage directory; when it is
 * a Word document, a PDF copy is made so it can be read page by page.
 */
@Entity
@Table(name = "document")
public class Document {

	private static final int MAX_DETAIL_LENGTH = 2000;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "dossier_id")
	private Dossier dossier;

	@Column(name = "file_name", nullable = false, updatable = false)
	private String fileName;

	@Column(name = "storage_path", nullable = false, updatable = false)
	private String storagePath;

	@Column(name = "pdf_path")
	private String pdfPath;

	@Column(name = "size_bytes", nullable = false, updatable = false)
	private long sizeBytes;

	@Column(nullable = false, updatable = false)
	private String sha256;

	@Enumerated(EnumType.STRING)
	@Column(name = "piece_type", nullable = false)
	private PieceType pieceType;

	@Column(name = "piece_type_corrected", nullable = false)
	private boolean pieceTypeCorrected;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private DocumentState state;

	@Column(name = "state_detail")
	private String stateDetail;

	@Column(name = "page_count")
	private Integer pageCount;

	protected Document() {
	}

	Document(Dossier dossier, String fileName, String storagePath, long sizeBytes, String sha256,
			PieceType pieceType) {
		this.dossier = dossier;
		this.fileName = fileName;
		this.storagePath = storagePath;
		this.sizeBytes = sizeBytes;
		this.sha256 = sha256;
		this.pieceType = pieceType;
		this.state = DocumentState.PENDING;
	}

	/**
	 * A type chosen by a user replaces the one guessed from the file name.
	 */
	public void correctPieceType(PieceType pieceType) {
		this.pieceType = pieceType;
		this.pieceTypeCorrected = true;
	}

	/**
	 * @param pdfPath where the readable PDF is: the file itself, or its
	 * conversion for a Word document
	 */
	public void markRead(int pageCount, int pagesWithoutText, String pdfPath) {
		this.pageCount = pageCount;
		this.pdfPath = pdfPath;
		if (pagesWithoutText > 0) {
			this.state = DocumentState.SCANNED;
			this.stateDetail = pagesWithoutText + " page(s) sans texte sur " + pageCount
					+ " : document probablement scanné, ces pages ne sont pas analysées";
		}
		else {
			this.state = DocumentState.READ;
			this.stateDetail = null;
		}
	}

	public void resetForRetry() {
		this.state = DocumentState.PENDING;
		this.stateDetail = null;
	}

	public void markNotAnalyzed(String reason) {
		this.state = DocumentState.NOT_ANALYZED;
		this.stateDetail = reason;
	}

	public void markFailed(String reason) {
		this.state = DocumentState.FAILED;
		this.stateDetail = reason == null || reason.length() <= MAX_DETAIL_LENGTH ? reason
				: reason.substring(0, MAX_DETAIL_LENGTH);
	}

	public Long getId() {
		return id;
	}

	public Dossier getDossier() {
		return dossier;
	}

	public String getFileName() {
		return fileName;
	}

	public String getStoragePath() {
		return storagePath;
	}

	public String getPdfPath() {
		return pdfPath;
	}

	public long getSizeBytes() {
		return sizeBytes;
	}

	public String getSha256() {
		return sha256;
	}

	public PieceType getPieceType() {
		return pieceType;
	}

	public boolean isPieceTypeCorrected() {
		return pieceTypeCorrected;
	}

	public DocumentState getState() {
		return state;
	}

	public String getStateDetail() {
		return stateDetail;
	}

	public Integer getPageCount() {
		return pageCount;
	}

}
