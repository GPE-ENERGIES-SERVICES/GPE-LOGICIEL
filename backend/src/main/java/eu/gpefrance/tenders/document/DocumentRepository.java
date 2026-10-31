package eu.gpefrance.tenders.document;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, Long> {

	/**
	 * The latest other document of the same content in the given state: its
	 * reading can be taken rather than done again.
	 */
	Optional<Document> findFirstBySha256AndStateAndIdNotOrderByIdDesc(String sha256, DocumentState state, Long id);

}
