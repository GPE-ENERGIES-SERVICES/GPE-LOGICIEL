package eu.gpefrance.tenders.duplicate;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DuplicateDecisionRepository extends JpaRepository<DuplicateDecision, Long> {

	List<DuplicateDecision> findAllByOrderByDecidedAtDescIdDesc();

	List<DuplicateDecision> findByKindAndUndoneAtIsNull(DuplicateDecision.Kind kind);

}
