package eu.gpefrance.tenders.tender;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TenderNoticeRepository extends JpaRepository<TenderNotice, Long> {

	/**
	 * The notices of the source with the given identifiers, attached to a
	 * tender of the source, the first attached first: a notice never refers
	 * to another source, and a notice of the source kept in the history of a
	 * tender of another source is not one of its tenders.
	 */
	List<TenderNotice> findByTenderSourceAndSourceAndExternalIdInOrderByIdAsc(String tenderSource, String source,
			Collection<String> externalIds);

	Optional<TenderNotice> findByRawNoticeId(Long rawNoticeId);

}
