package eu.gpefrance.tenders.filter;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import eu.gpefrance.tenders.tender.Tender;
import eu.gpefrance.tenders.tender.TenderRepository;

/**
 * Applies the company's criteria to the tenders and records the reason.
 */
@Service
public class FilterService {

	private static final Logger log = LoggerFactory.getLogger(FilterService.class);

	private static final int BATCH_SIZE = 200;

	private final TenderRepository tenders;

	private final FilterCriteriaRepository criteriaRepository;

	private final TransactionTemplate transaction;

	private final Clock clock;

	public FilterService(TenderRepository tenders, FilterCriteriaRepository criteriaRepository,
			TransactionTemplate transaction, Clock clock) {
		this.tenders = tenders;
		this.criteriaRepository = criteriaRepository;
		this.transaction = transaction;
		this.clock = clock;
	}

	/**
	 * Filters the new tenders and those changed by a notice since they were
	 * filtered.
	 * @return the number of tenders filtered
	 */
	public synchronized int filterPending() {
		int filtered = 0;
		int batch;
		do {
			Integer count = transaction.execute(status -> {
				List<Tender> pending = tenders.findToFilter(PageRequest.of(0, BATCH_SIZE));
				apply(pending);
				return pending.size();
			});
			batch = count == null ? 0 : count;
			filtered += batch;
		}
		while (batch > 0);
		if (filtered > 0) {
			log.info("{} tenders filtered", filtered);
		}
		return filtered;
	}

	/**
	 * Filters every tender again, after the criteria changed.
	 * @return the number of tenders filtered
	 */
	public synchronized int filterAll() {
		int filtered = 0;
		long lastId = 0;
		while (true) {
			long after = lastId;
			List<Long> ids = transaction.execute(status -> {
				List<Tender> page = tenders.findByIdGreaterThanOrderById(after, PageRequest.of(0, BATCH_SIZE));
				apply(page);
				return page.stream().map(Tender::getId).toList();
			});
			if (ids == null || ids.isEmpty()) {
				break;
			}
			filtered += ids.size();
			lastId = ids.getLast();
		}
		log.info("{} tenders filtered with the new criteria", filtered);
		return filtered;
	}

	public FilterCriteria replaceCriteria(FilterCriteriaRequest request, String updatedBy) {
		return transaction.execute(status -> {
			FilterCriteria criteria = criteriaRepository.getCurrent();
			criteria.replace(request.cleanDepartments(), request.marketTypes().stream().distinct().toList(),
					request.minDaysBeforeDeadline(), request.cleanCpvPrefixes(), request.cleanKeywords(),
					request.cleanExcludedKeywords(), request.minAmount(), request.maxAmount(), updatedBy,
					clock.instant());
			return criteria;
		});
	}

	private void apply(List<Tender> batch) {
		FilterCriteria criteria = criteriaRepository.getCurrent();
		Instant now = clock.instant();
		for (Tender tender : batch) {
			FilterResult result = TenderFilter.apply(tender, criteria, now);
			tender.applyFilter(result.retained(), result.reason(), now);
		}
	}

}
