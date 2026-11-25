"use client";

import { followUpClass, formatDateTime, FOLLOW_UP_LABELS } from "@/lib/format";
import { useLoader } from "@/lib/useLoader";
import { getStatusHistory } from "@/services/awardApi";

/** Every change of the follow-up status: from, to, when, by whom, why. */
export function StatusHistory({ tenderId, version }: { tenderId: number; version: string }) {
  const history = useLoader(() => getStatusHistory(tenderId), `${tenderId}/${version}`);
  if (history.error) {
    return <p className="muted">Historique des statuts indisponible.</p>;
  }
  if (!history.data) {
    return <p className="muted">Chargement…</p>;
  }
  if (history.data.length === 0) {
    return <p className="muted">Aucun changement de statut enregistré.</p>;
  }
  return (
    <ol className="status-timeline">
      {history.data.map((change) => (
        <li key={change.id}>
          <span className={`badge ${followUpClass(change.oldStatus)}`}>{FOLLOW_UP_LABELS[change.oldStatus]}</span>
          <span aria-label="vers">→</span>
          <span className={`badge ${followUpClass(change.newStatus)}`}>{FOLLOW_UP_LABELS[change.newStatus]}</span>
          <span className="muted small">
            {formatDateTime(change.changedAt)}
            {change.changedBy && ` · ${change.changedBy}`}
            {change.origin === "NOTICE" && " · d'après l'avis d'attribution"}
          </span>
          {change.reason && <span className="status-reason">« {change.reason} »</span>}
        </li>
      ))}
    </ol>
  );
}
