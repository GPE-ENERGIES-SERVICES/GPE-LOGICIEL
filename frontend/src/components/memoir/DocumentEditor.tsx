"use client";

import { useEffect, useRef, useState } from "react";

import { formatDateTime } from "@/lib/format";
import { AI_ACTION_LABELS, COMPANY_BLOCK_LABELS, PLACEHOLDER_LABELS } from "@/lib/memoir";
import {
  acceptProposal,
  askAi,
  cancelJob,
  getRevisions,
  insertCompany,
  rejectProposal,
  restoreRevision,
  saveContent,
  startWriting,
  uploadMemoirImage,
} from "@/services/memoirApi";
import type { AiAction, CompanyBlock, MemoirRevision, MemoirSection, MemoirWorkspace } from "@/types/memoir";

type Props = {
  workspace: MemoirWorkspace;
  onSection: (section: MemoirSection) => void;
  onChanged: (workspace: MemoirWorkspace) => void;
  onError: (message: string | null) => void;
};

/** The editor where the cursor was, kept while a toolbar button or a file dialog takes the focus. */
type Caret = { editor: HTMLElement; range: Range } | null;

/**
 * The memoir as a document: its sections in the order of the outline, each
 * typed into directly and saved by itself, the toolbar of a word processor
 * above, and the work of the AI shown section by section.
 */
export function DocumentEditor({ workspace, onSection, onChanged, onError }: Props) {
  const { memoir, sections, job, ai } = workspace;
  const caret = useRef<Caret>(null);
  const imageInput = useRef<HTMLInputElement>(null);
  // The image clicked in the text, to replace or remove; the state only shows its buttons.
  const selected = useRef<HTMLImageElement | null>(null);
  const [imageSelected, setImageSelected] = useState(false);
  const select = (image: HTMLImageElement | null) => {
    selected.current = image;
    setImageSelected(image !== null);
  };
  const replacing = useRef(false);
  const writing = job?.status === "RUNNING" && job.kind === "WRITING";
  const visible = sections.filter((section) => !section.proposed);
  const current = writing ? sections.find((section) => section.id === job?.currentSectionId) : undefined;

  const remember = () => {
    const selection = window.getSelection();
    if (selection && selection.rangeCount > 0) {
      const range = selection.getRangeAt(0);
      const editor = (range.startContainer instanceof HTMLElement ? range.startContainer : range.startContainer.parentElement)
        ?.closest<HTMLElement>(".memoir-body");
      if (editor) {
        caret.current = { editor, range: range.cloneRange() };
      }
    }
  };

  const restore = () => {
    if (!caret.current) {
      return false;
    }
    caret.current.editor.focus();
    const selection = window.getSelection();
    selection?.removeAllRanges();
    selection?.addRange(caret.current.range);
    return true;
  };

  const command = (name: string, value?: string) => {
    if (!restore()) {
      onError("Cliquez d'abord dans le texte d'une section.");
      return;
    }
    document.execCommand(name, false, value);
    remember();
  };

  const placeholder = (kind: string) =>
    command(
      "insertHTML",
      `<figure class="placeholder" data-kind="${kind}"><figcaption>${PLACEHOLDER_LABELS[kind]} : décrire ici</figcaption></figure><p><br></p>`,
    );

  const onImage = async (file: File) => {
    try {
      const uploaded = await uploadMemoirImage(memoir.id, file);
      const image = selected.current;
      if (replacing.current && image) {
        image.src = uploaded.url;
        image.closest(".memoir-body")?.dispatchEvent(new Event("input", { bubbles: true }));
        select(null);
      } else {
        command("insertHTML", `<figure><img src="${uploaded.url}" alt=""><figcaption>Légende</figcaption></figure><p><br></p>`);
      }
    } catch (err) {
      onError(err instanceof Error ? err.message : "Image refusée.");
    } finally {
      replacing.current = false;
    }
  };

  const run = async (action: () => Promise<MemoirWorkspace>) => {
    onError(null);
    try {
      onChanged(await action());
    } catch (err) {
      onError(err instanceof Error ? err.message : "Action impossible.");
    }
  };

  const empty = visible.filter((section) => !section.content && !visible.some((child) => child.parentId === section.id)).length;

  return (
    <div className="memoir-editor">
      <div className="memoir-toolbar" role="toolbar" aria-label="Mise en forme" onMouseDown={(event) => {
        if ((event.target as HTMLElement).closest("button")) {
          event.preventDefault();
        }
      }}>
        <button type="button" title="Titre" onClick={() => command("formatBlock", "h3")}>Titre</button>
        <button type="button" title="Sous-titre" onClick={() => command("formatBlock", "h4")}>Sous-titre</button>
        <button type="button" title="Paragraphe" onClick={() => command("formatBlock", "p")}>¶</button>
        <span className="memoir-toolbar-separator" />
        <button type="button" title="Gras" onClick={() => command("bold")}><strong>G</strong></button>
        <button type="button" title="Italique" onClick={() => command("italic")}><em>I</em></button>
        <button type="button" title="Souligné" onClick={() => command("underline")}><u>S</u></button>
        <span className="memoir-toolbar-separator" />
        <button type="button" title="Liste à puces" onClick={() => command("insertUnorderedList")}>• Liste</button>
        <button type="button" title="Liste numérotée" onClick={() => command("insertOrderedList")}>1. Liste</button>
        <span className="memoir-toolbar-separator" />
        <button type="button" title="Insérer une image" onClick={() => {
          replacing.current = false;
          if (!caret.current) {
            onError("Cliquez d'abord dans le texte d'une section.");
            return;
          }
          imageInput.current?.click();
        }}>Image</button>
        <select
          aria-label="Insérer un emplacement réservé"
          value=""
          onMouseDown={remember}
          onChange={(event) => event.target.value && placeholder(event.target.value)}
        >
          <option value="">Emplacement…</option>
          {Object.entries(PLACEHOLDER_LABELS).map(([kind, label]) => (
            <option key={kind} value={kind}>
              {label}
            </option>
          ))}
        </select>
        {imageSelected && (
          <>
            <span className="memoir-toolbar-separator" />
            <button type="button" onClick={() => {
              replacing.current = true;
              imageInput.current?.click();
            }}>Remplacer l&apos;image</button>
            <button className="danger" type="button" onClick={() => {
              const image = selected.current;
              const body = image?.closest(".memoir-body");
              (image?.closest("figure") ?? image)?.remove();
              body?.dispatchEvent(new Event("input", { bubbles: true }));
              select(null);
            }}>Supprimer l&apos;image</button>
          </>
        )}
        <input
          ref={imageInput}
          type="file"
          accept=".png,.jpg,.jpeg"
          hidden
          onChange={(event) => {
            const file = event.target.files?.[0];
            if (file) {
              onImage(file);
            }
            event.target.value = "";
          }}
        />
      </div>

      <div className="memoir-writing">
        {writing ? (
          <>
            <span className="memoir-spinner" aria-hidden="true" />
            <span>
              Rédaction par l&apos;IA : {job.doneSteps} / {job.totalSteps}
              {current && ` · en cours : ${current.number} ${current.title}`}
            </span>
            <progress max={job.totalSteps} value={job.doneSteps} />
            <button type="button" className="secondary" disabled={job.cancelRequested} onClick={() => run(() => cancelJob(memoir.id))}>
              {job.cancelRequested ? "Arrêt après la section en cours…" : "Arrêter"}
            </button>
          </>
        ) : (
          <>
            <span className="muted">
              {visible.length === 0
                ? "Construisez le sommaire à gauche : ses parties apparaîtront ici."
                : `${memoir.written} / ${memoir.sections} sections rédigées`}
            </span>
            {visible.length > 0 && (
              <button
                type="button"
                disabled={!ai.available || !memoir.outlineValidatedAt || empty === 0}
                title={!ai.available ? ai.message : !memoir.outlineValidatedAt ? "Validez d'abord le sommaire" : undefined}
                onClick={() => run(() => startWriting(memoir.id))}
              >
                Rédiger les {empty} section(s) vide(s) avec l&apos;IA
              </button>
            )}
            {job && job.kind === "WRITING" && job.status !== "RUNNING" && job.message && (
              <details className="memoir-job-message">
                <summary>{job.status === "DONE" ? "Rédaction terminée" : job.status === "CANCELLED" ? "Rédaction arrêtée" : "Rédaction interrompue"} : voir les remarques</summary>
                <p className="memoir-message">{job.message}</p>
              </details>
            )}
          </>
        )}
      </div>

      <div
        className="memoir-page"
        onClick={(event) => {
          const target = event.target as HTMLElement;
          select(target instanceof HTMLImageElement && target.closest(".memoir-body") ? target : null);
        }}
        onKeyUp={remember}
        onMouseUp={remember}
      >
        <h1 className="memoir-page-title">{memoir.title}</h1>
        {memoir.tenderTitle && (
          <p className="memoir-page-subtitle">
            {memoir.tenderTitle}
            {memoir.lotNumber && ` · lot ${memoir.lotNumber}`}
          </p>
        )}
        {visible.map((section) => (
          <SectionEditor
            key={section.id}
            section={section}
            workspace={workspace}
            generating={writing && job?.currentSectionId === section.id}
            parent={visible.some((child) => child.parentId === section.id)}
            onSection={onSection}
            onError={onError}
          />
        ))}
      </div>
    </div>
  );
}

const HEADINGS = ["h2", "h3", "h4", "h5"] as const;

/** One section: its title, its text typed into and saved by itself, its proposal, its tools. */
function SectionEditor({
  section,
  workspace,
  generating,
  parent,
  onSection,
  onError,
}: {
  section: MemoirSection;
  workspace: MemoirWorkspace;
  generating: boolean;
  parent: boolean;
  onSection: (section: MemoirSection) => void;
  onError: (message: string | null) => void;
}) {
  const body = useRef<HTMLDivElement>(null);
  // The content last put in the editor or saved: a content coming from the server replaces only an editor left alone.
  const shown = useRef<string | null | undefined>(undefined);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [state, setState] = useState<"saved" | "dirty" | "saving">("saved");
  const [busy, setBusy] = useState(false);
  const [requirementId, setRequirementId] = useState<string>("");
  const [revisions, setRevisions] = useState<MemoirRevision[] | null>(null);
  const Heading = HEADINGS[Math.min(section.level, 4) - 1];
  const { ai, requirements } = workspace;

  useEffect(() => {
    const element = body.current;
    if (element && section.content !== shown.current && document.activeElement !== element && state === "saved") {
      element.innerHTML = section.content ?? "";
      shown.current = section.content;
    }
  }, [section.content, state]);

  useEffect(() => () => {
    if (timer.current) {
      clearTimeout(timer.current);
    }
  }, []);

  const save = async () => {
    if (timer.current) {
      clearTimeout(timer.current);
      timer.current = null;
    }
    const element = body.current;
    if (!element) {
      return;
    }
    setState("saving");
    try {
      const saved = await saveContent(section.id, element.innerHTML);
      shown.current = saved.content;
      onSection(saved);
      setState("saved");
    } catch (err) {
      setState("dirty");
      onError(err instanceof Error ? `Section ${section.number} non enregistrée : ${err.message}` : "Enregistrement impossible.");
    }
  };

  const act = async (action: () => Promise<MemoirSection>) => {
    setBusy(true);
    onError(null);
    try {
      onSection(await action());
    } catch (err) {
      onError(err instanceof Error ? err.message : "Action impossible.");
    } finally {
      setBusy(false);
    }
  };

  const linked = requirements.filter((requirement) => section.requirementIds.includes(requirement.id));

  return (
    <section id={`memoir-section-${section.id}`} className={`memoir-section level-${section.level}${generating ? " generating" : ""}`}>
      <Heading className="memoir-heading">
        <span className="memoir-number">{section.number}</span> {section.title}
      </Heading>

      {section.proposal && (
        <div className="memoir-proposal">
          <div className="memoir-proposal-head">
            <strong>{section.proposalNote ?? "Proposition"}</strong>
            <span className="muted"> — votre texte n&apos;est pas modifié tant que vous n&apos;acceptez pas.</span>
          </div>
          <div className="memoir-proposal-body" dangerouslySetInnerHTML={{ __html: section.proposal }} />
          <div className="form-actions">
            <button type="button" className="primary" disabled={busy} onClick={() => act(() => acceptProposal(section.id, false))}>
              Remplacer mon texte
            </button>
            <button type="button" disabled={busy} onClick={() => act(() => acceptProposal(section.id, true))}>
              Ajouter à la suite
            </button>
            <button type="button" className="secondary" disabled={busy} onClick={() => act(() => rejectProposal(section.id))}>
              Ignorer
            </button>
          </div>
        </div>
      )}

      {generating && (
        <p className="memoir-generating">
          <span className="memoir-spinner" aria-hidden="true" /> L&apos;IA rédige cette section…
        </p>
      )}

      <div
        ref={body}
        className="memoir-body"
        contentEditable
        suppressContentEditableWarning
        role="textbox"
        aria-multiline="true"
        aria-label={`Texte de la section ${section.number} ${section.title}`}
        data-placeholder={
          parent
            ? "Introduction de la partie (facultatif)"
            : section.description
              ? `À rédiger : ${section.description}`
              : "Rédigez cette section…"
        }
        onInput={() => {
          setState("dirty");
          if (timer.current) {
            clearTimeout(timer.current);
          }
          timer.current = setTimeout(save, 1500);
        }}
        onBlur={() => {
          if (state === "dirty" || timer.current) {
            save();
          }
        }}
      />

      <div className="memoir-section-tools">
        <span className={`memoir-save ${state}`}>
          {state === "saving" ? "Enregistrement…" : state === "dirty" ? "Modifié" : section.content ? "Enregistré" : ""}
        </span>
        <select
          aria-label="Demander à l'IA"
          value=""
          disabled={busy || !ai.available}
          title={ai.available ? undefined : ai.message}
          onChange={(event) => {
            const action = event.target.value as AiAction;
            if (!action) {
              return;
            }
            if (action === "ADAPT" && !requirementId) {
              onError("Choisissez d'abord l'exigence du DCE à laquelle adapter le texte.");
              return;
            }
            act(() =>
              askAi(section.id, {
                action,
                instruction: null,
                requirementId: action === "ADAPT" ? Number(requirementId) : null,
              }),
            );
          }}
        >
          <option value="">{busy ? "L'IA travaille…" : "Demander à l'IA…"}</option>
          {(Object.keys(AI_ACTION_LABELS) as AiAction[]).map((action) => (
            <option key={action} value={action} disabled={action !== "WRITE" && !section.content}>
              {AI_ACTION_LABELS[action]}
            </option>
          ))}
        </select>
        {(linked.length > 0 ? linked : requirements).length > 0 && (
          <select aria-label="Exigence du DCE" value={requirementId} onChange={(event) => setRequirementId(event.target.value)}>
            <option value="">Exigence visée…</option>
            {(linked.length > 0 ? linked : requirements).map((requirement) => (
              <option key={requirement.id} value={requirement.id}>
                {requirement.text.slice(0, 70)}
              </option>
            ))}
          </select>
        )}
        <select
          aria-label="Insérer depuis l'entreprise"
          value=""
          disabled={busy}
          onChange={(event) => event.target.value && act(() => insertCompany(section.id, event.target.value as CompanyBlock))}
        >
          <option value="">Insérer depuis l&apos;Entreprise…</option>
          {(Object.keys(COMPANY_BLOCK_LABELS) as CompanyBlock[]).map((block) => (
            <option key={block} value={block}>
              {COMPANY_BLOCK_LABELS[block]}
            </option>
          ))}
        </select>
        <button
          type="button"
          className="secondary"
          onClick={async () => {
            if (revisions) {
              setRevisions(null);
              return;
            }
            try {
              setRevisions(await getRevisions(section.id));
            } catch (err) {
              onError(err instanceof Error ? err.message : "Historique indisponible.");
            }
          }}
        >
          Versions précédentes
        </button>
      </div>

      {revisions && (
        <div className="memoir-revisions">
          {revisions.length === 0 ? (
            <p className="muted">Aucune version précédente.</p>
          ) : (
            <ul className="plain-list">
              {revisions.map((revision) => (
                <li key={revision.id}>
                  {formatDateTime(revision.createdAt)} — remplacée par {revision.replacedBy ?? "?"}{" "}
                  <button
                    type="button"
                    className="link-button"
                    onClick={() => {
                      setRevisions(null);
                      act(() => restoreRevision(section.id, revision.id));
                    }}
                  >
                    Proposer de la rétablir
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </section>
  );
}
