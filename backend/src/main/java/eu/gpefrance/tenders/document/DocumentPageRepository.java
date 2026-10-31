package eu.gpefrance.tenders.document;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface DocumentPageRepository extends JpaRepository<DocumentPage, Long> {

	Optional<DocumentPage> findByDocumentIdAndPageNumber(Long documentId, int pageNumber);

	List<DocumentPage> findByDocumentIdOrderByPageNumber(Long documentId);

	/**
	 * Length of the text of each document and its pages with text, and how
	 * many of them come from OCR, without loading the text.
	 */
	@Query("""
			select p.documentId as documentId, sum(length(p.text)) as characters,
			  sum(case when length(trim(p.text)) > 0 then 1 else 0 end) as pagesWithText,
			  sum(case when p.method = eu.gpefrance.tenders.document.PageMethod.OCR and length(trim(p.text)) > 0
			    then 1 else 0 end) as pagesByOcr
			from DocumentPage p where p.documentId in :documentIds group by p.documentId""")
	List<TextCount> countText(Collection<Long> documentIds);

	interface TextCount {

		Long getDocumentId();

		Long getCharacters();

		Long getPagesWithText();

		Long getPagesByOcr();

	}

	@Modifying
	@Query("delete from DocumentPage p where p.documentId = :documentId")
	void deleteByDocumentId(Long documentId);

}
