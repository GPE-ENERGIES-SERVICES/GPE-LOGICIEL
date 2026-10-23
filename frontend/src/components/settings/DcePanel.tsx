"use client";

import { useState } from "react";

import { DCE_STATUS_LABELS } from "@/components/DceLinks";
import { formatDateTime } from "@/lib/format";
import { useLoader } from "@/lib/useLoader";
import { getDceEvents, getDceSummary, retryDceLink } from "@/services/documentApi";
import type { DceLinkStatus, DceSummary } from "@/types/document";

const RETRIABLE: DceLinkStatus[] = ["TEMPORARY_ERROR", "PERMANENT_ERROR", "LINK_UNAVAILABLE", "STORAGE_LIMIT_REACHED"];

/** Bytes in decimal units: 3,000,000,000 bytes are 3 Go. */
function size(bytes: number): string {
  if (bytes >= 1_000_000_000) {
    return `${(bytes / 1_000_000_000).toLocaleString("fr-FR", { maximumFractionDigits: 2 })} Go`;
  }
  if (bytes >= 1_000_000) {
    return `${(bytes / 1_000_000).toLocaleString("fr-FR", { maximumFractionDigits: 1 })} Mo`;
  }
  return `${bytes.toLocaleString("fr-FR")} octets`;
}

function count(summary: DceSummary, status: DceLinkStatus): number {
  return summary.links[status] ?? 0;
}

function Summary({ summary }: { summary: DceSummary }) {
  const fetched = count(summary, "FETCHED");
  const manual =
    count(summary, "AUTOMATED_ACCESS_NOT_AUTHORIZED") +
    count(summary, "ACCESS_REQUIRED") +
    count(summary, "LINK_UNAVAILABLE");
  return (
    <>
      {summary.allowedPlatforms.length === 0 ? (
        <p className="banner muted">
          Aucune plateforme n&apos;est autorisée pour la récupération automatique : aucun DCE n&apos;est téléchargé par
          GPE. Les consultations éligibles restent à télécharger à la main sur leur plateforme.
        </p>
      ) : (
        <p className="banner muted">
          Plateformes autorisées : {summary.allowedPlatforms.join(", ")}. Récupération{" "}
          {summary.workerEnabled ? `active (${summary.concurrency} à la fois)` : "désactivée"}.
        </p>
      )}
      <table>
        <tbody>
          <tr>
            <th>Consultations éligibles</th>
            <td>{summary.eligibility.ELIGIBLE ?? 0}</td>
            <th>Non éligibles</th>
            <td>{summary.eligibility.NOT_ELIGIBLE ?? 0}</td>
            <th>Indéterminées</th>
            <td>{summary.eligibility.INDETERMINATE ?? 0}</td>
          </tr>
          <tr>
            <th>DCE en attente</th>
            <td>{count(summary, "ELIGIBLE_PENDING") + count(summary, "IN_PROGRESS")}</td>
            <th>DCE récupérés</th>
            <td>{fetched}</td>
            <th>Action humaine nécessaire</th>
            <td>{manual}</td>
          </tr>
          <tr>
            <th>Accès automatisé non autorisé</th>
            <td>{count(summary, "AUTOMATED_ACCESS_NOT_AUTHORIZED")}</td>
            <th>Erreurs temporaires</th>
            <td>{count(summary, "TEMPORARY_ERROR")}</td>
            <th>Erreurs définitives</th>
            <td>{count(summary, "PERMANENT_ERROR")}</td>
          </tr>
          <tr>
            <th>Espace utilisé</th>
            <td>
              {size(summary.usedBytes)} sur {size(summary.quotaBytes)}
            </td>
            <th>Espace réservé</th>
            <td>{size(summary.reservedBytes)}</td>
            <th>Quota atteint</th>
            <td>{count(summary, "STORAGE_LIMIT_REACHED")}</td>
          </tr>
        </tbody>
      </table>
      {summary.outOfDate > 0 && (
        <p className="hint">
          {summary.outOfDate} consultation(s) à réévaluer{summary.evaluating ? " (réévaluation en cours)" : ""}.
        </p>
      )}
      {fetched === 0 && <p className="hint">Aucun DCE n&apos;a été récupéré automatiquement.</p>}
    </>
  );
}

/**
 * The retrieval of the consultation files: eligibility of the tenders by the
 * saved searches, links by status, space used on the quota and the journal
 * of the changes, with a new attempt after a failure. The server checks again
 * that the tender is eligible and the platform allowed.
 */
export function DcePanel() {
  const summary = useLoader(getDceSummary, "dce-summary");
  const events = useLoader(() => getDceEvents(50), "dce-events");
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState<number | null>(null);

  const retry = async (linkId: number) => {
    setBusy(linkId);
    setMessage(null);
    try {
      await retryDceLink(linkId);
      setMessage("Nouvelle tentative programmée.");
      summary.reload();
      events.reload();
    } catch (err) {
      setMessage(err instanceof Error ? err.message : "Nouvelle tentative impossible.");
    } finally {
      setBusy(null);
    }
  };

  return (
    <div>
      {summary.error && <p className="error">{summary.error}</p>}
      {summary.data ? <Summary summary={summary.data} /> : !summary.error && <p className="muted">Chargement…</p>}
      {message && <p className="banner info">{message}</p>}
      <h3>Derniers changements d&apos;état</h3>
      {events.error && <p className="error">{events.error}</p>}
      {events.data && events.data.length === 0 && <p className="muted">Aucun changement enregistré.</p>}
      {events.data && events.data.length > 0 && (
        <table>
          <thead>
            <tr>
              <th>Date</th>
              <th>Consultation</th>
              <th>Plateforme</th>
              <th>État</th>
              <th>Motif</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {events.data.map((event) => (
              <tr key={event.id}>
                <td>{formatDateTime(event.occurredAt)}</td>
                <td>{event.tenderId ? <a href={`/tenders/${event.tenderId}`}>N° {event.tenderId}</a> : "–"}</td>
                <td>{event.platform ?? "–"}</td>
                <td>{DCE_STATUS_LABELS[event.toStatus]}</td>
                <td className="error-cell">{event.detail ?? ""}</td>
                <td className="actions">
                  {RETRIABLE.includes(event.toStatus) && (
                    <button type="button" onClick={() => retry(event.dceLinkId)} disabled={busy === event.dceLinkId}>
                      Relancer
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
