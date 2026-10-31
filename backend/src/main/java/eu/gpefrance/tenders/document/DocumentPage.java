package eu.gpefrance.tenders.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Text of one page of a document, the unit cited as a source.
 */
@Entity
@Table(name = "document_page")
public class DocumentPage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "document_id", nullable = false, updatable = false)
	private Long documentId;

	@Column(name = "page_number", nullable = false, updatable = false)
	private int pageNumber;

	@Column(nullable = false, updatable = false)
	private String text;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private PageMethod method;

	/** Words read by OCR, with position and confidence, as JSON; null for the other pages. */
	@Column(updatable = false)
	private String words;

	protected DocumentPage() {
	}

	public DocumentPage(Long documentId, int pageNumber, String text, PageMethod method) {
		this.documentId = documentId;
		this.pageNumber = pageNumber;
		this.text = text;
		this.method = method;
	}

	/**
	 * A page read by OCR, with its words.
	 */
	public DocumentPage(Long documentId, int pageNumber, String text, String words) {
		this(documentId, pageNumber, text, PageMethod.OCR);
		this.words = words;
	}

	/**
	 * The same page for another document of the same content.
	 */
	public DocumentPage copyFor(Long otherDocumentId) {
		DocumentPage copy = new DocumentPage(otherDocumentId, pageNumber, text, method);
		copy.words = words;
		return copy;
	}

	public Long getId() {
		return id;
	}

	public Long getDocumentId() {
		return documentId;
	}

	public int getPageNumber() {
		return pageNumber;
	}

	public String getText() {
		return text;
	}

	public PageMethod getMethod() {
		return method;
	}

	public String getWords() {
		return words;
	}

}
