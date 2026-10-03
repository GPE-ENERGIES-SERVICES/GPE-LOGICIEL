package eu.gpefrance.tenders.analysis;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ExtractedFieldRepository extends JpaRepository<ExtractedField, Long> {

	/**
	 * Answers of the finished analyses, counted by field, state and decision.
	 */
	@Query("""
			select f.fieldKey as key, f.state as state, f.decision as decision, count(f) as total
			from ExtractedField f
			where f.analysis.status = eu.gpefrance.tenders.analysis.AnalysisStatus.DONE
			group by f.fieldKey, f.state, f.decision
			""")
	List<FieldCount> countAnswers();

	interface FieldCount {

		String getKey();

		FieldState getState();

		FieldDecision getDecision();

		long getTotal();

	}

}
