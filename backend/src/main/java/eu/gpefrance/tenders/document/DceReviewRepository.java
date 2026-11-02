package eu.gpefrance.tenders.document;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DceReviewRepository extends JpaRepository<DceReview, Long> {

	List<DceReview> findAllByOrderByIdDesc();

}
