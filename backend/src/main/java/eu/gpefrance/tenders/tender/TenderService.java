package eu.gpefrance.tenders.tender;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consultation of the tenders, decisions taken on them and follows of the
 * users.
 */
@Service
public class TenderService {

	private final TenderRepository tenders;

	private final TenderFollowRepository follows;

	private final Clock clock;

	public TenderService(TenderRepository tenders, TenderFollowRepository follows, Clock clock) {
		this.tenders = tenders;
		this.follows = follows;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public PageResponse<TenderSummaryResponse> search(TenderTab tab, TenderSearch search, String sort, Long userId,
			int page, int size) {
		Instant now = clock.instant();
		Page<Tender> found = tenders.findAll(
				search.toSpecification(tab, userId, now).and(TenderSearch.orderedBy(sort, now)),
				PageRequest.of(page, size));
		List<Long> ids = found.getContent().stream().map(Tender::getId).toList();
		Set<Long> followed = ids.isEmpty() ? Set.of() : new HashSet<>(follows.findFollowed(userId, ids));
		return PageResponse.from(found, tender -> TenderSummaryResponse.from(tender, followed.contains(tender.getId())));
	}

	/**
	 * Number of tenders of each tab matching the search.
	 */
	@Transactional(readOnly = true)
	public Map<TenderTab, Long> countByTab(TenderSearch search, Long userId) {
		Instant now = clock.instant();
		Map<TenderTab, Long> counts = new EnumMap<>(TenderTab.class);
		for (TenderTab tab : TenderTab.values()) {
			counts.put(tab, tenders.count(search.toSpecification(tab, userId, now)));
		}
		return counts;
	}

	/**
	 * Number of tenders of each follow-up status in a tab, with the search but
	 * whatever its status filter.
	 */
	@Transactional(readOnly = true)
	public Map<FollowUpStatus, Long> countByStatus(TenderTab tab, TenderSearch search, Long userId) {
		Instant now = clock.instant();
		Map<FollowUpStatus, Long> counts = new EnumMap<>(FollowUpStatus.class);
		for (FollowUpStatus status : FollowUpStatus.values()) {
			counts.put(status, tenders.count(search.withStatus(List.of(status)).toSpecification(tab, userId, now)));
		}
		return counts;
	}

	@Transactional(readOnly = true)
	public TenderDetailResponse detail(Long id, Long userId) {
		return TenderDetailResponse.from(find(id), isFollowed(id, userId));
	}

	@Transactional
	public TenderDetailResponse followUp(Long id, FollowUpRequest request, String user, Long userId) {
		Tender tender = find(id);
		tender.followUp(request.status(), request.commentOr(tender.getFollowUpComment()), user, clock.instant());
		return TenderDetailResponse.from(tender, isFollowed(id, userId));
	}

	/**
	 * Follows or no longer follows a tender; doing it twice changes nothing.
	 */
	@Transactional
	public void follow(Long id, Long userId, boolean followed) {
		find(id);
		TenderFollow.Key key = new TenderFollow.Key(userId, id);
		if (followed && !follows.existsById(key)) {
			follows.save(new TenderFollow(userId, id, clock.instant()));
		}
		else if (!followed) {
			follows.deleteById(key);
		}
	}

	private boolean isFollowed(Long tenderId, Long userId) {
		return follows.existsById(new TenderFollow.Key(userId, tenderId));
	}

	private Tender find(Long id) {
		return tenders.findById(id).orElseThrow(() -> new TenderNotFoundException(id));
	}

}
