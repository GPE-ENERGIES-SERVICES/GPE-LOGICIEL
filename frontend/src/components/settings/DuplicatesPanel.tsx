"use client";

import Link from "next/link";
import { useState } from "react";

import { FOLLOW_UP_LABELS, formatDate, formatDateTime, NOTICE_KIND_LABELS, STATUS_LABELS } from "@/lib/format";
import { useLoader } from "@/lib/useLoader";
import {
  confirmDuplicateBatch,
  getDuplicateDecisions,
  getDuplicateGroups,
  markNotDuplicates,
  mergeDuplicate,
  previewDuplicateBatch,
  undoDuplicateDecision,
} from "@/services/duplicateApi";
import type { DuplicateBatchPreview, DuplicateCandidate, DuplicateGroup } from "@/types/duplicate";

const errorText = (err: unknown) => (err instanceof Error ? err.message : "Opération impossible.");

function CandidateColumn({ candidate, kept }: { candidate: DuplicateCandidate; kept: boolean }) {
  return (
    <td className={kept ? "" : "row-muted"}>
      <p>
        <Link href={`/tenders/${candidate.id}`} target="_blank">
          N° {candidate.id}
        </Link>{" "}
        · {candidate.source} {candidate.externalId}
      </p>
      <p>
        <strong>{candidate.title}</strong>
      </p>
      <p className="muted">{candidate.buyer ?? "Acheteur non indiqué"}</p>
      <p>
        Publiée le {formatDate(candidate.publishedAt)} · date limite {formatDateTime(candidate.deadline)} ·{" "}
        {STATUS_LABELS[candidate.status]}
      </p>
      <p>
        Suivi GPE : {FOLLOW_UP_LABELS[candidate.followUpStatus]}
        {candidate.followUpComment && ` — « ${candidate.followUpComment} »`}
        {candidate.teamData && <span className="badge urgent">Données de l&apos;équipe</span>}
      </p>
      <ol className="history">
        {candidate.notices.map((notice) => (
          <li key={`${notice.source}-${notice.externalId}`}>
            {NOTICE_KIND_LABELS[notice.kind]} {notice.source} {notice.externalId} du {formatDate(notice.publishedAt)}
            {notice.deadline && ` — date limite ${formatDateTime(notice.deadline)}`}
          </li>
        ))}
      </ol>
    </td>
  );
}

function GroupView({ group, onDecided }: { group: DuplicateGroup; onDecided: () => void }) {
  const [keptId, setKeptId] = useState(group.suggestedKeptId);
  const [pending, setPending] = useState<number | null>(null);
  const [acknowledged, setAcknowledged] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const kept = group.tenders.find((candidate) => candidate.id === keptId)!;
  const others = group.tenders.filter((candidate) => candidate.id !== keptId);

  const run = async (action: () => Promise<unknown>) => {
    setError(null);
    try {
      await action();
      setPending(null);
      onDecided();
    } catch (err) {
      setError(errorText(err));
    }
  };

  return (
    <details className="previous-dossier">
      <summary>
        {group.certain ? "Certain" : "À vérifier"} · {group.tenders.length} consultations ·{" "}
        {group.tenders[0].title}
      </summary>
      <ul>
        {group.explanation.map((line) => (
          <li key={line}>{line}</li>
        ))}
      </ul>
      <table>
        <thead>
          <tr>
            {group.tenders.map((candidate) => (
              <th key={candidate.id}>
                <label className="inline">
                  <input
                    type="radio"
                    name={`kept-${group.procedureId}`}
                    checked={candidate.id === keptId}
                    onChange={() => {
                      setKeptId(candidate.id);
                      setPending(null);
                    }}
                  />
                  Conserver
                </label>
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          <tr>
            {group.tenders.map((candidate) => (
              <CandidateColumn key={candidate.id} candidate={candidate} kept={candidate.id === keptId} />
            ))}
          </tr>
        </tbody>
      </table>
      {others.map((other) => (
        <div key={other.id} className="form-actions">
          {pending === other.id ? (
            <>
              <span>
                La consultation N° {other.id} sera masquée et ses avis rattachés à la N° {kept.id}. Annulable depuis
                l&apos;historique.
              </span>
              {kept.teamData && other.teamData && (
                <label className="inline">
                  <input type="checkbox" checked={acknowledged} onChange={(e) => setAcknowledged(e.target.checked)} />
                  Les deux portent des données de l&apos;équipe : je conserve la N° {kept.id}
                </label>
              )}
              <button
                type="button"
                className="primary"
                disabled={kept.teamData && other.teamData && !acknowledged}
                onClick={() => run(() => mergeDuplicate(kept.id, other.id, acknowledged))}
              >
                Confirmer la fusion
              </button>
              <button type="button" onClick={() => setPending(null)}>
                Annuler
              </button>
            </>
          ) : (
            <>
              <button type="button" onClick={() => setPending(other.id)} disabled={other.teamData && !kept.teamData}>
                Fusionner la N° {other.id} dans la N° {kept.id}
              </button>
              <button type="button" onClick={() => run(() => markNotDuplicates(kept.id, other.id))}>
                Ce ne sont pas des doublons
              </button>
              {other.teamData && !kept.teamData && (
                <span className="hint">La N° {other.id} porte des données de l&apos;équipe : conservez-la.</span>
              )}
            </>
          )}
        </div>
      ))}
      {error && <p className="error">{error}</p>}
    </details>
  );
}

function BatchMerge({ onDone }: { onDone: () => void }) {
  const [preview, setPreview] = useState<DuplicateBatchPreview | null>(null);
  const [error, setError] = useState<string | null>(null);

  const prepare = async () => {
    setError(null);
    try {
      setPreview(await previewDuplicateBatch());
    } catch (err) {
      setError(errorText(err));
    }
  };

  const confirm = async () => {
    if (!preview) {
      return;
    }
    setError(null);
    try {
      await confirmDuplicateBatch(preview.token);
      setPreview(null);
      onDone();
    } catch (err) {
      setError(errorText(err));
    }
  };

  return (
    <div>
      {!preview ? (
        <div className="form-actions">
          <button type="button" onClick={prepare}>
            Préparer la fusion en lot des groupes certains
          </button>
          <span className="muted">Aperçu seulement : rien n&apos;est fusionné avant votre confirmation.</span>
        </div>
      ) : (
        <>
          <p>
            {preview.merges.length} fusion{preview.merges.length > 1 ? "s" : ""} proposée
            {preview.merges.length > 1 ? "s" : ""} (deux consultations d&apos;une même source, un rectificatif citant
            l&apos;autre, aucune donnée de l&apos;équipe sur la consultation fusionnée). {preview.leftForReview} groupe
            {preview.leftForReview > 1 ? "s restent" : " reste"} à décider un par un.
          </p>
          <table>
            <thead>
              <tr>
                <th>Conservée</th>
                <th>Fusionnée</th>
                <th>Motif</th>
              </tr>
            </thead>
            <tbody>
              {preview.merges.map((merge) => (
                <tr key={merge.mergedTenderId}>
                  <td>N° {merge.keptTenderId}</td>
                  <td>N° {merge.mergedTenderId}</td>
                  <td>{merge.reason}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="form-actions">
            <button type="button" className="primary" onClick={confirm} disabled={preview.merges.length === 0}>
              Confirmer ces {preview.merges.length} fusions
            </button>
            <button type="button" onClick={() => setPreview(null)}>
              Fermer l&apos;aperçu
            </button>
          </div>
        </>
      )}
      {error && <p className="error">{error}</p>}
    </div>
  );
}

/**
 * Tenders that are probably the same consultation (same procedure
 * identifier): merged or declared different by an administrator, one by
 * one or in a batch read beforehand. Every decision can be undone.
 */
export function DuplicatesPanel() {
  const groups = useLoader(getDuplicateGroups, "duplicate-groups");
  const decisions = useLoader(getDuplicateDecisions, "duplicate-decisions");
  const [error, setError] = useState<string | null>(null);

  const reload = () => {
    groups.reload();
    decisions.reload();
  };

  const undo = async (id: number) => {
    setError(null);
    try {
      await undoDuplicateDecision(id);
      reload();
    } catch (err) {
      setError(errorText(err));
    }
  };

  const certain = groups.data?.filter((group) => group.certain).length ?? 0;

  return (
    <div>
      {groups.error && <p className="error">{groups.error}</p>}
      {groups.data && (
        <p className="muted">
          {groups.data.length} groupe{groups.data.length > 1 ? "s" : ""} de consultations partageant un identifiant de
          procédure, dont {certain} certain{certain > 1 ? "s" : ""}.
        </p>
      )}
      <BatchMerge onDone={reload} />
      {groups.data?.map((group) => (
        <GroupView key={group.procedureId} group={group} onDecided={reload} />
      ))}
      <h3>Décisions</h3>
      {error && <p className="error">{error}</p>}
      {decisions.data && decisions.data.length === 0 && <p className="muted">Aucune décision pour l&apos;instant.</p>}
      {decisions.data && decisions.data.length > 0 && (
        <table>
          <thead>
            <tr>
              <th>Date</th>
              <th>Décision</th>
              <th>Consultations</th>
              <th>Par</th>
              <th>État</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {decisions.data.map((decision) => (
              <tr key={decision.id} className={decision.undoneAt ? "row-muted" : ""}>
                <td>{formatDateTime(decision.decidedAt)}</td>
                <td>{decision.kind === "MERGE" ? "Fusion" : "Pas des doublons"}</td>
                <td>
                  {decision.kind === "MERGE"
                    ? `N° ${decision.otherTenderId} fusionnée dans N° ${decision.keptTenderId}`
                    : `N° ${decision.keptTenderId} et N° ${decision.otherTenderId}`}
                </td>
                <td>{decision.decidedBy}</td>
                <td>
                  {decision.undoneAt
                    ? `Annulée le ${formatDateTime(decision.undoneAt)} par ${decision.undoneBy}`
                    : "En vigueur"}
                </td>
                <td className="actions">
                  {!decision.undoneAt && (
                    <button type="button" onClick={() => undo(decision.id)}>
                      Annuler
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
