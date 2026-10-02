package eu.gpefrance.tenders.tender;

import java.time.Clock;
import java.util.EnumMap;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consultation of the tenders and decisions taken on them.
 */
@Service
public class TenderService {

	/** Closest deadline first; tenders without deadline last. */
	private static final Sort BY_DEADLINE = Sort.by(Sort.Order.asc("deadline").nullsLast(), Sort.Order.desc("id"));

	private final TenderRepository tenders;

	private final Clock clock;

	public TenderService(TenderRepository tenders, Clock clock) {
		this.tenders = tenders;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public PageResponse<TenderSummaryResponse> search(TenderSearch search, int page, int size) {
		return PageResponse.from(
				tenders.findAll(search.toSpecification(clock.instant()), PageRequest.of(page, size, BY_DEADLINE)),
				TenderSummaryResponse::from);
	}

	@Transactional(readOnly = true)
	public Map<TenderTab, Long> countByTab() {
		Map<TenderTab, Long> counts = new EnumMap<>(TenderTab.class);
		for (TenderTab tab : TenderTab.values()) {
			TenderSearch search = new TenderSearch(tab, null, null, null, null);
			counts.put(tab, tenders.count(search.toSpecification(clock.instant())));
		}
		return counts;
	}

	@Transactional(readOnly = true)
	public TenderDetailResponse detail(Long id) {
		return TenderDetailResponse.from(find(id));
	}

	@Transactional
	public TenderDetailResponse followUp(Long id, FollowUpRequest request, String user) {
		Tender tender = find(id);
		tender.followUp(request.status(), request.commentOr(tender.getFollowUpComment()), user, clock.instant());
		return TenderDetailResponse.from(tender);
	}

	private Tender find(Long id) {
		return tenders.findById(id).orElseThrow(() -> new TenderNotFoundException(id));
	}

}
