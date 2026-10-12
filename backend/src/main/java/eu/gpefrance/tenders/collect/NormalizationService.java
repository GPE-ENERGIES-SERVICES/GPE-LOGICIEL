package eu.gpefrance.tenders.collect;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import eu.gpefrance.tenders.tender.NoticeKind;
import eu.gpefrance.tenders.tender.Tender;
import eu.gpefrance.tenders.tender.TenderNotice;
import eu.gpefrance.tenders.tender.TenderNoticeRepository;
import eu.gpefrance.tenders.tender.TenderRepository;

/**
 * Converts the collected notices to tenders and attaches the later notices
 * (rectification, award, cancellation) to the tender they refer to. Each
 * source is processed apart, with its own {@link SourceParser}: a notice is
 * only ever attached to a tender of its own source. Only the information
 * published by the source is written: the decision of the team, its comment
 * and the follows of the users are never touched.
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

	private final Map<String, SourceParser> parsers;

	private final TransactionTemplate transaction;

	private final Clock clock;

	public NormalizationService(RawNoticeRepository rawNotices, TenderRepository tenders,
			TenderNoticeRepository tenderNotices, List<SourceParser> parsers, TransactionTemplate transaction,
			Clock clock) {
		this.rawNotices = rawNotices;
		this.tenders = tenders;
		this.tenderNotices = tenderNotices;
		this.parsers = parsers.stream().collect(Collectors.toUnmodifiableMap(SourceParser::source, Function.identity()));
		this.transaction = transaction;
		this.clock = clock;
	}

	/**
	 * For the notices of one source: tries again those that failed at a
	 * previous run, processes every notice not converted yet, oldest
	 * publication first so an initial notice is handled before its
	 * rectifications, then the notices that were waiting for an initial notice
	 * collected since. Each notice has its own transaction: one unreadable
	 * notice does not block the others.
	 */
	public synchronized NormalizationReport normalizePending(String source) {
		SourceParser parser = parsers.get(source);
		if (parser == null) {
			throw new IllegalArgumentException("No parser for the source " + source);
		}
		NormalizationReport report = NormalizationReport.NONE;
		// Failed at a previous run: tried once more, older than the notices collected since.
		for (RawNotice failed : rawNotices.findBySourceAndNormalizationNoteStartingWithOrderByPublishedAtAscIdAsc(source,
				ERROR)) {
			report = report.plus(normalize(failed.getId(), parser));
		}
		List<RawNotice> batch;
		do {
			batch = rawNotices.findTop100BySourceAndNormalizedAtIsNullOrderByPublishedAtAscIdAsc(source);
			for (RawNotice notice : batch) {
				report = report.plus(normalize(notice.getId(), parser));
			}
		}
		while (!batch.isEmpty());
		for (Long waiting : rawNotices.findWaitingForAKnownNotice(source, INITIAL_NOT_COLLECTED)) {
			report = report.plus(normalize(waiting, parser));
		}
		if (report.total() > 0) {
			log.info("{} {} notices normalized: {} tenders created, {} updated, {} ignored, {} errors", report.total(),
					source, report.created(), report.updated(), report.ignored(), report.errors());
		}
		return report;
	}

	private NormalizationReport normalize(Long rawNoticeId, SourceParser parser) {
		try {
			Outcome outcome = transaction.execute(status -> {
				RawNotice notice = rawNotices.findById(rawNoticeId).orElseThrow();
				SourceNotice parsed = parser.parse(notice.getPayload());
				Applied applied = apply(notice, parsed);
				notice.markNormalized(applied.note(), parsed.linkedIds(), clock.instant());
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
				.markFailed(truncate(note), clock.instant()));
			return new NormalizationReport(0, 0, 0, 1);
		}
	}

	private Applied apply(RawNotice raw, SourceNotice notice) {
		Instant now = clock.instant();
		if (notice.type() == NoticeType.CALL_FOR_TENDERS) {
			return createTender(raw, notice, NoticeKind.INITIAL, now);
		}
		if (notice.type() == NoticeType.OTHER) {
			return Applied.ignored("Not a call for tenders");
		}
		Optional<Tender> linked = linkedTender(raw, notice);
		if (linked.isEmpty()) {
			// A complete rectification carries the whole tender: it can stand alone.
			return notice.type() == NoticeType.COMPLETE_RECTIFICATION
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

	private Applied createTender(RawNotice raw, SourceNotice notice, NoticeKind kind, Instant now) {
		if (notice.details().title() == null) {
			return Applied.ignored("Notice without title");
		}
		Optional<Tender> rectified = kind == NoticeKind.INITIAL ? rectifiedBefore(raw, notice) : Optional.empty();
		if (rectified.isPresent()) {
			// The rectification citing it came later and prevails: the initial notice only completes.
			Tender tender = rectified.get();
			tender.complete(notice.details(), now);
			attach(tender, raw, notice, kind);
			return Applied.UPDATED;
		}
		Optional<Tender> known = tenders.findBySourceAndExternalId(raw.getSource(), notice.externalId());
		Tender tender = known.orElseGet(() -> new Tender(raw.getSource(), notice.externalId(), notice.details(), now));
		attach(tender, raw, notice, kind);
		tenders.save(tender);
		return known.isPresent() ? Applied.UPDATED : Applied.CREATED;
	}

	/**
	 * The tender made by a complete rectification received before this initial
	 * notice and citing it, in the same source: the initial notice joins it
	 * instead of making a second tender for the same consultation. Only an
	 * explicit citation counts; with several candidates, none is chosen.
	 */
	private Optional<Tender> rectifiedBefore(RawNotice raw, SourceNotice notice) {
		if (tenders.findBySourceAndExternalId(raw.getSource(), notice.externalId()).isPresent()) {
			return Optional.empty();
		}
		List<Long> candidates = tenders.findMadeByARectificationCiting(raw.getSource(), notice.externalId());
		if (candidates.size() > 1) {
			log.warn("{} notice {} is cited by the rectifications of several tenders {}: none is chosen",
					raw.getSource(), notice.externalId(), candidates);
			return Optional.empty();
		}
		return candidates.stream().findFirst().flatMap(tenders::findById);
	}

	private static boolean isLatest(Tender tender, LocalDate publishedAt) {
		return publishedAt == null || tender.getNotices()
			.stream()
			.noneMatch(existing -> existing.getPublishedAt() != null && existing.getPublishedAt().isAfter(publishedAt));
	}

	private static void attach(Tender tender, RawNotice raw, SourceNotice notice, NoticeKind kind) {
		if (!tender.identifyProcedure(notice.procedureId())) {
			log.warn("{} notice {} gives the procedure {} to a tender of the procedure {}: the first one is kept",
					raw.getSource(), notice.externalId(), notice.procedureId(), tender.getProcedureId());
		}
		boolean alreadyAttached = tender.getNotices()
			.stream()
			.anyMatch(existing -> existing.getRawNoticeId().equals(raw.getId()));
		if (!alreadyAttached) {
			tender.addNotice(raw.getId(), notice.externalId(), kind, notice.details().publishedAt(),
					notice.details().deadline(), notice.changeDescription());
		}
	}

	/**
	 * The tender the notice is attached to, if it was applied before: read
	 * again, a notice stays with its tender. Otherwise the tender of the
	 * notices it refers to, in its source.
	 */
	private Optional<Tender> linkedTender(RawNotice raw, SourceNotice notice) {
		Optional<Tender> attached = tenderNotices.findByRawNoticeId(raw.getId()).map(TenderNotice::getTender);
		if (attached.isPresent() || notice.linkedIds().isEmpty()) {
			return attached;
		}
		return tenderNotices.findByTenderSourceAndExternalIdInOrderByIdAsc(raw.getSource(), notice.linkedIds())
			.stream()
			.map(TenderNotice::getTender)
			.findFirst();
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
