package eu.gpefrance.tenders.tender;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TenderNoticeRepository extends JpaRepository<TenderNotice, Long> {

	List<TenderNotice> findByExternalIdIn(Collection<String> externalIds);

}
