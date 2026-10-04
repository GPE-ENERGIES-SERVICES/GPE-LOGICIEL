"use client";

import { formatDateTime } from "@/lib/format";
import { useLoader } from "@/lib/useLoader";
import { getCollectJournal } from "@/services/settingsApi";

/**
 * State of the last collection, shown above the list. A failure is never
 * silent.
 */
export function CollectStatus() {
  const { data } = useLoader(() => getCollectJournal(1), "last-run");
  if (!data) {
    return null;
  }
  const last = data.runs[0];
  if (data.running) {
    return <p className="banner info">Collecte en cours…</p>;
  }
  if (!last) {
    return <p className="banner warning">Aucune collecte n&apos;a encore été effectuée.</p>;
  }
  if (last.status === "FAILED") {
    return (
      <p className="banner error">
        La dernière collecte a échoué le {formatDateTime(last.startedAt)} : {last.errorMessage}. Elle sera
        reprise automatiquement à la prochaine collecte.
      </p>
    );
  }
  const newNotices =
    last.noticesNew === 0
      ? "aucun nouvel avis"
      : `${last.noticesNew} ${last.noticesNew > 1 ? "nouveaux" : "nouvel"} avis`;
  return (
    <p className="banner muted">
      Dernière collecte le {formatDateTime(last.finishedAt ?? last.startedAt)} : {newNotices}.
    </p>
  );
}
