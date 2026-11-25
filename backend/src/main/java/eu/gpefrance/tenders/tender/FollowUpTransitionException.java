package eu.gpefrance.tenders.tender;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * A change of follow-up status the rules do not allow, such as signing a
 * contract never won.
 */
public class FollowUpTransitionException extends ErrorResponseException {

	private static final Map<FollowUpStatus, String> LABELS = Map.of(FollowUpStatus.NEW, "Nouveau",
			FollowUpStatus.TO_STUDY, "À analyser", FollowUpStatus.RESPONDING, "En préparation",
			FollowUpStatus.SUBMITTED, "Réponse envoyée", FollowUpStatus.DISMISSED, "Écarté",
			FollowUpStatus.AWARDED, "Attribué", FollowUpStatus.SIGNED, "Signé", FollowUpStatus.REJECTED, "Refusé");

	FollowUpTransitionException(FollowUpStatus from, FollowUpStatus to) {
		super(HttpStatus.CONFLICT, ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
				String.format("Passage de « %s » à « %s » impossible.", LABELS.get(from), LABELS.get(to))), null);
	}

}
