package eu.gpefrance.tenders.memoir;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import eu.gpefrance.tenders.analysis.AiAssistant;
import eu.gpefrance.tenders.company.CompanyDirectory;
import eu.gpefrance.tenders.memoir.MemoirStore.MemoirRow;
import eu.gpefrance.tenders.memoir.MemoirStore.RequirementRow;
import eu.gpefrance.tenders.memoir.MemoirStore.SectionRow;
import eu.gpefrance.tenders.tender.Tender;
import eu.gpefrance.tenders.tender.TenderNotFoundException;
import eu.gpefrance.tenders.tender.TenderRepository;

/**
 * The memoirs and their outline, written by the users, helped by the AI and
 * by the data of the company. Nothing written is lost: a content replaced is
 * kept as a revision, and the text of the AI or of the company never replaces
 * a content without the user: it waits as a proposal. Every user reads and
 * writes the memoirs, like the tenders.
 */
@Service
public class MemoirService {

	private static final Logger log = LoggerFactory.getLogger(MemoirService.class);

	/**
	 * The outline usual in a technical memoir of works, when no analysis is
	 * made: a starting point, changed by the user.
	 */
	private static final List<String[]> TEMPLATE = List.of(
			new String[] { "Présentation de l'entreprise", "Identité, activités, certifications et qualifications utiles au marché." },
			new String[] { "Compréhension du marché et des enjeux", "Lecture du besoin de l'acheteur, contraintes du site, points sensibles." },
			new String[] { "Organisation et moyens humains", "Équipe affectée au chantier, encadrement, qualifications et habilitations." },
			new String[] { "Moyens matériels", "Véhicules, matériel et outillage mobilisés pour ce marché." },
			new String[] { "Méthodologie d'exécution", "Phasage, modes opératoires, interfaces avec les autres lots, continuité de service." },
			new String[] { "Planning et délais", "Durée, jalons, engagement sur les délais du DCE." },
			new String[] { "Qualité, sécurité et environnement", "Contrôles, autocontrôles, prévention des risques, gestion des déchets." },
			new String[] { "Références", "Chantiers comparables réalisés par l'entreprise." });

	private final MemoirStore store;

	private final MemoirFiles files;

	private final MemoirAi ai;

	private final CompanyDirectory company;

	private final TenderRepository tenders;

	private final Clock clock;

	public MemoirService(MemoirStore store, MemoirFiles files, MemoirAi ai, CompanyDirectory company,
			TenderRepository tenders, Clock clock) {
		this.store = store;
		this.files = files;
		this.ai = ai;
		this.company = company;
		this.tenders = tenders;
		this.clock = clock;
	}

	/**
	 * Everything the workspace shows.
	 * @param documents the documents of the consultation file the analysis reads
	 * @param ai whether the AI may be called, and why not
	 */
	public record Workspace(MemoirRow memoir, List<SectionRow> sections, List<RequirementRow> requirements,
			MemoirStore.JobRow job, List<MemoirAi.SourceDocument> documents, AiAssistant.Availability ai) {
	}

	public List<MemoirRow> list() {
		return store.memoirs();
	}

	public Workspace workspace(Long id) {
		MemoirRow memoir = found(id);
		return new Workspace(memoir, store.sections(id), store.requirements(id), store.lastJob(id).orElse(null),
				ai.sources(memoir.tenderId()), ai.availability());
	}

	@Transactional
	public MemoirRow create(String title, Long tenderId, String lotNumber, String user) {
		String lot = clean(lotNumber);
		if (tenderId != null) {
			Tender tender = tenders.findById(tenderId).orElseThrow(() -> new TenderNotFoundException(tenderId));
			if (lot != null && tender.getLots().stream().noneMatch(candidate -> lot.equals(candidate.getNumber()))) {
				throw MemoirRuleException.invalid("Ce lot n'appartient pas à l'appel d'offres.");
			}
			if (title == null || title.isBlank()) {
				title = "Mémoire technique – " + tender.getTitle();
			}
		}
		else if (lot != null) {
			throw MemoirRuleException.invalid("Un lot ne se choisit qu'avec son appel d'offres.");
		}
		if (title == null || title.isBlank()) {
			throw MemoirRuleException.invalid("Donnez un titre au mémoire ou choisissez son appel d'offres.");
		}
		Long id = store.insertMemoir(tenderId, lot, cut(title.strip(), 300), user, clock.instant());
		log.info("Memoir {} created by {} (tender {})", id, user, tenderId);
		return found(id);
	}

	@Transactional
	public MemoirRow update(Long id, String title, String lotNumber, MemoirStatus status, String user) {
		MemoirRow memoir = found(id);
		if (title == null || title.isBlank()) {
			throw MemoirRuleException.invalid("Le titre est obligatoire.");
		}
		if (status == MemoirStatus.READY_TO_WRITE && memoir.outlineValidatedAt() == null) {
			throw MemoirRuleException.invalid("Validez le sommaire pour passer à la rédaction.");
		}
		store.updateMemoir(id, cut(title.strip(), 300), memoir.tenderId() == null ? null : clean(lotNumber),
				status == null ? memoir.status() : status, user, clock.instant());
		return found(id);
	}

	@Transactional
	public void delete(Long id, String user) {
		found(id);
		files.deleteAfterCommit(store.filePaths(id));
		store.deleteMemoir(id);
		log.info("Memoir {} deleted by {}", id, user);
	}

	// Outline

	/** The usual parts of a technical memoir, for an empty outline. */
	@Transactional
	public Workspace template(Long id, String user) {
		found(id);
		if (store.sections(id).stream().anyMatch(section -> !section.proposed())) {
			throw MemoirRuleException.conflict("Le sommaire n'est pas vide : ajoutez les parties une à une.");
		}
		TEMPLATE.forEach(part -> store.insertSection(id, null, part[0], part[1], false, user, clock.instant()));
		changed(id, user);
		return workspace(id);
	}

	@Transactional
	public Workspace addSection(Long id, Long parentId, String title, String description, String user) {
		found(id);
		if (parentId != null) {
			SectionRow parent = section(parentId);
			if (!parent.memoirId().equals(id)) {
				throw MemoirRuleException.invalid("Cette partie n'appartient pas au mémoire.");
			}
			if (parent.level() >= 4) {
				throw MemoirRuleException.invalid("Le sommaire compte quatre niveaux au plus.");
			}
		}
		store.insertSection(id, parentId, cut(title.strip(), 300), clean(description), false, user, clock.instant());
		changed(id, user);
		return workspace(id);
	}

	@Transactional
	public Workspace renameSection(Long sectionId, String title, String description, String user) {
		SectionRow section = section(sectionId);
		store.updateSection(sectionId, cut(title.strip(), 300), clean(description), user, clock.instant());
		changed(section.memoirId(), user);
		return workspace(section.memoirId());
	}

	/** Removes a part, its sub-parts and their content. */
	@Transactional
	public Workspace deleteSection(Long sectionId, String user) {
		SectionRow section = section(sectionId);
		store.deleteSection(sectionId);
		changed(section.memoirId(), user);
		return workspace(section.memoirId());
	}

	/** A part proposed by the AI joins the outline. */
	@Transactional
	public Workspace acceptSection(Long sectionId, String user) {
		SectionRow section = section(sectionId);
		if (section.parentId() != null && section(section.parentId()).proposed()) {
			throw MemoirRuleException.invalid("Acceptez d'abord la partie qui la contient.");
		}
		store.acceptSection(sectionId);
		changed(section.memoirId(), user);
		return workspace(section.memoirId());
	}

	/** Accepts every part proposed by the AI. */
	@Transactional
	public Workspace acceptAllSections(Long id, String user) {
		found(id);
		store.sections(id).stream().filter(SectionRow::proposed).forEach(section -> store.acceptSection(section.id()));
		changed(id, user);
		return workspace(id);
	}

	public record Placement(Long id, Long parentId, int position) {
	}

	/** The whole outline placed again (moved up, down, in or out): every section exactly once, no loop. */
	@Transactional
	public Workspace reorder(Long id, List<Placement> placements, String user) {
		found(id);
		List<SectionRow> sections = store.sections(id);
		Set<Long> ids = new HashSet<>(sections.stream().map(SectionRow::id).toList());
		Map<Long, Long> parents = new HashMap<>();
		for (Placement placement : placements) {
			if (!ids.contains(placement.id()) || placement.parentId() != null && !ids.contains(placement.parentId())
					|| parents.put(placement.id(), placement.parentId()) != null) {
				throw MemoirRuleException.invalid("Le sommaire envoyé ne correspond pas à ce mémoire.");
			}
		}
		if (parents.size() != ids.size()) {
			throw MemoirRuleException.invalid("Le sommaire envoyé doit citer chaque partie une fois.");
		}
		for (Long section : parents.keySet()) {
			int depth = 0;
			for (Long parent = parents.get(section); parent != null; parent = parents.get(parent)) {
				if (parent.equals(section) || ++depth > 3) {
					throw MemoirRuleException.invalid("Le sommaire compte quatre niveaux au plus, sans boucle.");
				}
			}
		}
		placements.forEach(placement -> store.place(placement.id(), placement.parentId(), placement.position()));
		changed(id, user);
		return workspace(id);
	}

	/** The outline becomes the frame of the writing. */
	@Transactional
	public Workspace validateOutline(Long id, String user) {
		found(id);
		List<SectionRow> sections = store.sections(id);
		if (sections.stream().noneMatch(section -> !section.proposed())) {
			throw MemoirRuleException.invalid("Le sommaire est vide.");
		}
		long proposed = sections.stream().filter(SectionRow::proposed).count();
		if (proposed > 0) {
			throw MemoirRuleException.invalid(proposed + " partie(s) proposée(s) par l'IA attendent votre décision.");
		}
		store.validateOutline(id, user, clock.instant());
		return workspace(id);
	}

	@Transactional
	public Workspace linkRequirements(Long sectionId, List<Long> requirementIds, String user) {
		SectionRow section = section(sectionId);
		Set<Long> known = new HashSet<>(store.requirements(section.memoirId()).stream().map(RequirementRow::id).toList());
		if (!known.containsAll(requirementIds)) {
			throw MemoirRuleException.invalid("Exigence inconnue pour ce mémoire.");
		}
		store.linkRequirements(sectionId, List.copyOf(new HashSet<>(requirementIds)));
		store.touch(section.memoirId(), user, clock.instant());
		return workspace(section.memoirId());
	}

	@Transactional
	public Workspace addRequirement(Long id, String category, String text, String user) {
		found(id);
		store.insertRequirement(id, MemoirAi.CATEGORIES.contains(category) ? category : "OTHER", cut(text.strip(), 1000),
				null, null, null, null, false, "USER");
		store.touch(id, user, clock.instant());
		return workspace(id);
	}

	@Transactional
	public Workspace deleteRequirement(Long id, Long requirementId, String user) {
		found(id);
		store.deleteRequirement(id, requirementId);
		store.touch(id, user, clock.instant());
		return workspace(id);
	}

	// Content

	/** What a user typed: saved as it is, cleaned; what it replaces from the AI or the company is kept. */
	@Transactional
	public SectionRow saveContent(Long sectionId, String html, String user) {
		SectionRow section = section(sectionId);
		store.saveManual(sectionId, MemoirHtml.clean(html), user, clock.instant());
		store.touch(section.memoirId(), user, clock.instant());
		return section(sectionId);
	}

	/**
	 * The proposal of the section replaces its content, or follows it; the
	 * former content is kept as a revision.
	 */
	@Transactional
	public SectionRow acceptProposal(Long sectionId, boolean append, String user) {
		SectionRow section = section(sectionId);
		if (section.proposal() == null) {
			throw MemoirRuleException.conflict("Aucune proposition en attente pour cette section.");
		}
		String content = append && section.content() != null ? section.content() + section.proposal() : section.proposal();
		store.setContent(sectionId, content, append ? "MANUAL" : origin(section.proposalNote()), user, clock.instant());
		store.setProposal(sectionId, null, null, user, clock.instant());
		store.touch(section.memoirId(), user, clock.instant());
		return section(sectionId);
	}

	@Transactional
	public SectionRow rejectProposal(Long sectionId, String user) {
		section(sectionId);
		store.setProposal(sectionId, null, null, user, clock.instant());
		return section(sectionId);
	}

	public List<MemoirStore.RevisionRow> revisions(Long sectionId) {
		section(sectionId);
		return store.revisions(sectionId);
	}

	/** A former content comes back as a proposal: the user decides. */
	@Transactional
	public SectionRow restore(Long sectionId, Long revisionId, String user) {
		section(sectionId);
		MemoirStore.RevisionRow revision = store.revision(sectionId, revisionId)
			.orElseThrow(() -> MemoirRuleException.notFound("Version introuvable."));
		store.setProposal(sectionId, revision.content(), "Version précédente", user, clock.instant());
		return section(sectionId);
	}

	/**
	 * A block written from the data of the company: the content of an empty
	 * section, a proposal otherwise.
	 */
	@Transactional
	public SectionRow insertCompany(Long sectionId, CompanyBlocks.Block block, String user) {
		SectionRow section = section(sectionId);
		String html = CompanyBlocks.html(block, company.snapshot(LocalDate.now(clock)));
		place(section, html, "Données de l'entreprise", "COMPANY", user);
		return section(sectionId);
	}

	/** One request to the AI on a section, answered as its content when empty, as a proposal otherwise. */
	public SectionRow askAi(Long sectionId, MemoirAi.Action action, String instruction, Long requirementId, String user) {
		SectionRow section = section(sectionId);
		MemoirRow memoir = found(section.memoirId());
		List<RequirementRow> requirements = store.requirements(memoir.id());
		RequirementRow requirement = requirementId == null ? null
				: requirements.stream()
					.filter(candidate -> candidate.id().equals(requirementId))
					.findFirst()
					.orElseThrow(() -> MemoirRuleException.notFound("Exigence introuvable."));
		if (action == MemoirAi.Action.ADAPT && requirement == null) {
			throw MemoirRuleException.invalid("Choisissez l'exigence du DCE à laquelle adapter le texte.");
		}
		MemoirAi.Written written = ai.act(memoir, store.sections(memoir.id()), section, requirements, action,
				clean(instruction), requirement, user);
		if (written.html() == null) {
			throw MemoirRuleException.invalid("L'IA n'a rien proposé"
					+ (written.missing().isEmpty() ? "." : " : " + String.join(" ; ", written.missing())));
		}
		String note = "Proposition de l'IA" + (written.missing().isEmpty() ? ""
				: " — à compléter : " + String.join(" ; ", written.missing()));
		place(section, written.html(), note, "AI", user);
		return section(sectionId);
	}

	/** An image for the memoir; the editor puts it where the user wants. */
	@Transactional
	public Long uploadImage(Long id, MultipartFile file, String user) {
		found(id);
		MemoirFiles.Stored stored = files.storeImage(id, file);
		return store.insertFile(id, stored.fileName(), stored.relativePath(), stored.size(), user, clock.instant());
	}

	MemoirStore.FileRow file(Long fileId) {
		return store.file(fileId).orElseThrow(() -> MemoirRuleException.notFound("Image introuvable."));
	}

	MemoirRow found(Long id) {
		return store.memoir(id).orElseThrow(() -> MemoirRuleException.notFound("Mémoire introuvable."));
	}

	private SectionRow section(Long id) {
		return store.section(id).orElseThrow(() -> MemoirRuleException.notFound("Partie introuvable."));
	}

	/** Written in an empty section; else waiting for the user, never written over their text. */
	private void place(SectionRow section, String html, String note, String origin, String user) {
		if (!store.fillIfEmpty(section.id(), html, origin, user, clock.instant())) {
			store.setProposal(section.id(), html, note, user, clock.instant());
		}
		store.touch(section.memoirId(), user, clock.instant());
	}

	private void changed(Long id, String user) {
		store.outlineChanged(id);
		store.touch(id, user, clock.instant());
	}

	private static String origin(String note) {
		if (note == null) {
			return "MANUAL";
		}
		return note.startsWith("Données") ? "COMPANY" : note.startsWith("Version") ? "MANUAL" : "AI";
	}

	private static String clean(String text) {
		return text == null || text.isBlank() ? null : text.strip();
	}

	private static String cut(String text, int length) {
		return text.length() <= length ? text : text.substring(0, length);
	}


}
