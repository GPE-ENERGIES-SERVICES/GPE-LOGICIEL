package eu.gpefrance.tenders.collect;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import eu.gpefrance.tenders.collect.boamp.BoampNoticeParser;
import eu.gpefrance.tenders.collect.boamp.BoampNoticeType;
import eu.gpefrance.tenders.collect.boamp.ParsedNotice;
import eu.gpefrance.tenders.tender.NoticeKind;
import eu.gpefrance.tenders.tender.Tender;
import eu.gpefrance.tenders.tender.TenderNotice;
import eu.gpefrance.tenders.tender.TenderNoticeRepository;
import eu.gpefrance.tenders.tender.TenderRepository;

/**
 * Converts the collected notices to tenders and attaches the later notices
 * (rectification, award, cancellation) to the tender they refer to.
 */
@Service
public class NormalizationService {

	private static final Logger log = LoggerFactory.getLogger(NormalizationService.class);

	private static final int MAX_NOTE_LENGTH = 2000;

	private final RawNoticeRepository rawNotices;

	private final TenderRepository tenders;

	private final TenderNoticeRepository tenderNotices;

	private final BoampNoticeParser parser;

	private final TransactionTemplate transaction;

	private final Clock clock;

	public NormalizationService(RawNoticeRepository rawNotices, TenderRepository tenders,
			TenderNoticeRepository tenderNotices, BoampNoticeParser parser, TransactionTemplate transaction,
			Clock clock) {
		this.rawNotices = rawNotices;
		this.tenders = tenders;
		this.tenderNotices = tenderNotices;
		this.parser = parser;
		this.transaction = transaction;
		this.clock = clock;
	}

	/**
	 * Processes every notice not converted yet, oldest publication first so an
	 * initial notice is handled before its rectifications. Each notice has its
	 * own transaction: one unreadable notice does not block the others.
	 * @return the number of notices processed
	 */
	public synchronized int normalizePending() {
		int processed = 0;
		List<RawNotice> batch;
		do {
			batch = rawNotices.findTop100ByNormalizedAtIsNullOrderByPublishedAtAscIdAsc();
			for (RawNotice notice : batch) {
				normalize(notice.getId());
				processed++;
			}
		}
		while (!batch.isEmpty());
		if (processed > 0) {
			log.info("{} collected notices normalized", processed);
		}
		return processed;
	}

	private void normalize(Long rawNoticeId) {
		try {
			transaction.executeWithoutResult(status -> {
				RawNotice notice = rawNotices.findById(rawNoticeId).orElseThrow();
				notice.markNormalized(apply(notice, parser.parse(notice.getPayload())), clock.instant());
			});
		}
		catch (RuntimeException ex) {
			log.warn("Notice {} could not be normalized", rawNoticeId, ex);
			String note = "Error: " + ex.getClass().getSimpleName() + ": " + ex.getMessage();
			transaction.executeWithoutResult(status -> rawNotices.findById(rawNoticeId)
				.orElseThrow()
				.markNormalized(truncate(note), clock.instant()));
		}
	}

	/**
	 * @return a note explaining why the notice was not applied, or {@code null}
	 */
	private String apply(RawNotice raw, ParsedNotice notice) {
		Instant now = clock.instant();
		if (notice.type() == BoampNoticeType.CALL_FOR_TENDERS) {
			return createTender(raw, notice, NoticeKind.INITIAL, now);
		}
		if (notice.type() == BoampNoticeType.OTHER) {
			return "Not a call for tenders";
		}
		Optional<Tender> linked = linkedTender(notice);
		if (linked.isEmpty()) {
			// A complete rectification carries the whole tender: it can stand alone.
			return notice.type() == BoampNoticeType.COMPLETE_RECTIFICATION
					? createTender(raw, notice, NoticeKind.RECTIFICATION, now) : "Initial notice not collected";
		}
		Tender tender = linked.get();
		switch (notice.type()) {
			case COMPLETE_RECTIFICATION -> {
				tender.update(notice.details(), now);
				attach(tender, raw, notice, NoticeKind.RECTIFICATION);
			}
			case RECTIFICATION -> {
				if (notice.details().deadline() != null) {
					tender.changeDeadline(notice.details().deadline(), now);
				}
				attach(tender, raw, notice, NoticeKind.RECTIFICATION);
			}
			case AWARD -> {
				tender.close(now);
				attach(tender, raw, notice, NoticeKind.AWARD);
			}
			case CANCELLATION -> {
				tender.cancel(now);
				attach(tender, raw, notice, NoticeKind.CANCELLATION);
			}
			default -> throw new IllegalStateException("Unexpected notice type " + notice.type());
		}
		return null;
	}

	private String createTender(RawNotice raw, ParsedNotice notice, NoticeKind kind, Instant now) {
		if (notice.details().title() == null) {
			return "Notice without title";
		}
		Tender tender = tenders.findBySourceAndExternalId(raw.getSource(), notice.idweb())
			.orElseGet(() -> new Tender(raw.getSource(), notice.idweb(), notice.details(), now));
		attach(tender, raw, notice, kind);
		tenders.save(tender);
		return null;
	}

	private static void attach(Tender tender, RawNotice raw, ParsedNotice notice, NoticeKind kind) {
		boolean alreadyAttached = tender.getNotices()
			.stream()
			.anyMatch(existing -> existing.getRawNoticeId().equals(raw.getId()));
		if (!alreadyAttached) {
			tender.addNotice(raw.getId(), notice.idweb(), kind, notice.details().publishedAt(),
					notice.details().deadline(), notice.changeDescription());
		}
	}

	private Optional<Tender> linkedTender(ParsedNotice notice) {
		if (notice.linkedIds().isEmpty()) {
			return Optional.empty();
		}
		return tenderNotices.findByExternalIdIn(notice.linkedIds()).stream().map(TenderNotice::getTender).findFirst();
	}

	private static String truncate(String note) {
		return note.length() <= MAX_NOTE_LENGTH ? note : note.substring(0, MAX_NOTE_LENGTH);
	}

}
