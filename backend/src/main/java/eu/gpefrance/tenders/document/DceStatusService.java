package eu.gpefrance.tenders.document;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import eu.gpefrance.tenders.alert.Alert;
import eu.gpefrance.tenders.alert.AlertRepository;
import eu.gpefrance.tenders.eligibility.EligibilityService;
import eu.gpefrance.tenders.eligibility.EligibilityState;
import eu.gpefrance.tenders.eligibility.TenderEligibility;
import eu.gpefrance.tenders.eligibility.TenderEligibilityRepository;

/**
 * What the interface shows of the retrieval of the consultation files: for a
 * tender, and for the whole application. The saved searches of a user are
 * only named to their owner.
 */
@Service
public class DceStatusService {

	private final DceLinkService dceLinks;

	private final DceLinkRepository links;

	private final DceLinkEventRepository events;

	private final DceQueueService queue;

	private final DcePlatformPolicy policy;

	private final DceStorageQuota quota;

	private final DceRetrievalProperties properties;

	private final TenderEligibilityRepository eligibilities;

	private final EligibilityService eligibility;

	private final AlertRepository alerts;

	public DceStatusService(DceLinkService dceLinks, DceLinkRepository links, DceLinkEventRepository events,
			DceQueueService queue, DcePlatformPolicy policy, DceStorageQuota quota, DceRetrievalProperties properties,
			TenderEligibilityRepository eligibilities, EligibilityService eligibility, AlertRepository alerts) {
		this.dceLinks = dceLinks;
		this.links = links;
		this.events = events;
		this.queue = queue;
		this.policy = policy;
		this.quota = quota;
		this.properties = properties;
		this.eligibilities = eligibilities;
		this.eligibility = eligibility;
		this.alerts = alerts;
	}

	@Transactional(readOnly = true)
	public List<DceLinkResponse> linksOf(Long tenderId, Long userId) {
		Optional<TenderEligibility> found = eligibilities.findById(tenderId);
		List<String> yourAlerts = found.map(e -> alerts.findByUserIdOrderByIdAsc(userId)
			.stream()
			.filter(alert -> e.getMatchedAlertIds().contains(alert.getId()))
			.map(Alert::getName)
			.toList()).orElse(List.of());
		EligibilityState state = found.map(TenderEligibility::getState).orElse(null);
		String reason = found.map(TenderEligibility::getReason).orElse("Pas encore évaluée");
		return dceLinks.linksOf(tenderId)
			.stream()
			.map(link -> DceLinkResponse.from(link, state, reason, yourAlerts, policy.isAllowed(link.getPlatform())))
			.toList();
	}

	@Transactional(readOnly = true)
	public List<DceEventResponse> history(Long linkId) {
		return queue.history(linkId).stream().map(event -> DceEventResponse.from(event, null)).toList();
	}

	@Transactional(readOnly = true)
	public List<DceEventResponse> recentEvents(int limit) {
		return events.findAllByOrderByOccurredAtDescIdDesc(PageRequest.of(0, limit))
			.stream()
			.map(event -> DceEventResponse.from(event, links.findById(event.getDceLinkId()).orElse(null)))
			.toList();
	}

	@Transactional(readOnly = true)
	public DceSummaryResponse summary() {
		Map<EligibilityState, Long> byEligibility = new EnumMap<>(EligibilityState.class);
		for (EligibilityState state : EligibilityState.values()) {
			byEligibility.put(state, eligibilities.countByState(state));
		}
		Map<DceLinkStatus, Long> byStatus = new EnumMap<>(DceLinkStatus.class);
		for (DceLinkStatus status : DceLinkStatus.values()) {
			byStatus.put(status, links.countByStatus(status));
		}
		return new DceSummaryResponse(byEligibility, byStatus, eligibility.outOfDate(), eligibility.isRunning(),
				quota.used(), quota.reserved(), quota.quota(), properties.allowedPlatforms().stream().sorted().toList(),
				properties.workerEnabled(), properties.concurrency(), properties.maxAttempts(),
				properties.maxFileSize().toBytes());
	}

	public DceLinkResponse retry(Long linkId, Long userId, String user) {
		DceLink link = links.findById(linkId).orElseThrow(() -> DceRuleException.notFound(linkId));
		boolean eligible = eligibilities.findById(link.getTenderId())
			.map(e -> e.getState() == EligibilityState.ELIGIBLE)
			.orElse(false);
		DceLink retried = queue.retry(linkId, eligible, user);
		return linksOf(retried.getTenderId(), userId).stream()
			.filter(response -> response.id().equals(linkId))
			.findFirst()
			.orElseThrow();
	}

}
