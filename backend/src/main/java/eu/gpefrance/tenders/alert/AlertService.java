package eu.gpefrance.tenders.alert;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import eu.gpefrance.tenders.eligibility.EligibilityService;
import eu.gpefrance.tenders.eligibility.TenderEligibilityRepository;
import eu.gpefrance.tenders.tender.Regions;

/**
 * The saved searches of each user. Only its owner reads, changes or deletes a
 * saved search; the owner is the logged-in user, never one given by the
 * interface. Every change marks a new revision of the saved searches: the
 * eligibility of the consultation files is then evaluated again in the
 * background. Deleting a saved search never deletes a tender, a document, a
 * link nor a file already retrieved.
 */
@Service
public class AlertService {

	/** Types of works known by the interface (ACTIVITY_LABELS). */
	static final Set<String> ACTIVITIES = Set.of("ELECTRICITY", "PHOTOVOLTAIC", "EV_CHARGING", "HEATING",
			"AIR_CONDITIONING", "ENERGY_RENOVATION", "PUBLIC_LIGHTING", "NETWORKS", "VRD", "MAINTENANCE", "STUDIES");

	private static final Logger log = LoggerFactory.getLogger(AlertService.class);

	private final AlertRepository alerts;

	private final TenderEligibilityRepository eligibilities;

	private final EligibilityService eligibility;

	private final JdbcTemplate jdbc;

	private final TransactionTemplate transaction;

	private final Clock clock;

	public AlertService(AlertRepository alerts, TenderEligibilityRepository eligibilities,
			EligibilityService eligibility, JdbcTemplate jdbc, TransactionTemplate transaction, Clock clock) {
		this.alerts = alerts;
		this.eligibilities = eligibilities;
		this.eligibility = eligibility;
		this.jdbc = jdbc;
		this.transaction = transaction;
		this.clock = clock;
	}

	public List<AlertResponse> list(Long userId) {
		return alerts.findByUserIdOrderByIdAsc(userId).stream().map(this::response).toList();
	}

	public AlertResponse get(Long id, Long userId) {
		return response(owned(id, userId));
	}

	public AlertResponse create(AlertRequest request, Long userId) {
		AlertCriteria criteria = validate(request);
		Alert alert = transaction.execute(status -> {
			Alert saved = alerts.save(new Alert(userId, criteria, request.active() == null || request.active(),
					clock.instant()));
			changed();
			return saved;
		});
		log.info("Saved search {} created by user {}", alert.getId(), userId);
		return response(alert);
	}

	public AlertResponse update(Long id, AlertRequest request, Long userId) {
		AlertCriteria criteria = validate(request);
		Alert alert = transaction.execute(status -> {
			Alert found = owned(id, userId);
			found.replace(criteria, request.active() == null ? found.isActive() : request.active(), clock.instant());
			changed();
			return alerts.save(found);
		});
		log.info("Saved search {} changed by user {} (active: {})", id, userId, alert.isActive());
		return response(alert);
	}

	public void delete(Long id, Long userId) {
		transaction.executeWithoutResult(status -> {
			alerts.delete(owned(id, userId));
			changed();
		});
		log.info("Saved search {} deleted by user {}", id, userId);
	}

	public AlertResponse markViewed(Long id, Long userId) {
		Alert alert = transaction.execute(status -> {
			Alert found = owned(id, userId);
			found.markViewed(clock.instant());
			return alerts.save(found);
		});
		return response(alert);
	}

	/** The saved searches whose owner is the user: those of another user are not found. */
	private Alert owned(Long id, Long userId) {
		return alerts.findByIdAndUserId(id, userId).orElseThrow(AlertRuleException::notFound);
	}

	/**
	 * A new revision of the saved searches; the evaluation starts once the
	 * change is committed.
	 */
	private void changed() {
		jdbc.update("UPDATE alert_revision SET revision = revision + 1 WHERE id = 1");
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					eligibility.requestEvaluation();
				}
			});
		}
		else {
			eligibility.requestEvaluation();
		}
	}

	static AlertCriteria validate(AlertRequest request) {
		String region = request.region() == null || request.region().isBlank() ? null : request.region().strip();
		if (region != null && !Regions.isKnown(region)) {
			throw AlertRuleException.invalid("Région inconnue : " + region);
		}
		List<String> activities = request.activitiesOrEmpty();
		List<String> unknown = activities.stream().filter(activity -> !ACTIVITIES.contains(activity)).toList();
		if (!unknown.isEmpty()) {
			throw AlertRuleException.invalid("Type de travaux inconnu : " + String.join(", ", unknown));
		}
		if (request.minAmount() != null && request.maxAmount() != null
				&& request.minAmount().compareTo(request.maxAmount()) > 0) {
			throw AlertRuleException.invalid("Le montant minimum dépasse le montant maximum.");
		}
		AlertCriteria criteria = new AlertCriteria(request.name().strip(), request.keywordsOrEmpty(), region,
				request.departmentsOrEmpty(), request.marketTypesOrEmpty(), activities, request.cpvPrefixesOrEmpty(),
				request.minAmount(), request.maxAmount());
		if (!criteria.restricts()) {
			throw AlertRuleException.invalid(
					"Une veille doit avoir au moins un critère (mot-clé, zone, type de marché, travaux, CPV ou montant).");
		}
		return criteria;
	}

	private AlertResponse response(Alert alert) {
		long matches = eligibilities.countMatching(alert.getId());
		// Published after the day of the last visit; all of them before a first visit.
		long news = alert.getLastViewedAt() == null ? matches
				: eligibilities.countMatchingPublishedAfter(alert.getId(),
						LocalDate.ofInstant(alert.getLastViewedAt(), clock.getZone()));
		return AlertResponse.from(alert, matches, news, eligibilities.countIndeterminate(alert.getId()),
				eligibility.isRunning() || eligibility.outOfDate() > 0);
	}

}
