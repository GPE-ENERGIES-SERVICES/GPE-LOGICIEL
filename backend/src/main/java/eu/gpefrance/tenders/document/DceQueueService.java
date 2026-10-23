package eu.gpefrance.tenders.document;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import eu.gpefrance.tenders.eligibility.EligibilityState;

/**
 * Turns the eligibility of a tender into the status of each of its links:
 * the business decision (a saved search matches) and the technical one (the
 * platform is allowed) stay apart. A link already retrieved, being retrieved,
 * failed for good or needing a person is never changed by an evaluation: its
 * files are kept whatever happens to the saved searches.
 */
@Service
public class DceQueueService {

	private static final Logger log = LoggerFactory.getLogger(DceQueueService.class);

	/** Statuses that a new eligibility may move to another one. */
	private static final Set<DceLinkStatus> OPEN = Set.of(DceLinkStatus.AVAILABLE, DceLinkStatus.NOT_ELIGIBLE,
			DceLinkStatus.ELIGIBLE_PENDING, DceLinkStatus.TEMPORARY_ERROR,
			DceLinkStatus.AUTOMATED_ACCESS_NOT_AUTHORIZED, DceLinkStatus.STORAGE_LIMIT_REACHED);

	private final DceLinkRepository links;

	private final DceLinkEventRepository events;

	private final DcePlatformPolicy policy;

	private final Clock clock;

	public DceQueueService(DceLinkRepository links, DceLinkEventRepository events, DcePlatformPolicy policy,
			Clock clock) {
		this.links = links;
		this.events = events;
		this.policy = policy;
		this.clock = clock;
	}

	/**
	 * Updates the links of the tender for its eligibility, in the transaction
	 * of the caller.
	 */
	@Transactional
	public void applyEligibility(Long tenderId, EligibilityState state, String reason) {
		Instant now = clock.instant();
		for (DceLink link : links.findByTenderIdOrderByFirstSeenAtAscIdAsc(tenderId)) {
			if (!OPEN.contains(link.getStatus())) {
				continue;
			}
			switch (state) {
				case NOT_ELIGIBLE -> evaluated(link, DceLinkStatus.NOT_ELIGIBLE, reason, now);
				case INDETERMINATE -> evaluated(link, DceLinkStatus.AVAILABLE,
						"Éligibilité indéterminée : " + reason + ". Rien n'est récupéré automatiquement.", now);
				case ELIGIBLE -> queueIfAllowed(link, now);
			}
		}
	}

	private void queueIfAllowed(DceLink link, Instant now) {
		DcePlatformPolicy.Decision decision = policy.decide(link);
		if (!decision.isAllowed()) {
			evaluated(link, DceLinkStatus.AUTOMATED_ACCESS_NOT_AUTHORIZED,
					decision.reason() + " Le lien reste disponible pour une action manuelle autorisée.", now);
			return;
		}
		if (link.getStatus() == DceLinkStatus.ELIGIBLE_PENDING || link.getStatus() == DceLinkStatus.TEMPORARY_ERROR
				|| link.getStatus() == DceLinkStatus.STORAGE_LIMIT_REACHED) {
			// Already queued, waiting for a new attempt, or for space freed by a person.
			return;
		}
		if (change(link, DceLinkStatus.ELIGIBLE_PENDING, "Consultation éligible, plateforme autorisée : en attente.",
				now)) {
			link.queue(now);
			log.info("DCE link {} of tender {} queued ({})", link.getId(), link.getTenderId(), link.getPlatform());
		}
	}

	/**
	 * The status an evaluation of the eligibility gives: recorded in the
	 * history only when the status changes; a new wording of the same status
	 * only updates the detail, so that evaluating again adds nothing.
	 */
	private void evaluated(DceLink link, DceLinkStatus status, String detail, Instant now) {
		if (link.getStatus() == status) {
			link.describe(detail);
		}
		else {
			change(link, status, detail, now);
		}
	}

	/**
	 * @return whether the status changed
	 */
	boolean change(DceLink link, DceLinkStatus status, String detail, Instant now) {
		DceLinkEvent event = link.changeStatus(status, detail, now);
		if (event == null) {
			return false;
		}
		events.save(event);
		return true;
	}

	/** Statuses after which a person may ask for a new attempt. */
	static final Set<DceLinkStatus> RETRIABLE = Set.of(DceLinkStatus.TEMPORARY_ERROR, DceLinkStatus.PERMANENT_ERROR,
			DceLinkStatus.LINK_UNAVAILABLE, DceLinkStatus.STORAGE_LIMIT_REACHED);

	/**
	 * A new attempt asked by a person, with the same controls as the
	 * automatic retrieval: only after a failure, for an eligible tender and an
	 * allowed platform. It never bypasses the saved searches nor the
	 * restrictions of the platform.
	 * @param eligible whether the tender of the link is eligible
	 * @throws DceRuleException when it may not be tried again
	 */
	@Transactional
	public DceLink retry(Long linkId, boolean eligible, String user) {
		DceLink link = links.findById(linkId).orElseThrow(() -> DceRuleException.notFound(linkId));
		if (!RETRIABLE.contains(link.getStatus())) {
			throw DceRuleException.refused("Seul un lien en erreur, indisponible ou bloqué par le quota peut être relancé.");
		}
		if (!eligible) {
			throw DceRuleException.refused("La consultation ne correspond à aucune veille active : rien n'est récupéré.");
		}
		DcePlatformPolicy.Decision decision = policy.decide(link);
		if (!decision.isAllowed()) {
			throw DceRuleException.refused(decision.reason());
		}
		Instant now = clock.instant();
		change(link, DceLinkStatus.ELIGIBLE_PENDING, "Nouvelle tentative demandée par " + user, now);
		link.queue(now);
		return link;
	}

	@Transactional(readOnly = true)
	public List<DceLinkEvent> history(Long linkId) {
		return events.findByDceLinkIdOrderByOccurredAtDescIdDesc(linkId);
	}

}
