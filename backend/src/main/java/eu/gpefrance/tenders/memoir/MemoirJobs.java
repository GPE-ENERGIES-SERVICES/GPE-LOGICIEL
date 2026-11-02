package eu.gpefrance.tenders.memoir;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.ErrorResponseException;

import eu.gpefrance.tenders.memoir.MemoirStore.MemoirRow;
import eu.gpefrance.tenders.memoir.MemoirStore.SectionRow;
import jakarta.annotation.PreDestroy;

/**
 * The work of the AI on a memoir, one step after the other in the
 * background: the analysis of the consultation file (one step), the writing
 * of the sections (one step each). One work at a time per memoir and one at
 * a time for the whole application: no load on the server beyond a call to
 * the provider. Its progress is read by the interface, which may stop it
 * between two steps. A section already written is never written over: the
 * text of the AI waits as a proposal.
 */
@Component
class MemoirJobs {

	private static final Logger log = LoggerFactory.getLogger(MemoirJobs.class);

	private final MemoirStore store;

	private final MemoirAi ai;

	private final MemoirProperties properties;

	private final Clock clock;

	private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "memoir-ai");
		thread.setDaemon(true);
		return thread;
	});

	MemoirJobs(MemoirStore store, MemoirAi ai, MemoirProperties properties, Clock clock) {
		this.store = store;
		this.ai = ai;
		this.properties = properties;
		this.clock = clock;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void interruptLeftOver() {
		int interrupted = store.interruptRunningJobs(clock.instant());
		if (interrupted > 0) {
			log.warn("{} memoir work(s) left running before the restart marked as interrupted", interrupted);
		}
	}

	@PreDestroy
	void stop() {
		executor.shutdownNow();
	}

	/** Reads the consultation file and proposes requirements and an outline, to be accepted part by part. */
	void startAnalysis(MemoirRow memoir, String user) {
		requireAi();
		if (memoir.tenderId() == null) {
			throw MemoirRuleException.invalid("Associez d'abord ce mémoire à un appel d'offres : son DCE sera analysé.");
		}
		Long jobId = store.startJob(memoir.id(), "ANALYSIS", 1, user, clock.instant());
		run(jobId, () -> {
			MemoirAi.Analysis analysis = ai.analyse(memoir, user);
			store.deleteAiRequirements(memoir.id());
			store.deleteProposedSections(memoir.id());
			List<Long> requirementIds = new ArrayList<>();
			for (MemoirAi.Requirement requirement : analysis.requirements()) {
				requirementIds.add(store.insertRequirement(memoir.id(), requirement.category(), requirement.text(),
						requirement.weight(), requirement.documentId(), requirement.page(), requirement.quote(),
						requirement.verified(), "AI"));
			}
			// Levels read in order: each part under the last part of the level above.
			Long[] parents = new Long[5];
			for (MemoirAi.Part part : analysis.outline()) {
				int level = part.level() > 1 && parents[part.level() - 1] == null ? 1 : part.level();
				Long id = store.insertSection(memoir.id(), level == 1 ? null : parents[level - 1], part.title(),
						part.description(), true, user, clock.instant());
				parents[level] = id;
				for (int deeper = level + 1; deeper < parents.length; deeper++) {
					parents[deeper] = null;
				}
				store.linkRequirements(id, part.requirements().stream().map(requirementIds::get).toList());
			}
			store.outlineChanged(memoir.id());
			store.progress(jobId, null, 1);
			List<String> notes = new ArrayList<>(analysis.missing());
			analysis.notSent().forEach(document -> notes.add("Non transmis : " + document));
			return notes.isEmpty() ? null : String.join("\n", notes);
		});
	}

	/**
	 * Writes the sections without content of a validated outline, in reading
	 * order: the last level of each part (a part with sub-parts is not written
	 * itself).
	 * @param sectionIds only these sections, or all when empty
	 */
	void startWriting(MemoirRow memoir, List<Long> sectionIds, String user) {
		requireAi();
		if (memoir.outlineValidatedAt() == null) {
			throw MemoirRuleException.invalid("Validez d'abord le sommaire : la rédaction suit le sommaire validé.");
		}
		List<SectionRow> outline = store.sections(memoir.id());
		Set<Long> parents = outline.stream()
			.filter(section -> !section.proposed() && section.parentId() != null)
			.map(SectionRow::parentId)
			.collect(Collectors.toSet());
		List<SectionRow> targets = outline.stream()
			.filter(section -> !section.proposed() && section.content() == null && !parents.contains(section.id()))
			.filter(section -> sectionIds.isEmpty() || sectionIds.contains(section.id()))
			.toList();
		if (targets.isEmpty()) {
			throw MemoirRuleException.invalid("Toutes les sections ont déjà un texte : demandez plutôt une réécriture "
					+ "section par section.");
		}
		Long jobId = store.startJob(memoir.id(), "WRITING", targets.size(), user, clock.instant());
		store.setStatus(memoir.id(), MemoirStatus.WRITING);
		run(jobId, () -> {
			List<String> missing = new ArrayList<>();
			int done = 0;
			for (SectionRow target : targets) {
				if (store.cancelRequested(jobId)) {
					throw new Cancelled();
				}
				store.progress(jobId, target.id(), done);
				MemoirAi.Written written = ai.write(memoir, store.sections(memoir.id()), target,
						store.requirements(memoir.id()), user);
				if (written.html() != null && !store.fillIfEmpty(target.id(), written.html(), "AI", user, clock.instant())) {
					// Written by a user meanwhile: the text of the AI waits for them.
					store.setProposal(target.id(), written.html(), "Rédaction proposée par l'IA", user, clock.instant());
				}
				written.missing().forEach(item -> missing.add(target.number() + " " + target.title() + " : " + item));
				store.progress(jobId, null, ++done);
			}
			store.setStatus(memoir.id(), MemoirStatus.TO_REVIEW);
			return missing.isEmpty() ? null : "À compléter :\n" + String.join("\n", missing);
		});
	}

	/** Stops the work after its current step. */
	void cancel(Long memoirId) {
		if (!store.requestCancel(memoirId)) {
			throw MemoirRuleException.conflict("Aucun travail de l'IA en cours sur ce mémoire.");
		}
	}

	private void requireAi() {
		var availability = ai.availability();
		if (!availability.available()) {
			throw MemoirRuleException.conflict(availability.message());
		}
	}

	private interface Work {

		/** @return a message for the user, or null */
		String run();

	}

	private static final class Cancelled extends RuntimeException {

		Cancelled() {
			super(null, null, false, false);
		}

	}

	private void run(Long jobId, Work work) {
		Runnable task = () -> {
			try {
				store.finishJob(jobId, "DONE", work.run(), clock.instant());
			}
			catch (Cancelled ex) {
				store.finishJob(jobId, "CANCELLED", "Arrêté à la demande : le texte déjà rédigé est conservé.",
						clock.instant());
			}
			catch (ErrorResponseException ex) {
				store.finishJob(jobId, "FAILED", ex.getBody().getDetail(), clock.instant());
			}
			catch (RuntimeException ex) {
				log.warn("Memoir work {} failed: {}", jobId, ex.getClass().getSimpleName());
				store.finishJob(jobId, "FAILED", "Erreur inattendue : le travail a été arrêté, rien n'est perdu.",
						clock.instant());
			}
		};
		if (properties.async()) {
			executor.submit(task);
		}
		else {
			task.run();
		}
	}

}
