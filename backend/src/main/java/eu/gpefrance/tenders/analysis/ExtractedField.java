package eu.gpefrance.tenders.analysis;

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
 * One answer of the extraction grid and its source: the quote, the document
 * and the page. A field not found has no value and no source.
 */
@Entity
@Table(name = "extracted_field")
public class ExtractedField {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "analysis_id")
	private Analysis analysis;

	@Column(name = "field_key", nullable = false, updatable = false)
	private String fieldKey;

	@Column(updatable = false)
	private String value;

	@Column(updatable = false)
	private String quote;

	@Column(name = "document_id", updatable = false)
	private Long documentId;

	@Column(name = "page_number", updatable = false)
	private Integer pageNumber;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private FieldState state;

	protected ExtractedField() {
	}

	ExtractedField(Analysis analysis, String fieldKey, String value, String quote, Long documentId,
			Integer pageNumber, FieldState state) {
		this.analysis = analysis;
		this.fieldKey = fieldKey;
		this.value = value;
		this.quote = quote;
		this.documentId = documentId;
		this.pageNumber = pageNumber;
		this.state = state;
	}

	public Long getId() {
		return id;
	}

	public String getFieldKey() {
		return fieldKey;
	}

	public String getValue() {
		return value;
	}

	public String getQuote() {
		return quote;
	}

	public Long getDocumentId() {
		return documentId;
	}

	public Integer getPageNumber() {
		return pageNumber;
	}

	public FieldState getState() {
		return state;
	}

}
