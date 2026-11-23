package eu.gpefrance.tenders.analysis;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AnalysisRepository extends JpaRepository<Analysis, Long> {

	@EntityGraph(attributePaths = { "fields", "documentIds" })
	Optional<Analysis> findWithFieldsById(Long id);

	List<Analysis> findByDossierIdOrderByIdDesc(Long dossierId);

	List<Analysis> findByTenderIdOrderByIdDesc(Long tenderId);

	List<Analysis> findByStatusIn(Collection<AnalysisStatus> statuses);

	List<Analysis> findByTenderIdAndKindOrderByIdDesc(Long tenderId, AnalysisKind kind);

	Optional<Analysis> findFirstByTenderIdAndKindAndStatusInOrderByIdDesc(Long tenderId, AnalysisKind kind,
			Collection<AnalysisStatus> statuses);

	Optional<Analysis> findFirstByTenderIdAndKindAndInputFingerprintAndStatusOrderByIdDesc(Long tenderId,
			AnalysisKind kind, String fingerprint, AnalysisStatus status);

	List<Analysis> findAllByOrderByIdDesc(Pageable pageable);

	/** The latest finished analyses of a kind, to tell how long they take. */
	List<Analysis> findTop20ByKindAndStatusAndStartedAtNotNullOrderByIdDesc(AnalysisKind kind,
			AnalysisStatus status);

	long countByStatus(AnalysisStatus status);

	/**
	 * Tokens consumed by every analysis, failed ones included: they were paid.
	 */
	@Query("select coalesce(sum(a.inputTokens), 0) as input, coalesce(sum(a.outputTokens), 0) as output from Analysis a")
	TokenTotals sumTokens();

	/**
	 * Tokens of the analyses created since the given time, failed ones
	 * included.
	 */
	// The ledger of every call (ai_call), analyses included: each call counted once.
	@Query(nativeQuery = true, value = """
			select coalesce(sum(input_tokens), 0) as input, coalesce(sum(output_tokens), 0) as output
			from ai_call where created_at >= :since""")
	TokenTotals sumTokensSince(Instant since);

	/** What the calls made since the given time cost, in euros, as priced when they were made. */
	@Query(nativeQuery = true, value = "select coalesce(sum(cost), 0) from ai_call where created_at >= :since")
	BigDecimal sumCostSince(Instant since);

	/** The same tokens by what they were spent on: kind of analysis or of assistance. */
	@Query(nativeQuery = true, value = """
			select kind as kind, coalesce(sum(input_tokens), 0) as input, coalesce(sum(output_tokens), 0) as output,
			       sum(cost) as cost
			from ai_call where created_at >= :since
			group by kind order by kind""")
	List<KindTokens> sumTokensByKindSince(Instant since);

	interface KindTokens extends TokenTotals {

		String getKind();

		/** Null when no call of the kind was priced. */
		BigDecimal getCost();

	}

	List<Analysis> findByStatus(AnalysisStatus status);

	interface TokenTotals {

		long getInput();

		long getOutput();

	}

}
