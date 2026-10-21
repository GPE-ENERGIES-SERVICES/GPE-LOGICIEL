"use client";

import { useEffect, useState } from "react";

import { formatDate, formatDateTime } from "@/lib/format";
import { useLoader } from "@/lib/useLoader";
import { getCollectJournal, getCollectSources, startCollect } from "@/services/settingsApi";
import type { CollectRun, SourceAccess } from "@/types/settings";

const STATUS_LABELS = { RUNNING: "En cours", SUCCESS: "Réussie", FAILED: "Échec" };

const TRIGGER_LABELS = { SCHEDULED: "Planifiée", MANUAL: "Manuelle", CATCH_UP: "Rattrapage" };

const ACCESS_LABELS: Record<SourceAccess, string> = {
  AUTOMATED: "API ou open data",
  AUTHENTICATED: "Accès avec identifiants",
  MANUAL: "Dépôt manuel",
  NOT_AUTOMATABLE: "Consultation manuelle",
};

/** "du 11/10/2026 au 22/10/2026" for the publication days a run asked. */
function period(run: CollectRun): string {
  return run.untilDate ? `du ${formatDate(run.sinceDate)} au ${formatDate(run.untilDate)}` : `depuis le ${formatDate(run.sinceDate)}`;
}

function duration(run: CollectRun): string {
  if (!run.finishedAt) {
    return "–";
  }
  const seconds = Math.round((Date.parse(run.finishedAt) - Date.parse(run.startedAt)) / 1000);
  return seconds < 60 ? `${seconds} s` : `${Math.floor(seconds / 60)} min ${seconds % 60} s`;
}

function Sources({ version }: { version: number }) {
  const { data, error } = useLoader(getCollectSources, `sources-${version}`);
  if (error) {
    return <p className="error">{error}</p>;
  }
  if (!data) {
    return null;
  }
  return (
    <table>
      <thead>
        <tr>
          <th>Source</th>
          <th>Accès</th>
          <th>Collecte automatique</th>
          <th>Dernière tentative</th>
          <th>Dernière collecte réussie</th>
          <th>Publications couvertes</th>
          <th>Prochaine collecte</th>
          <th>Dernière erreur</th>
          <th title="Heures du lundi au vendredi depuis la dernière collecte réussie ; alerte au-delà de 24">
            Retard
          </th>
        </tr>
      </thead>
      <tbody>
        {data.map((source) => (
          <tr
            key={source.source}
            className={source.overdue || source.lastRun?.status === "FAILED" ? "row-error" : ""}
          >
            <td>{source.source}</td>
            <td>{ACCESS_LABELS[source.access]}</td>
            <td>
              {source.running
                ? source.catchingUp
                  ? "Rattrapage en cours"
                  : "En cours"
                : source.active
                  ? "Oui"
                  : "Non"}
            </td>
            <td>
              {source.lastRun
                ? `${formatDateTime(source.lastRun.startedAt)} (${STATUS_LABELS[source.lastRun.status]})`
                : "Jamais"}
            </td>
            <td>{source.lastSuccess ? formatDateTime(source.lastSuccess.startedAt) : "Aucune"}</td>
            <td>{source.lastSuccess ? period(source.lastSuccess) : "–"}</td>
            <td>{source.nextRun ? formatDateTime(source.nextRun) : "–"}</td>
            <td className="error-cell">
              {source.lastFailure
                ? `${formatDateTime(source.lastFailure.startedAt)} : ${source.lastFailure.errorMessage ?? "erreur"}`
                : "–"}
            </td>
            <td>
              {!source.active
                ? "–"
                : source.workingHoursSinceSuccess === null
                  ? "Jamais collectée"
                  : `${source.workingHoursSinceSuccess} h ouvrées${source.overdue ? " (alerte)" : ""}`}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

export function CollectJournal() {
  const { data, error, reload } = useLoader(() => getCollectJournal(30), "journal");
  const [starting, setStarting] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [version, setVersion] = useState(0);
  const running = starting || Boolean(data?.running);

  // A collection runs in the background: refresh the journal until it is over.
  useEffect(() => {
    if (!running) {
      return;
    }
    const timer = window.setInterval(async () => {
      const journal = await getCollectJournal(1).catch(() => null);
      if (journal && !journal.running) {
        setStarting(false);
        setVersion((current) => current + 1);
        reload();
      }
    }, 3000);
    return () => window.clearInterval(timer);
  }, [running, reload]);

  const start = async () => {
    setStarting(true);
    setActionError(null);
    try {
      await startCollect();
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
        <span className="muted">
          Collectes automatiques à 7 h et 13 h les jours ouvrés, une source après l&apos;autre. Une collecte
          manquée pendant un arrêt est rattrapée au démarrage puis chaque heure, par tranches de 14 jours au plus.
        </span>
      </div>
      {actionError && <p className="error">{actionError}</p>}
      <Sources version={version} />
      {error && <p className="error">{error}</p>}
      {data && (
        <table>
          <thead>
            <tr>
              <th>Début</th>
              <th>Source</th>
              <th>Type</th>
              <th>Publications</th>
              <th>Statut</th>
              <th>Durée</th>
              <th title="Avis reçus de la source">Reçus</th>
              <th title="Avis enregistrés pour la première fois">Nouveaux</th>
              <th title="Avis déjà enregistrés, ignorés">Doublons</th>
              <th title="Consultations créées">Créées</th>
              <th title="Avis appliqués à une consultation connue">Mises à jour</th>
              <th title="Avis sans identifiant ou illisibles">Erreurs</th>
              <th>Erreur</th>
            </tr>
          </thead>
          <tbody>
            {data.runs.map((run) => (
              <tr key={run.id} className={run.status === "FAILED" ? "row-error" : ""}>
                <td>{formatDateTime(run.startedAt)}</td>
                <td>{run.source}</td>
                <td>{TRIGGER_LABELS[run.trigger]}</td>
                <td>{period(run)}</td>
                <td>{STATUS_LABELS[run.status]}</td>
                <td>{duration(run)}</td>
                <td>{run.noticesReceived}</td>
                <td>{run.noticesNew}</td>
                <td>{run.noticesKnown}</td>
                <td>{run.tendersCreated}</td>
                <td>{run.tendersUpdated}</td>
                <td>{run.noticesRejected + run.noticeErrors}</td>
                <td className="error-cell">{run.errorMessage ?? ""}</td>
              </tr>
            ))}
            {data.runs.length === 0 && (
              <tr>
                <td colSpan={13}>Aucune collecte pour l&apos;instant.</td>
              </tr>
            )}
          </tbody>
        </table>
      )}
    </div>
  );
}
