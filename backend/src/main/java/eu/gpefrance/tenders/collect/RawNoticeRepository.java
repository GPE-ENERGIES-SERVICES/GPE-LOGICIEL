package eu.gpefrance.tenders.collect;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RawNoticeRepository extends JpaRepository<RawNotice, Long> {

	boolean existsBySourceAndExternalId(String source, String externalId);

}
