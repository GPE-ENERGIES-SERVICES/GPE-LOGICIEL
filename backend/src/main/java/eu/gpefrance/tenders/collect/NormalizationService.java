package eu.gpefrance.tenders.collect;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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
 * (rectification, award, cancellation) to the tender they refer to. Only the
 * information published by the source is written: the decision of the team,
 * its comment and the follows of the users are never touched.
 */
@Service
public class NormalizationService {

	/** Note of a notice whose tender is not known yet: it is applied once its initial notice is collected. */
	static final String INITIAL_NOT_COLLECTED = "Initial notice not collected";

	/** Start of the note of a notice that failed: it is tried again at the next collection. */
	static final String ERROR = "Error: ";

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
	 * Tries again the notices that failed at a previous run, processes every
	 * notice not converted yet, oldest publication first so an initial notice
	 * is handled before its rectifications, then the notices that were
	 * waiting for an initial notice collected since. Each notice has its own
	 * transaction: one unreadable notice does not block the others.
	 */
	public synchronized NormalizationReport normalizePending() {
		NormalizationReport report = NormalizationReport.NONE;
		// Failed at a previous run: tried once more, older than the notices collected since.
		for (RawNotice failed : rawNotices.findByNormalizationNoteStartingWithOrderByPublishedAtAscIdAsc(ERROR)) {
			report = report.plus(normalize(failed.getId()));
		}
		List<RawNotice> batch;
		do {
			batch = rawNotices.findTop100ByNormalizedAtIsNullOrderByPublishedAtAscIdAsc();
			for (RawNotice notice : batch) {
				report = report.plus(normalize(notice.getId()));
			}
		}
		while (!batch.isEmpty());
		for (Long waiting : rawNotices.findWaitingForAKnownNotice(INITIAL_NOT_COLLECTED)) {
			report = report.plus(normalize(waiting));
		}
		if (report.total() > 0) {
			log.info("{} collected notices normalized: {} tenders created, {} updated, {} ignored, {} errors",
					report.total(), report.created(), report.updated(), report.ignored(), report.errors());
		}
		return report;
	}

	private NormalizationReport normalize(Long rawNoticeId) {
		try {
			Outcome outcome = transaction.execute(status -> {
				RawNotice notice = rawNotices.findById(rawNoticeId).orElseThrow();
				Applied applied = apply(notice, parser.parse(notice.getPayload()));
				notice.markNormalized(applied.note(), clock.instant());
				return applied.outcome();
			});
			return switch (outcome) {
				case CREATED -> new NormalizationReport(1, 0, 0, 0);
				case UPDATED -> new NormalizationReport(0, 1, 0, 0);
				case IGNORED -> new NormalizationReport(0, 0, 1, 0);
			};
		}
		catch (RuntimeException ex) {
			log.warn("Notice {} could not be normalized", rawNoticeId, ex);
			String note = ERROR + ex.getClass().getSimpleName() + ": " + ex.getMessage();
			transaction.executeWithoutResult(status -> rawNotices.findById(rawNoticeId)
				.orElseThrow()
				.markNormalized(truncate(note), clock.instant()));
			return new NormalizationReport(0, 0, 0, 1);
		}
	}

	private Applied apply(RawNotice raw, ParsedNotice notice) {
		Instant now = clock.instant();
		if (notice.type() == BoampNoticeType.CALL_FOR_TENDERS) {
			return createTender(raw, notice, NoticeKind.INITIAL, now);
		}
		if (notice.type() == BoampNoticeType.OTHER) {
			return Applied.ignored("Not a call for tenders");
		}
		Optional<Tender> linked = linkedTender(notice);
		if (linked.isEmpty()) {
			// A complete rectification carries the whole tender: it can stand alone.
			return notice.type() == BoampNoticeType.COMPLETE_RECTIFICATION
					? createTender(raw, notice, NoticeKind.RECTIFICATION, now) : Applied.ignored(INITIAL_NOT_COLLECTED);
		}
		Tender tender = linked.get();
		// A rectification published before one already applied only completes the history.
		boolean latest = isLatest(tender, notice.details().publishedAt());
		switch (notice.type()) {
			case COMPLETE_RECTIFICATION -> {
				if (latest) {
					tender.update(notice.details(), now);
				}
				attach(tender, raw, notice, NoticeKind.RECTIFICATION);
			}
			case RECTIFICATION -> {
				if (latest && notice.details().deadline() != null) {
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
		return Applied.UPDATED;
	}

	private Applied createTender(RawNotice raw, ParsedNotice notice, NoticeKind kind, Instant now) {
		if (notice.details().title() == null) {
			return Applied.ignored("Notice without title");
		}
		Optional<Tender> known = tenders.findBySourceAndExternalId(raw.getSource(), notice.idweb());
		Tender tender = known.orElseGet(() -> new Tender(raw.getSource(), notice.idweb(), notice.details(), now));
		attach(tender, raw, notice, kind);
		tenders.save(tender);
		return known.isPresent() ? Applied.UPDATED : Applied.CREATED;
	}

	private static boolean isLatest(Tender tender, LocalDate publishedAt) {
		return publishedAt == null || tender.getNotices()
			.stream()
			.noneMatch(existing -> existing.getPublishedAt() != null && existing.getPublishedAt().isAfter(publishedAt));
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

	private enum Outcome {

		CREATED, UPDATED, IGNORED

	}

	/**
	 * @param note why the notice did not create or update a tender, if so
	 */
	private record Applied(Outcome outcome, String note) {

		static final Applied CREATED = new Applied(Outcome.CREATED, null);

		static final Applied UPDATED = new Applied(Outcome.UPDATED, null);

		static Applied ignored(String note) {
			return new Applied(Outcome.IGNORED, note);
		}

	}

}
