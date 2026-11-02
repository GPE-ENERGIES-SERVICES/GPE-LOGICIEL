package eu.gpefrance.tenders.document;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import eu.gpefrance.tenders.tender.Tender;
import eu.gpefrance.tenders.tender.TenderNotFoundException;
import eu.gpefrance.tenders.tender.TenderRepository;

/**
 * What changes a consultation file analysed without any tender as a whole:
 * its name, its deletion, its attachment to a tender. Neither of the last two
 * happens while a reading or an analysis is running on it.
 */
@Service
public class DceReviewService {

	private final DceReviewRepository reviews;

	private final DossierRepository dossiers;

	private final TenderRepository tenders;

	private final FileStorage storage;

	private final JdbcTemplate jdbc;

	private final TransactionTemplate transaction;

	public DceReviewService(DceReviewRepository reviews, DossierRepository dossiers, TenderRepository tenders,
			FileStorage storage, JdbcTemplate jdbc, TransactionTemplate transaction) {
		this.reviews = reviews;
		this.dossiers = dossiers;
		this.tenders = tenders;
		this.storage = storage;
		this.jdbc = jdbc;
		this.transaction = transaction;
	}

	public DceReview rename(Long reviewId, String name) {
		return transaction.execute(status -> {
			DceReview review = reviews.findById(reviewId).orElseThrow(DceReviewService::notFound);
			review.rename(name);
			return review;
		});
	}

	/**
	 * Deletes the analysis, its dossiers, what was read of them and their
	 * files. The tokens already paid stay in the monthly ceiling.
	 */
	public void delete(Long reviewId) {
		transaction.executeWithoutResult(status -> {
			lockIdle(reviewId);
			jdbc.update("""
					INSERT INTO ai_call (kind, model, input_tokens, output_tokens, created_at)
					SELECT 'DELETED_DCE_REVIEW', model, coalesce(input_tokens, 0), coalesce(output_tokens, 0), created_at
					FROM analysis WHERE review_id = ? AND (input_tokens IS NOT NULL OR output_tokens IS NOT NULL)
					""", reviewId);
			jdbc.update("DELETE FROM extracted_field WHERE analysis_id IN (SELECT id FROM analysis WHERE review_id = ?)",
					reviewId);
			jdbc.update("DELETE FROM analysis_document WHERE analysis_id IN (SELECT id FROM analysis WHERE review_id = ?)",
					reviewId);
			jdbc.update("DELETE FROM analysis WHERE review_id = ?", reviewId);
			jdbc.update("""
					DELETE FROM document_page WHERE document_id IN
					(SELECT d.id FROM document d JOIN dossier s ON s.id = d.dossier_id WHERE s.review_id = ?)
					""", reviewId);
			jdbc.update("DELETE FROM document WHERE dossier_id IN (SELECT id FROM dossier WHERE review_id = ?)", reviewId);
			jdbc.update("DELETE FROM dossier WHERE review_id = ?", reviewId);
			jdbc.update("DELETE FROM dce_review WHERE id = ?", reviewId);
		});
		// After the commit: a failed deletion keeps its files.
		DossierService.deleteQuietly(storage.resolve("reviews/" + reviewId));
	}

	/**
	 * Hands the dossiers and analyses over to a tender, as its latest versions;
	 * the review no longer exists. Its files stay where they are, the documents
	 * keep their path.
	 */
	public void attach(Long reviewId, Long tenderId) {
		transaction.executeWithoutResult(status -> {
			lockIdle(reviewId);
			Tender tender = tenders.findById(tenderId).orElseThrow(() -> new TenderNotFoundException(tenderId));
			if (tender.getMergedIntoId() != null) {
				throw new ResponseStatusException(HttpStatus.CONFLICT,
						"Cet appel d'offres a été fusionné avec un autre : rattachez le DCE à ce dernier.");
			}
			jdbc.update("UPDATE dossier SET tender_id = ?, review_id = NULL, version = version + ? WHERE review_id = ?",
					tenderId, dossiers.findLastVersion(tenderId), reviewId);
			jdbc.update("UPDATE analysis SET tender_id = ?, review_id = NULL WHERE review_id = ?", tenderId, reviewId);
			jdbc.update("DELETE FROM dce_review WHERE id = ?", reviewId);
		});
	}

	private void lockIdle(Long reviewId) {
		if (jdbc.queryForList("SELECT id FROM dce_review WHERE id = ? FOR UPDATE", Long.class, reviewId).isEmpty()) {
			throw notFound();
		}
		Long running = jdbc.queryForObject("""
				SELECT (SELECT count(*) FROM dossier WHERE review_id = ? AND status IN ('PENDING', 'PROCESSING'))
				     + (SELECT count(*) FROM analysis WHERE review_id = ? AND status IN ('PENDING', 'PROCESSING', 'RETRY_WAITING'))
				""", Long.class, reviewId, reviewId);
		if (running != null && running > 0) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Une lecture ou une analyse est en cours : attendez sa fin ou annulez-la.");
		}
	}

	private static ResponseStatusException notFound() {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "Analyse de DCE introuvable.");
	}

}
