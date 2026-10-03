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

	protected DocumentPage() {
	}

	public DocumentPage(Long documentId, int pageNumber, String text, PageMethod method) {
		this.documentId = documentId;
		this.pageNumber = pageNumber;
		this.text = text;
		this.method = method;
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

}
