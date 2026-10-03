package eu.gpefrance.tenders.document;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface DocumentPageRepository extends JpaRepository<DocumentPage, Long> {

	Optional<DocumentPage> findByDocumentIdAndPageNumber(Long documentId, int pageNumber);

	List<DocumentPage> findByDocumentIdOrderByPageNumber(Long documentId);

	@Modifying
	@Query("delete from DocumentPage p where p.documentId = :documentId")
	void deleteByDocumentId(Long documentId);

}
