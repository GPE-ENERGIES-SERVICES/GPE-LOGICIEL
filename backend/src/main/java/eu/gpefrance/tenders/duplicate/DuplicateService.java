package eu.gpefrance.tenders.duplicate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import eu.gpefrance.tenders.collect.NoticeType;
import eu.gpefrance.tenders.collect.NormalizationService;
import eu.gpefrance.tenders.collect.RawNoticeRepository;
import eu.gpefrance.tenders.collect.SourceNotice;
import eu.gpefrance.tenders.document.DceLink;
import eu.gpefrance.tenders.document.DceLinkRepository;
import eu.gpefrance.tenders.document.DossierRepository;
import eu.gpefrance.tenders.duplicate.DuplicateResponses.BatchMerge;
import eu.gpefrance.tenders.duplicate.DuplicateResponses.BatchPreview;
import eu.gpefrance.tenders.duplicate.DuplicateResponses.Candidate;
import eu.gpefrance.tenders.duplicate.DuplicateResponses.Group;
import eu.gpefrance.tenders.tender.FollowUpStatus;
import eu.gpefrance.tenders.tender.NoticeKind;
import eu.gpefrance.tenders.tender.Tender;
import eu.gpefrance.tenders.tender.TenderFollowRepository;
import eu.gpefrance.tenders.tender.TenderNotice;
import eu.gpefrance.tenders.tender.TenderRepository;
import eu.gpefrance.tenders.tender.TenderState;
import tools.jackson.databind.json.JsonMapper;

/**
 * Tenders that are probably the same consultation, found by their exact
 * eForms procedure identifier, and the decisions of a person on them. A merge
 * moves the notices and links of the duplicate to the tender kept, applies
 * them by the rules of the collection and hides the duplicate; nothing is
 * deleted and the decision of the team is never changed. It is undone by
 * giving back the state saved before it.
 */
@Service
public class DuplicateService {

	private final TenderRepository tenders;

	private final DuplicateDecisionRepository decisions;

	private final RawNoticeRepository rawNotices;

	private final NormalizationService normalization;

	private final TenderFollowRepository follows;

	private final DossierRepository dossiers;

	private final DceLinkRepository links;

	private final JsonMapper jsonMapper;

	private final Clock clock;

	public DuplicateService(TenderRepository tenders, DuplicateDecisionRepository decisions,
			RawNoticeRepository rawNotices, NormalizationService normalization, TenderFollowRepository follows,
			DossierRepository dossiers, DceLinkRepository links, JsonMapper jsonMapper, Clock clock) {
		this.tenders = tenders;
		this.decisions = decisions;
		this.rawNotices = rawNotices;
		this.normalization = normalization;
		this.follows = follows;
		this.dossiers = dossiers;
		this.links = links;
		this.jsonMapper = jsonMapper;
		this.clock = clock;
	}

	/**
	 * Every group of tenders not merged sharing a procedure identifier, but
	 * two tenders a person said are different consultations.
	 */
	@Transactional(readOnly = true)
	public List<Group> groups() {
		Set<Set<Long>> notDuplicates = new HashSet<>();
		for (DuplicateDecision decision : decisions.findByKindAndUndoneAtIsNull(DuplicateDecision.Kind.NOT_DUPLICATE)) {
			notDuplicates.add(Set.of(decision.getKeptTenderId(), decision.getOtherTenderId()));
		}
		List<Group> groups = new ArrayList<>();
		for (String procedureId : tenders.findSharedProcedures()) {
			List<Tender> members = tenders.findByProcedureIdAndMergedIntoIdIsNullOrderByIdAsc(procedureId);
			if (members.size() == 2
					&& notDuplicates.contains(Set.of(members.get(0).getId(), members.get(1).getId()))) {
				continue;
			}
			groups.add(group(procedureId, members));
		}
		return groups;
	}

	/**
	 * Merges a duplicate into the tender kept. Refused unless both have the
	 * same procedure identifier; refused when only the duplicate has data of
	 * the team; when both have, only with an explicit acknowledgement, the
	 * data of the duplicate staying on it.
	 */
	@Transactional
	public DuplicateDecision merge(Long keptId, Long otherId, boolean acknowledgeTeamData, String user) {
		Tender kept = find(keptId);
		Tender other = find(otherId);
		check(kept, other);
		boolean keptData = hasTeamData(kept);
		boolean otherData = hasTeamData(other);
		if (otherData && !keptData) {
			throw DuplicateRuleException.refused("La consultation " + other.getId()
					+ " porte des données de l'équipe : conservez-la plutôt que de la fusionner.");
		}
		if (otherData && !acknowledgeTeamData) {
			throw DuplicateRuleException.refused("Les deux consultations portent des données de l'équipe : "
					+ "confirmez explicitement la consultation conservée.");
		}
		Instant now = clock.instant();
		String reason = String.join(" ", explain(List.of(kept, other)));
		String before = jsonMapper.writeValueAsString(TenderState.of(kept));
		List<TenderNotice> taken = List.copyOf(other.getNotices());
		taken.forEach(kept::takeNotice);
		List<Long> movedLinks = new ArrayList<>();
		for (DceLink link : links.findByTenderIdOrderByFirstSeenAtAscIdAsc(other.getId())) {
			if (links.findByTenderIdAndUrl(kept.getId(), link.getUrl()).isEmpty()) {
				link.moveTo(kept.getId());
				movedLinks.add(link.getId());
			}
		}
		normalization.applyTakenNotices(kept, taken, now);
		// The history shows each notice of the source as read now: a rectification taken
		// for an initial notice before a fix of the source is shown as a rectification.
		Map<Long, String> relabeled = new HashMap<>();
		for (TenderNotice notice : kept.getNotices()) {
			if (notice.getSource().equals(kept.getSource())) {
				normalization.currentKind(rawNotices.findById(notice.getRawNoticeId()).orElseThrow())
					.filter(kind -> kind != notice.getKind())
					.ifPresent(kind -> {
						relabeled.put(notice.getId(), notice.getKind().name());
						notice.relabel(kind);
					});
			}
		}
		other.markMergedInto(kept.getId());
		return decisions.save(DuplicateDecision.merge(kept.getId(), other.getId(), reason, user, before,
				taken.stream().map(TenderNotice::getId).toList(), movedLinks, relabeled, now));
	}

	/**
	 * Two tenders a person says are different consultations: no longer
	 * proposed together. Nothing else changes.
	 */
	@Transactional
	public DuplicateDecision notDuplicates(Long firstId, Long secondId, String user) {
		Tender first = find(firstId);
		Tender second = find(secondId);
		check(first, second);
		String reason = String.join(" ", explain(List.of(first, second)));
		return decisions.save(DuplicateDecision.notDuplicate(first.getId(), second.getId(), reason, user,
				clock.instant()));
	}

	/**
	 * Undoes a decision. A merge gives back exactly the tender kept as it was
	 * before, and the notices and links to the duplicate, shown again. Refused
	 * when the tender kept has received another notice since: its state then
	 * depends on it, and must be checked by a person.
	 */
	@Transactional
	public DuplicateDecision undo(Long decisionId, String user) {
		DuplicateDecision decision = decisions.findById(decisionId)
			.orElseThrow(() -> DuplicateRuleException.notFound("Décision inconnue : " + decisionId));
		if (!decision.isActive()) {
			throw DuplicateRuleException.refused("Cette décision a déjà été annulée.");
		}
		if (decision.getKind() == DuplicateDecision.Kind.MERGE) {
			Tender kept = find(decision.getKeptTenderId());
			Tender other = find(decision.getOtherTenderId());
			TenderState before = jsonMapper.readValue(decision.getKeptBefore(), TenderState.class);
			Set<Long> expected = new HashSet<>(before.noticeIds());
			expected.addAll(decision.getMovedNoticeIds());
			if (kept.getNotices().stream().anyMatch(notice -> !expected.contains(notice.getId()))) {
				throw DuplicateRuleException.refused("La consultation " + kept.getId()
						+ " a reçu un nouvel avis depuis la fusion : l'annulation doit être vérifiée à la main.");
			}
			for (TenderNotice notice : List.copyOf(kept.getNotices())) {
				String previousKind = decision.getRelabeledKinds().get(notice.getId());
				if (previousKind != null) {
					notice.relabel(NoticeKind.valueOf(previousKind));
				}
				if (decision.getMovedNoticeIds().contains(notice.getId())) {
					other.takeNotice(notice);
				}
			}
			for (Long linkId : decision.getMovedLinkIds()) {
				links.findById(linkId).ifPresent(link -> link.moveTo(other.getId()));
			}
			kept.restore(before);
			other.unmerge();
		}
		decision.undo(user, clock.instant());
		return decision;
	}

	@Transactional(readOnly = true)
	public List<DuplicateDecision> history() {
		return decisions.findAllByOrderByDecidedAtDescIdDesc();
	}

	/**
	 * The merges of the groups certain enough to be decided together: two
	 * tenders of one source, a rectification explicitly citing the other, and
	 * no data of the team on the duplicate. Nothing is merged here.
	 */
	@Transactional(readOnly = true)
	public BatchPreview previewBatch() {
		List<BatchMerge> merges = new ArrayList<>();
		List<Group> groups = groups();
		for (Group group : groups) {
			if (!group.certain()) {
				continue;
			}
			Candidate other = group.tenders()
				.stream()
				.filter(candidate -> !candidate.id().equals(group.suggestedKeptId()))
				.findFirst()
				.orElseThrow();
			if (!other.teamData()) {
				merges.add(new BatchMerge(group.procedureId(), group.suggestedKeptId(), other.id(),
						String.join(" ", group.explanation())));
			}
		}
		return new BatchPreview(token(merges), merges, groups.size() - merges.size());
	}

	/**
	 * Makes the merges of a preview, only if they are still exactly those the
	 * person read (same token).
	 */
	@Transactional
	public List<DuplicateDecision> mergeBatch(String token, String user) {
		BatchPreview preview = previewBatch();
		if (!preview.token().equals(token)) {
			throw DuplicateRuleException.refused(
					"Les doublons ont changé depuis l'aperçu : relisez le nouveau rapport avant de confirmer.");
		}
		return preview.merges()
			.stream()
			.map(merge -> merge(merge.keptTenderId(), merge.mergedTenderId(), false, user))
			.toList();
	}

	private Group group(String procedureId, List<Tender> members) {
		List<String> explanation = explain(members);
		Optional<Long[]> citation = members.size() == 2 && members.get(0).getSource().equals(members.get(1).getSource())
				? citation(members) : Optional.empty();
		List<Tender> withData = members.stream().filter(this::hasTeamData).toList();
		Long suggested = withData.size() == 1 ? withData.getFirst().getId()
				: citation.map(pair -> pair[1]).orElse(members.getFirst().getId());
		return new Group(procedureId, citation.isPresent(), explanation, suggested,
				members.stream().map(this::candidate).toList());
	}

	/**
	 * Why the tenders are considered the same consultation, as read now from
	 * their notices.
	 */
	private List<String> explain(List<Tender> members) {
		List<String> lines = new ArrayList<>();
		lines.add("Même identifiant de procédure eForms (" + members.getFirst().getProcedureId() + ").");
		for (Tender tender : members) {
			for (Tender other : members) {
				if (tender != other) {
					citedNotice(tender, other).ifPresent(cited -> lines.add("La consultation " + tender.getId()
							+ " (" + tender.getSource() + " " + tender.getExternalId()
							+ ") a été créée par un rectificatif qui cite l'avis " + cited + " de la consultation "
							+ other.getId() + "."));
				}
			}
		}
		if (members.stream().map(Tender::getSource).distinct().count() > 1) {
			lines.add("Publiée par plusieurs sources.");
		}
		return lines;
	}

	/**
	 * The pair (made by a rectification, cited) when one tender of the two was
	 * made by a rectification citing a notice of the other.
	 */
	private Optional<Long[]> citation(List<Tender> members) {
		for (Tender tender : members) {
			for (Tender other : members) {
				if (tender != other && citedNotice(tender, other).isPresent()) {
					return Optional.of(new Long[] { tender.getId(), other.getId() });
				}
			}
		}
		return Optional.empty();
	}

	/**
	 * The notice of the other tender that the notice which made this tender
	 * cites, when that notice is a rectification as read now.
	 */
	private Optional<String> citedNotice(Tender tender, Tender other) {
		Optional<TenderNotice> first = tender.getNotices()
			.stream()
			.filter(notice -> notice.getExternalId().equals(tender.getExternalId())
					&& notice.getSource().equals(tender.getSource()))
			.findFirst();
		if (first.isEmpty()) {
			return Optional.empty();
		}
		SourceNotice read = normalization.read(rawNotices.findById(first.get().getRawNoticeId()).orElseThrow());
		if (read.type() != NoticeType.COMPLETE_RECTIFICATION && read.type() != NoticeType.RECTIFICATION) {
			return Optional.empty();
		}
		return other.getNotices()
			.stream()
			.filter(notice -> notice.getSource().equals(tender.getSource()))
			.map(TenderNotice::getExternalId)
			.filter(read.linkedIds()::contains)
			.findFirst();
	}

	private Candidate candidate(Tender tender) {
		return new Candidate(tender.getId(), tender.getSource(), tender.getExternalId(), tender.getTitle(),
				tender.getBuyer(), tender.getPublishedAt(), tender.getDeadline(), tender.getStatus(),
				tender.getFollowUpStatus(), tender.getFollowUpComment(), hasTeamData(tender),
				tender.getNotices()
					.stream()
					.map(notice -> new DuplicateResponses.Notice(notice.getSource(), notice.getExternalId(),
							notice.getKind(), notice.getPublishedAt(), notice.getDeadline()))
					.toList());
	}

	private boolean hasTeamData(Tender tender) {
		return tender.getFollowUpStatus() != FollowUpStatus.NEW || tender.getFollowUpComment() != null
				|| follows.existsByTenderId(tender.getId()) || dossiers.existsByTenderId(tender.getId());
	}

	private void check(Tender first, Tender second) {
		if (first.getId().equals(second.getId())) {
			throw DuplicateRuleException.refused("Choisissez deux consultations différentes.");
		}
		if (first.getMergedIntoId() != null || second.getMergedIntoId() != null) {
			throw DuplicateRuleException.refused("L'une des consultations est déjà fusionnée.");
		}
		if (first.getProcedureId() == null || !first.getProcedureId().equals(second.getProcedureId())) {
			throw DuplicateRuleException.refused(
					"Les deux consultations n'ont pas le même identifiant de procédure : aucune fusion possible.");
		}
	}

	private Tender find(Long id) {
		return tenders.findById(id)
			.orElseThrow(() -> DuplicateRuleException.notFound("Consultation inconnue : " + id));
	}

	private static String token(List<BatchMerge> merges) {
		StringBuilder content = new StringBuilder();
		merges.forEach(merge -> content.append(merge.keptTenderId()).append(':').append(merge.mergedTenderId())
			.append(';'));
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(content.toString().getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
