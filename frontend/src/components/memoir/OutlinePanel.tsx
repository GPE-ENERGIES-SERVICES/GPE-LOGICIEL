"use client";

import { useState, type FormEvent } from "react";

import { moved, REQUIREMENT_LABELS } from "@/lib/memoir";
import {
  acceptAllSections,
  acceptSection,
  addRequirement,
  addSection,
  applyTemplate,
  deleteRequirement,
  deleteSection,
  linkRequirements,
  renameSection,
  reorderOutline,
  startAnalysis,
  validateOutline,
} from "@/services/memoirApi";
import type { MemoirSection, MemoirWorkspace, RequirementCategory } from "@/types/memoir";

type Props = {
  workspace: MemoirWorkspace;
  onChanged: (workspace: MemoirWorkspace) => void;
  onError: (message: string | null) => void;
  onShow: (sectionId: number) => void;
};

/**
 * The preparation of the memoir: the requirements read in the consultation
 * file and their coverage, and the outline the user builds, changes and
 * validates; the parts proposed by the AI wait for their decision.
 */
export function OutlinePanel({ workspace, onChanged, onError, onShow }: Props) {
  const [tab, setTab] = useState<"outline" | "requirements">("outline");
  const [editing, setEditing] = useState<number | "new" | null>(null);
  const [adding, setAdding] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const { memoir, sections, requirements, job, ai, documents } = workspace;
  const running = job?.status === "RUNNING";
  const proposed = sections.filter((section) => section.proposed).length;
  const covered = new Set(sections.filter((section) => !section.proposed).flatMap((section) => section.requirementIds));
  const uncovered = requirements.filter((requirement) => !covered.has(requirement.id)).length;

  const run = async (action: () => Promise<MemoirWorkspace>) => {
    setBusy(true);
    onError(null);
    try {
      onChanged(await action());
      return true;
    } catch (err) {
      onError(err instanceof Error ? err.message : "Action impossible.");
      return false;
    } finally {
      setBusy(false);
    }
  };

  const move = (section: MemoirSection, direction: "up" | "down" | "in" | "out") => {
    const placements = moved(sections, section.id, direction);
    if (placements) {
      run(() => reorderOutline(memoir.id, placements));
    }
  };

  const documentName = (id: number | null) => documents.find((document) => document.id === id)?.fileName;

  return (
    <aside className="memoir-panel" aria-label="Préparation du mémoire">
      <div className="memoir-panel-tabs" role="tablist">
        <button type="button" role="tab" aria-selected={tab === "outline"} onClick={() => setTab("outline")}>
          Sommaire
          {proposed > 0 && <span className="nav-badge">{proposed}</span>}
        </button>
        <button type="button" role="tab" aria-selected={tab === "requirements"} onClick={() => setTab("requirements")}>
          Exigences du DCE
          {uncovered > 0 && <span className="nav-badge">{uncovered}</span>}
        </button>
      </div>

      {memoir.tenderId && (
        <div className="memoir-panel-block">
          <button
            type="button"
            className="primary"
            disabled={busy || running || !ai.available || documents.length === 0}
            title={!ai.available ? ai.message : documents.length === 0 ? "Aucun document lu dans le DCE" : undefined}
            onClick={() => {
              if (
                sections.length === 0 ||
                window.confirm("Relancer l'analyse ? Les exigences et les parties proposées par l'IA seront remplacées ; vos parties restent.")
              ) {
                run(() => startAnalysis(memoir.id));
              }
            }}
          >
            {running && job?.kind === "ANALYSIS" ? "Analyse du DCE en cours…" : "Analyser le DCE et proposer un sommaire"}
          </button>
          {!ai.available && <p className="hint">IA non disponible : {ai.message}</p>}
          {ai.available && documents.length === 0 && (
            <p className="hint">Aucun document lu dans le DCE : déposez-le dans l&apos;appel d&apos;offres.</p>
          )}
          {ai.available && documents.length > 0 && (
            <p className="hint">{documents.length} document(s) du DCE seront lus (RC, CCTP, CCAP…).</p>
          )}
        </div>
      )}

      {tab === "outline" ? (
        <div className="memoir-panel-body">
          {sections.length === 0 && (
            <div className="memoir-panel-block">
              <p className="muted">Sommaire vide.</p>
              <button type="button" disabled={busy} onClick={() => run(() => applyTemplate(memoir.id))}>
                Partir du sommaire type
              </button>
            </div>
          )}
          {proposed > 0 && (
            <p className="banner info">
              {proposed} partie(s) proposée(s) par l&apos;IA.{" "}
              <button type="button" className="link-button" disabled={busy} onClick={() => run(() => acceptAllSections(memoir.id))}>
                Tout accepter
              </button>
            </p>
          )}
          <ol className="memoir-outline">
            {sections.map((section) => (
              <li
                key={section.id}
                className={`level-${section.level}${section.proposed ? " proposed" : ""}`}
              >
                {editing === section.id ? (
                  <SectionForm
                    section={section}
                    requirements={requirements}
                    busy={busy}
                    onCancel={() => setEditing(null)}
                    onSubmit={async (title, description, requirementIds) => {
                      const ok = await run(() => renameSection(section.id, { title, description }));
                      if (ok && requirementIds.join() !== section.requirementIds.join()) {
                        await run(() => linkRequirements(section.id, requirementIds));
                      }
                      if (ok) {
                        setEditing(null);
                      }
                    }}
                  />
                ) : (
                  <>
                    <div className="memoir-outline-line">
                      <span
                        className={`memoir-dot ${section.proposal ? "proposal" : section.content ? "written" : "empty"}`}
                        title={section.proposal ? "Proposition en attente" : section.content ? "Rédigée" : "À rédiger"}
                      />
                      <button type="button" className="link-button memoir-outline-title" onClick={() => onShow(section.id)}>
                        {section.number && <span className="memoir-number">{section.number}</span>} {section.title}
                      </button>
                    </div>
                    {section.description && <p className="memoir-outline-description">{section.description}</p>}
                    {section.proposed ? (
                      <div className="memoir-outline-actions visible">
                        <button type="button" disabled={busy} onClick={() => run(() => acceptSection(section.id))}>
                          Accepter
                        </button>
                        <button type="button" className="secondary" disabled={busy} onClick={() => run(() => deleteSection(section.id))}>
                          Refuser
                        </button>
                      </div>
                    ) : (
                      <div className="memoir-outline-actions">
                        <button type="button" className="secondary" title="Modifier" onClick={() => setEditing(section.id)}>
                          Modifier
                        </button>
                        <button type="button" className="secondary" title="Ajouter une sous-partie" disabled={section.level >= 4} onClick={() => setAdding(section.id)}>
                          + Sous-partie
                        </button>
                        <button type="button" className="secondary" aria-label="Monter" disabled={busy} onClick={() => move(section, "up")}>
                          ↑
                        </button>
                        <button type="button" className="secondary" aria-label="Descendre" disabled={busy} onClick={() => move(section, "down")}>
                          ↓
                        </button>
                        <button type="button" className="secondary" aria-label="Décaler à droite" title="Faire une sous-partie de la précédente" disabled={busy} onClick={() => move(section, "in")}>
                          →
                        </button>
                        <button type="button" className="secondary" aria-label="Décaler à gauche" disabled={busy || section.parentId === null} onClick={() => move(section, "out")}>
                          ←
                        </button>
                        <button
                          type="button"
                          className="secondary"
                          aria-label="Supprimer"
                          disabled={busy}
                          onClick={() => {
                            if (window.confirm(`Supprimer « ${section.title} », ses sous-parties et leur texte ?`)) {
                              run(() => deleteSection(section.id));
                            }
                          }}
                        >
                          ✕
                        </button>
                      </div>
                    )}
                  </>
                )}
                {adding === section.id && (
                  <SectionForm
                    busy={busy}
                    onCancel={() => setAdding(null)}
                    onSubmit={async (title, description) => {
                      if (await run(() => addSection(memoir.id, { parentId: section.id, title, description }))) {
                        setAdding(null);
                      }
                    }}
                  />
                )}
              </li>
            ))}
          </ol>
          {editing === "new" ? (
            <SectionForm
              busy={busy}
              onCancel={() => setEditing(null)}
              onSubmit={async (title, description) => {
                if (await run(() => addSection(memoir.id, { parentId: null, title, description }))) {
                  setEditing(null);
                }
              }}
            />
          ) : (
            <button type="button" className="memoir-add" onClick={() => setEditing("new")}>
              + Ajouter une partie
            </button>
          )}
          {sections.length > 0 && (
            <div className="memoir-panel-block">
              {memoir.outlineValidatedAt ? (
                <p className="success">Sommaire validé par {memoir.outlineValidatedBy}.</p>
              ) : (
                <button type="button" className="primary" disabled={busy || proposed > 0} onClick={() => run(() => validateOutline(memoir.id))}>
                  Valider le sommaire
                </button>
              )}
              {uncovered > 0 && <p className="hint">{uncovered} exigence(s) du DCE ne sont rattachées à aucune partie.</p>}
            </div>
          )}
        </div>
      ) : (
        <div className="memoir-panel-body">
          {requirements.length === 0 && (
            <p className="muted">
              Aucune exigence relevée. Lancez l&apos;analyse du DCE, ou ajoutez-les à la main depuis le règlement de consultation.
            </p>
          )}
          <ul className="memoir-requirements">
            {requirements.map((requirement) => (
              <li key={requirement.id}>
                <div>
                  <span className="badge">{REQUIREMENT_LABELS[requirement.category]}</span>
                  {requirement.weight && <span className="badge">{requirement.weight}</span>}
                  {!covered.has(requirement.id) && <span className="badge urgent">Non couverte</span>}
                </div>
                <p>{requirement.text}</p>
                {requirement.documentId && (
                  <p className="hint">
                    {documentName(requirement.documentId) ?? `Document ${requirement.documentId}`}, page {requirement.page}
                    {requirement.verified ? " · citation retrouvée" : ""}
                  </p>
                )}
                {requirement.origin === "AI" && !requirement.verified && (
                  <p className="hint error">Citation non retrouvée dans le DCE : à vérifier.</p>
                )}
                {requirement.quote && <blockquote>« {requirement.quote} »</blockquote>}
                <button
                  type="button"
                  className="secondary"
                  disabled={busy}
                  onClick={() => run(() => deleteRequirement(memoir.id, requirement.id))}
                >
                  Supprimer
                </button>
              </li>
            ))}
          </ul>
          <RequirementForm
            busy={busy}
            onSubmit={(category, text) => run(() => addRequirement(memoir.id, category, text))}
          />
          {job?.kind === "ANALYSIS" && job.message && (
            <div className="memoir-panel-block">
              <strong>Signalé par l&apos;analyse</strong>
              <p className="hint memoir-message">{job.message}</p>
            </div>
          )}
        </div>
      )}
    </aside>
  );
}

function SectionForm({
  section,
  requirements,
  busy,
  onSubmit,
  onCancel,
}: {
  section?: MemoirSection;
  requirements?: MemoirWorkspace["requirements"];
  busy: boolean;
  onSubmit: (title: string, description: string | null, requirementIds: number[]) => void;
  onCancel: () => void;
}) {
  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    onSubmit(
      String(form.get("title") ?? "").trim(),
      String(form.get("description") ?? "").trim() || null,
      form.getAll("requirement").map(Number),
    );
  };
  return (
    <form className="memoir-section-form" onSubmit={submit}>
      <input name="title" required maxLength={300} defaultValue={section?.title ?? ""} placeholder="Titre de la partie" autoFocus />
      <textarea name="description" rows={2} defaultValue={section?.description ?? ""} placeholder="Ce qu'elle doit contenir" />
      {requirements && requirements.length > 0 && (
        <fieldset>
          <legend>Exigences couvertes</legend>
          {requirements.map((requirement) => (
            <label key={requirement.id} className="memoir-check">
              <input
                type="checkbox"
                name="requirement"
                value={requirement.id}
                defaultChecked={section?.requirementIds.includes(requirement.id)}
              />{" "}
              {requirement.text}
            </label>
          ))}
        </fieldset>
      )}
      <div className="form-actions">
        <button type="submit" className="primary" disabled={busy}>
          Enregistrer
        </button>
        <button type="button" className="secondary" onClick={onCancel}>
          Annuler
        </button>
      </div>
    </form>
  );
}

function RequirementForm({ busy, onSubmit }: { busy: boolean; onSubmit: (category: string, text: string) => Promise<boolean> }) {
  return (
    <form
      className="memoir-section-form"
      onSubmit={async (event) => {
        event.preventDefault();
        const element = event.currentTarget;
        const form = new FormData(element);
        if (await onSubmit(String(form.get("category")), String(form.get("text") ?? "").trim())) {
          element.reset();
        }
      }}
    >
      <select name="category" defaultValue="CRITERION">
        {(Object.keys(REQUIREMENT_LABELS) as RequirementCategory[]).map((category) => (
          <option key={category} value={category}>
            {REQUIREMENT_LABELS[category]}
          </option>
        ))}
      </select>
      <textarea name="text" required rows={2} maxLength={1000} placeholder="Ajouter une exigence lue dans le DCE" />
      <button type="submit" disabled={busy}>
        Ajouter l&apos;exigence
      </button>
    </form>
  );
}
