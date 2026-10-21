package eu.gpefrance.tenders.collect;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import eu.gpefrance.tenders.document.DceLinkService;
import eu.gpefrance.tenders.tender.NoticeKind;
import eu.gpefrance.tenders.tender.Tender;
import eu.gpefrance.tenders.tender.TenderMatch;
import eu.gpefrance.tenders.tender.TenderMatchRepository;
import eu.gpefrance.tenders.tender.TenderNotice;
import eu.gpefrance.tenders.tender.TenderNoticeRepository;
import eu.gpefrance.tenders.tender.TenderRepository;

/**
 * Converts the collected notices to tenders and attaches the later notices
 * (rectification, award, cancellation) to the tender they refer to. Each
 * source is processed apart, with its own {@link SourceParser}: a notice only
 * changes a tender of its own source. A source that defers to the others
 * (TED) adds its notices of a consultation already published by another
 * source (BOAMP) to the history of that tender, without changing it; the
 * other way round, a match is proposed to a person. Only the information
 * published by the source is written: the decision of the team, its comment
 * and the follows of the users are never touched.
 */
@Service
public class NormalizationService {

	/** Note of a notice whose tender is not known yet: it is applied once its initial notice is collected. */
	static final String INITIAL_NOT_COLLECTED = "Initial notice not collected";

	/**
	 * Note of a notice of a source that defers to the others, whose procedure
	 * is the one of several tenders of other sources: left to a person.
	 */
	static final String SHARED_PROCEDURE = "Procedure of several tenders of other sources";

	/** Start of the note of a notice that failed: it is tried again at the next collection. */
	static final String ERROR = "Error: ";

	private static final Logger log = LoggerFactory.getLogger(NormalizationService.class);

	private static final int MAX_NOTE_LENGTH = 2000;

	private final RawNoticeRepository rawNotices;

	private final TenderRepository tenders;

	private final TenderNoticeRepository tenderNotices;

	private final TenderMatchRepository matches;

	private final DceLinkService dceLinks;

	private final Map<String, SourceParser> parsers;

	private final TransactionTemplate transaction;

	private final Clock clock;

	public NormalizationService(RawNoticeRepository rawNotices, TenderRepository tenders,
			TenderNoticeRepository tenderNotices, TenderMatchRepository matches, DceLinkService dceLinks,
			List<SourceParser> parsers, TransactionTemplate transaction, Clock clock) {
		this.rawNotices = rawNotices;
		this.tenders = tenders;
		this.tenderNotices = tenderNotices;
		this.matches = matches;
		this.dceLinks = dceLinks;
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
				Applied applied = apply(notice, parsed, parser);
				if (applied.tender() != null) {
					// Where to obtain the consultation file, as this notice gives it, with its source.
					dceLinks.record(applied.tender().getId(), notice.getSource(), parsed.details().platformUrl(),
							clock.instant());
				}
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

	private Applied apply(RawNotice raw, SourceNotice notice, SourceParser parser) {
		Instant now = clock.instant();
		if (notice.type() == NoticeType.OTHER) {
			return Applied.ignored("Not a call for tenders");
		}
		Optional<Tender> attached = tenderNotices.findByRawNoticeId(raw.getId()).map(TenderNotice::getTender);
		if (attached.isPresent() && !attached.get().getSource().equals(raw.getSource())) {
			// Read again, a notice kept in the history of a tender of another source stays there only.
			return Applied.updated(attached.get());
		}
		if (notice.type() == NoticeType.CALL_FOR_TENDERS) {
			return createTender(raw, notice, NoticeKind.INITIAL, now, parser);
		}
		Optional<Tender> linked = attached.isPresent() ? attached : linkedTender(raw, notice, parser);
		if (linked.isEmpty() && parser.defersToOtherSources()) {
			Optional<Applied> deferred = deferToOtherSource(raw, notice);
			if (deferred.isPresent()) {
				return deferred.get();
			}
		}
		if (linked.isEmpty()) {
			// A complete rectification carries the whole tender: it can stand alone.
			return notice.type() == NoticeType.COMPLETE_RECTIFICATION
					? createTender(raw, notice, NoticeKind.RECTIFICATION, now, parser)
					: Applied.ignored(INITIAL_NOT_COLLECTED);
		}
		Tender tender = linked.get();
		// A rectification published before one already applied only completes the history.
		applyLaterNotice(tender, notice, isLatest(tender, raw.getSource(), notice.details().publishedAt()), now);
		attach(tender, raw, notice, kindOf(notice.type()));
		return Applied.updated(tender);
	}

	/**
	 * What a rectification, an award or a cancellation of its source changes
	 * on a tender: a rectification only when no later one was applied.
	 */
	private static void applyLaterNotice(Tender tender, SourceNotice notice, boolean latest, Instant now) {
		switch (notice.type()) {
			case COMPLETE_RECTIFICATION -> {
				if (latest) {
					tender.update(notice.details(), now);
					tender.reopenIfDeadlineToCome(now);
				}
			}
			case RECTIFICATION -> {
				if (latest && notice.details().deadline() != null) {
					tender.changeDeadline(notice.details().deadline(), now);
					tender.reopenIfDeadlineToCome(now);
				}
			}
			case AWARD -> tender.close(now);
			case CANCELLATION -> tender.cancel(now);
			default -> throw new IllegalStateException("Unexpected notice type " + notice.type());
		}
	}

	/**
	 * A stored notice read again by the parser of its source, with the rules
	 * of today: how it would be applied if it was collected now.
	 */
	public SourceNotice read(RawNotice raw) {
		return parsers.get(raw.getSource()).parse(raw.getPayload());
	}

	/**
	 * The kind a stored notice gets in the history of a tender when read now;
	 * empty for a notice that is not one of a tender.
	 */
	public Optional<NoticeKind> currentKind(RawNotice raw) {
		NoticeType type = read(raw).type();
		return type == NoticeType.OTHER ? Optional.empty() : Optional.of(kindOf(type));
	}

	/**
	 * Applies to a tender the notices it has just taken from a duplicate
	 * merged into it, oldest first, by the rules of the collection: only the
	 * notices of its own source change it, a rectification only when it is
	 * the latest, an initial notice only completes what is unknown. The
	 * notices are already in its history.
	 */
	public void applyTakenNotices(Tender tender, List<TenderNotice> taken, Instant now) {
		SourceParser parser = parsers.get(tender.getSource());
		List<TenderNotice> ownSource = taken.stream()
			.filter(notice -> notice.getSource().equals(tender.getSource()))
			.sorted(Comparator.comparing(TenderNotice::getPublishedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
				.thenComparing(TenderNotice::getId))
			.toList();
		for (TenderNotice taking : ownSource) {
			SourceNotice notice = parser.parse(rawNotices.findById(taking.getRawNoticeId()).orElseThrow().getPayload());
			boolean latest = isLatest(tender, tender.getSource(), notice.details().publishedAt());
			switch (notice.type()) {
				case CALL_FOR_TENDERS -> {
					if (latest) {
						tender.update(notice.details(), now);
					}
					else {
						tender.complete(notice.details(), now);
					}
				}
				case OTHER -> {
				}
				default -> applyLaterNotice(tender, notice, latest, now);
			}
		}
	}

	private Applied createTender(RawNotice raw, SourceNotice notice, NoticeKind kind, Instant now,
			SourceParser parser) {
		if (notice.details().title() == null) {
			return Applied.ignored("Notice without title");
		}
		Optional<Tender> rectified = kind == NoticeKind.INITIAL ? rectifiedBefore(raw, notice, parser)
				: Optional.empty();
		if (rectified.isPresent()) {
			// The rectification citing it came later and prevails: the initial notice only completes.
			Tender tender = rectified.get();
			tender.complete(notice.details(), now);
			attach(tender, raw, notice, kind);
			return Applied.updated(tender);
		}
		Optional<Tender> known = tenders.findBySourceAndExternalId(raw.getSource(), notice.externalId());
		if (known.isPresent() && parser.republishesChangedNotices() && !isAttached(known.get(), raw)) {
			// A new version of the whole notice: applied as a complete rectification.
			Tender tender = known.get();
			if (isLatest(tender, raw.getSource(), notice.details().publishedAt())) {
				tender.update(notice.details(), now);
				tender.reopenIfDeadlineToCome(now);
			}
			attach(tender, raw, notice, NoticeKind.RECTIFICATION);
			return Applied.updated(tender);
		}
		if (known.isEmpty() && parser.defersToOtherSources()) {
			Optional<Applied> deferred = deferToOtherSource(raw, notice);
			if (deferred.isPresent()) {
				return deferred.get();
			}
		}
		Tender tender = known.orElseGet(() -> new Tender(raw.getSource(), notice.externalId(), notice.details(), now));
		attach(tender, raw, notice, kind);
		tenders.save(tender);
		if (known.isEmpty()) {
			proposeMatches(tender, now);
		}
		return known.isPresent() ? Applied.updated(tender) : Applied.created(tender);
	}

	/**
	 * For a source that defers to the others, a notice without tender in its
	 * source whose procedure is the one of a tender of another source: added
	 * to the history of that tender only, never changing it. With several
	 * such tenders, nothing is chosen: the notice is set aside and reported.
	 * @return empty when no tender of another source has the procedure
	 */
	private Optional<Applied> deferToOtherSource(RawNotice raw, SourceNotice notice) {
		if (notice.procedureId() == null) {
			return Optional.empty();
		}
		List<Tender> others = tenders
			.findByProcedureIdAndSourceNotAndMergedIntoIdIsNullOrderByIdAsc(notice.procedureId(),
				raw.getSource());
		if (others.isEmpty()) {
			return Optional.empty();
		}
		if (others.size() > 1) {
			log.warn("{} notice {} has the procedure {} of several tenders of other sources {}: set aside",
					raw.getSource(), notice.externalId(), notice.procedureId(),
					others.stream().map(Tender::getId).toList());
			return Optional.of(Applied.ignored(SHARED_PROCEDURE));
		}
		Tender other = others.getFirst();
		attach(other, raw, notice, kindOf(notice.type()));
		return Optional.of(Applied.updated(other));
	}

	/**
	 * A new tender whose procedure is the one of tenders of other sources:
	 * the same consultation published twice, proposed to a person. The
	 * tenders are left as they are.
	 */
	private void proposeMatches(Tender tender, Instant now) {
		if (tender.getProcedureId() != null) {
			for (Tender other : tenders
				.findByProcedureIdAndSourceNotAndMergedIntoIdIsNullOrderByIdAsc(tender.getProcedureId(),
					tender.getSource())) {
				propose(tender, other, TenderMatch.SAME_PROCEDURE, now);
			}
		}
		if (tender.getReference() != null && tender.getDeadline() != null) {
			for (Tender other : tenders.findByReferenceAndDeadlineAndSourceNotAndMergedIntoIdIsNullOrderByIdAsc(
					tender.getReference(), tender.getDeadline(), tender.getSource())) {
				if (proposesMatchesByReference(tender.getSource()) || proposesMatchesByReference(other.getSource())) {
					propose(tender, other, TenderMatch.SAME_REFERENCE, now);
				}
			}
		}
	}

	private void propose(Tender tender, Tender other, String rule, Instant now) {
		if (!matches.existsByTenderIdAndMatchedTenderId(tender.getId(), other.getId())) {
			matches.save(new TenderMatch(tender.getId(), other.getId(), rule, now));
			log.info("{} tender {} and {} tender {} match by the rule {}: match proposed", tender.getSource(),
					tender.getId(), other.getSource(), other.getId(), rule);
		}
	}

	private boolean proposesMatchesByReference(String source) {
		SourceParser parser = parsers.get(source);
		return parser != null && parser.proposesMatchesByReference();
	}

	private static NoticeKind kindOf(NoticeType type) {
		return switch (type) {
			case CALL_FOR_TENDERS -> NoticeKind.INITIAL;
			case RECTIFICATION, COMPLETE_RECTIFICATION -> NoticeKind.RECTIFICATION;
			case AWARD -> NoticeKind.AWARD;
			case CANCELLATION -> NoticeKind.CANCELLATION;
			case OTHER -> throw new IllegalArgumentException("Not a notice of a tender");
		};
	}

	/**
	 * The tender made by a complete rectification received before this initial
	 * notice and citing it, in the same source: the initial notice joins it
	 * instead of making a second tender for the same consultation. Only an
	 * explicit citation counts, or for a source that links its notices by
	 * procedure, the same procedure; with several candidates, none is chosen.
	 */
	private Optional<Tender> rectifiedBefore(RawNotice raw, SourceNotice notice, SourceParser parser) {
		if (tenders.findBySourceAndExternalId(raw.getSource(), notice.externalId()).isPresent()) {
			return Optional.empty();
		}
		List<Long> candidates = tenders.findMadeByARectificationCiting(raw.getSource(), notice.externalId());
		if (candidates.size() > 1) {
			log.warn("{} notice {} is cited by the rectifications of several tenders {}: none is chosen",
					raw.getSource(), notice.externalId(), candidates);
			return Optional.empty();
		}
		if (candidates.isEmpty() && parser.linksNoticesByProcedure()) {
			return sameProcedure(raw, notice);
		}
		return candidates.stream().findFirst().flatMap(tenders::findById);
	}

	/**
	 * The only tender of the same source with the procedure of the notice.
	 */
	private Optional<Tender> sameProcedure(RawNotice raw, SourceNotice notice) {
		if (notice.procedureId() == null) {
			return Optional.empty();
		}
		List<Tender> found = tenders.findBySourceAndProcedureIdAndMergedIntoIdIsNull(raw.getSource(),
				notice.procedureId());
		if (found.size() > 1) {
			log.warn("{} notice {} has the procedure {} of several tenders {}: none is chosen", raw.getSource(),
					notice.externalId(), notice.procedureId(), found.stream().map(Tender::getId).toList());
			return Optional.empty();
		}
		return found.stream().findFirst();
	}

	/**
	 * Whether no notice of the same source attached to the tender was
	 * published after this one. The notices of other sources kept in its
	 * history do not count: they never change the tender.
	 */
	private static boolean isLatest(Tender tender, String source, LocalDate publishedAt) {
		return publishedAt == null || tender.getNotices()
			.stream()
			.filter(existing -> existing.getSource().equals(source))
			.noneMatch(existing -> existing.getPublishedAt() != null && existing.getPublishedAt().isAfter(publishedAt));
	}

	/**
	 * Adds the notice to the history of the tender, with its own source. The
	 * procedure identifier is only taken from a notice of the source of the
	 * tender.
	 */
	private static void attach(Tender tender, RawNotice raw, SourceNotice notice, NoticeKind kind) {
		if (tender.getSource().equals(raw.getSource()) && !tender.identifyProcedure(notice.procedureId())) {
			log.warn("{} notice {} gives the procedure {} to a tender of the procedure {}: the first one is kept",
					raw.getSource(), notice.externalId(), notice.procedureId(), tender.getProcedureId());
		}
		if (!isAttached(tender, raw)) {
			tender.addNotice(raw.getSource(), raw.getId(), notice.externalId(), kind, notice.details().publishedAt(),
					notice.details().deadline(), notice.changeDescription());
		}
	}

	private static boolean isAttached(Tender tender, RawNotice raw) {
		return tender.getNotices().stream().anyMatch(existing -> existing.getRawNoticeId().equals(raw.getId()));
	}

	/**
	 * The tender of the notices it refers to, in its source; for a source that
	 * links its notices by procedure, the tender of the same source and
	 * procedure.
	 */
	private Optional<Tender> linkedTender(RawNotice raw, SourceNotice notice, SourceParser parser) {
		Optional<Tender> cited = notice.linkedIds().isEmpty() ? Optional.empty()
				: tenderNotices
					.findByTenderSourceAndSourceAndExternalIdInOrderByIdAsc(raw.getSource(), raw.getSource(),
							notice.linkedIds())
					.stream()
					.map(TenderNotice::getTender)
					.findFirst();
		return cited.isPresent() || !parser.linksNoticesByProcedure() ? cited : sameProcedure(raw, notice);
	}

	private static String truncate(String note) {
		return note.length() <= MAX_NOTE_LENGTH ? note : note.substring(0, MAX_NOTE_LENGTH);
	}

	private enum Outcome {

		CREATED, UPDATED, IGNORED

	}

	/**
	 * @param note why the notice did not create or update a tender, if so
	 * @param tender the tender the notice was attached to, if any
	 */
	private record Applied(Outcome outcome, String note, Tender tender) {

		static Applied created(Tender tender) {
			return new Applied(Outcome.CREATED, null, tender);
		}

		static Applied updated(Tender tender) {
			return new Applied(Outcome.UPDATED, null, tender);
		}

		static Applied ignored(String note) {
			return new Applied(Outcome.IGNORED, note, null);
		}

	}

}
