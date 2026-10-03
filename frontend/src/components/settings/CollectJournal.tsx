"use client";

import { useEffect, useState } from "react";

import { api } from "@/lib/api";
import { formatDate, formatDateTime } from "@/lib/format";
import type { CollectJournal as Journal } from "@/lib/types";
import { useLoader } from "@/lib/useLoader";

const STATUS_LABELS = { RUNNING: "En cours", SUCCESS: "Réussie", FAILED: "Échec" };

const TRIGGER_LABELS = { SCHEDULED: "Planifiée", MANUAL: "Manuelle" };

export function CollectJournal() {
  const { data, error, reload } = useLoader(() => api<Journal>("/api/collect-runs?limit=30"), "journal");
  const [starting, setStarting] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const running = starting || Boolean(data?.running);

  // A collection runs in the background: refresh the journal until it is over.
  useEffect(() => {
    if (!running) {
      return;
    }
    const timer = window.setInterval(async () => {
      const journal = await api<Journal>("/api/collect-runs?limit=1").catch(() => null);
      if (journal && !journal.running) {
        setStarting(false);
        reload();
      }
    }, 3000);
    return () => window.clearInterval(timer);
  }, [running, reload]);

  const start = async () => {
    setStarting(true);
    setActionError(null);
    try {
      await api("/api/collect-runs", { method: "POST" });
    } catch (err) {
      setActionError(err instanceof Error ? err.message : "Impossible de lancer la collecte.");
      setStarting(false);
    }
  };

  return (
    <div>
      <div className="form-actions">
        <button type="button" className="primary" onClick={start} disabled={running}>
          {running ? "Collecte en cours…" : "Lancer une collecte"}
        </button>
        <span className="muted">Collectes automatiques à 7 h et 13 h les jours ouvrés.</span>
      </div>
      {actionError && <p className="error">{actionError}</p>}
      {error && <p className="error">{error}</p>}
      {data && (
        <table>
          <thead>
            <tr>
              <th>Début</th>
              <th>Type</th>
              <th>Avis publiés depuis</th>
              <th>Statut</th>
              <th>Reçus</th>
              <th>Nouveaux</th>
              <th>Erreur</th>
            </tr>
          </thead>
          <tbody>
            {data.runs.map((run) => (
              <tr key={run.id} className={run.status === "FAILED" ? "row-error" : ""}>
                <td>{formatDateTime(run.startedAt)}</td>
                <td>{TRIGGER_LABELS[run.trigger]}</td>
                <td>{formatDate(run.sinceDate)}</td>
                <td>{STATUS_LABELS[run.status]}</td>
                <td>{run.noticesReceived}</td>
                <td>{run.noticesNew}</td>
                <td className="error-cell">{run.errorMessage ?? ""}</td>
              </tr>
            ))}
            {data.runs.length === 0 && (
              <tr>
                <td colSpan={7}>Aucune collecte pour l&apos;instant.</td>
              </tr>
            )}
          </tbody>
        </table>
      )}
    </div>
  );
}
