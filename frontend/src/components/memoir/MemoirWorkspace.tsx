"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";

import { MEMOIR_STATUS_LABELS } from "@/lib/memoir";
import { useLoader } from "@/lib/useLoader";
import { deleteMemoir, getWorkspace, updateMemoir } from "@/services/memoirApi";
import type { MemoirSection, MemoirStatus, MemoirWorkspace } from "@/types/memoir";

import { DocumentEditor } from "./DocumentEditor";
import { OutlinePanel } from "./OutlinePanel";

/** Read again this often while the AI works on the memoir. */
const POLL_MS = 2000;

/**
 * The workspace of a memoir: the preparation on the left (requirements of
 * the consultation file, outline), the document on the right, larger.
 */
export function MemoirWorkspaceView({ id }: { id: number }) {
  const router = useRouter();
  const loaded = useLoader(() => getWorkspace(id), String(id));
  const [saved, setSaved] = useState<MemoirWorkspace | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [panel, setPanel] = useState(true);
  const workspace = saved ?? loaded.data;
  const running = workspace?.job?.status === "RUNNING";

  useEffect(() => {
    if (!running) {
      return;
    }
    const timer = setInterval(async () => {
      try {
        setSaved(await getWorkspace(id));
      } catch {
        // read again at the next tick
      }
    }, POLL_MS);
    return () => clearInterval(timer);
  }, [running, id]);

  if (loaded.error) {
    return <p className="banner error">{loaded.error}</p>;
  }
  if (!workspace) {
    return <p className="page-message">Chargement…</p>;
  }
  const { memoir } = workspace;

  /** A section saved: the workspace follows, its counts with it. */
  const onSection = (section: MemoirSection) =>
    setSaved((current) => {
      const base = current ?? workspace;
      const sections = base.sections.map((candidate) => (candidate.id === section.id ? section : candidate));
      const written = sections.filter((candidate) => !candidate.proposed && candidate.content).length;
      return { ...base, sections, memoir: { ...base.memoir, written } };
    });

  const change = async (status: MemoirStatus) => {
    try {
      const updated = await updateMemoir(memoir.id, { title: memoir.title, lotNumber: memoir.lotNumber, status });
      setSaved({ ...workspace, memoir: { ...workspace.memoir, ...updated } });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Changement impossible.");
    }
  };

  return (
    <div className="memoir-workspace">
      <header className="memoir-header">
        <div>
          <Link href="/memoirs">← Mémoires techniques</Link>
          <h1>{memoir.title}</h1>
          {memoir.tenderId && (
            <p className="muted">
              <Link href={`/tenders/${memoir.tenderId}`}>{memoir.tenderTitle}</Link>
              {memoir.lotNumber && ` · lot ${memoir.lotNumber}`}
            </p>
          )}
        </div>
        <div className="memoir-header-actions">
          <label>
            État{" "}
            <select value={memoir.status} onChange={(event) => change(event.target.value as MemoirStatus)}>
              {(Object.keys(MEMOIR_STATUS_LABELS) as MemoirStatus[]).map((status) => (
                <option key={status} value={status}>
                  {MEMOIR_STATUS_LABELS[status]}
                </option>
              ))}
            </select>
          </label>
          <button type="button" className="secondary" onClick={() => setPanel(!panel)} aria-expanded={panel}>
            {panel ? "Masquer la préparation" : "Afficher la préparation"}
          </button>
          <button
            type="button"
            className="secondary danger"
            onClick={async () => {
              if (window.confirm("Supprimer ce mémoire, son sommaire, son texte et ses images ?")) {
                try {
                  await deleteMemoir(memoir.id);
                  router.push("/memoirs");
                } catch (err) {
                  setError(err instanceof Error ? err.message : "Suppression impossible.");
                }
              }
            }}
          >
            Supprimer
          </button>
        </div>
      </header>
      {error && (
        <p className="banner error" role="alert">
          {error}{" "}
          <button type="button" className="link-button" onClick={() => setError(null)}>
            Fermer
          </button>
        </p>
      )}
      {workspace.job?.status === "FAILED" && workspace.job.message && (
        <p className="banner warning">Le dernier travail de l&apos;IA s&apos;est arrêté : {workspace.job.message}</p>
      )}
      <div className={panel ? "memoir-columns" : "memoir-columns no-panel"}>
        {panel && (
          <OutlinePanel
            workspace={workspace}
            onChanged={setSaved}
            onError={setError}
            onShow={(sectionId) =>
              document.getElementById(`memoir-section-${sectionId}`)?.scrollIntoView({ behavior: "smooth", block: "start" })
            }
          />
        )}
        <DocumentEditor workspace={workspace} onSection={onSection} onChanged={setSaved} onError={setError} />
      </div>
    </div>
  );
}
