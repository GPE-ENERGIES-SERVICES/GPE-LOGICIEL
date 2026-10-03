"use client";

import Link from "next/link";

import { ANALYSIS_STATUS_LABELS } from "@/lib/analysis";
import { api } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import type { AnalysisJournalEntry, AnalysisQuality } from "@/lib/types";
import { useLoader } from "@/lib/useLoader";

const costFormat = new Intl.NumberFormat("fr-FR", { style: "currency", currency: "EUR", maximumFractionDigits: 4 });

function cost(value: number | null): string {
  return value === null ? "–" : costFormat.format(value);
}

/**
 * Journal of the AI analyses (status, errors, cost) and measure of their
 * quality from the decisions of the users.
 */
export function AnalysisJournal() {
  const journal = useLoader(() => api<AnalysisJournalEntry[]>("/api/analyses?limit=30"), "analyses");
  const quality = useLoader(() => api<AnalysisQuality>("/api/analyses/quality"), "quality");

  return (
    <div>
      {quality.error && <p className="error">{quality.error}</p>}
      {quality.data && (
        <>
          <p>
            {quality.data.done} analyse{quality.data.done > 1 ? "s" : ""} terminée{quality.data.done > 1 ? "s" : ""},{" "}
            {quality.data.failed} en échec — coût total {cost(quality.data.totalCost)}, coût moyen par dossier{" "}
            {cost(quality.data.averageCost)}
          </p>
          <p className="hint">
            Les décisions des utilisateurs mesurent la qualité de l&apos;analyse : une valeur corrigée ou rejetée est une
            erreur du modèle. « Source vérifiée » compte les extraits retrouvés dans les documents.
          </p>
          <table>
            <thead>
              <tr>
                <th>Information</th>
                <th>Réponses</th>
                <th>Source vérifiée</th>
                <th>Non indiquée</th>
                <th>Validées</th>
                <th>Corrigées</th>
                <th>Rejetées</th>
              </tr>
            </thead>
            <tbody>
              {quality.data.fields.map((field) => (
                <tr key={field.key}>
                  <td>{field.label}</td>
                  <td>{field.answers}</td>
                  <td>{field.verified}</td>
                  <td>{field.notIndicated}</td>
                  <td>{field.validated}</td>
                  <td>{field.corrected}</td>
                  <td>{field.rejected}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}

      <h3 className="journal-title">Dernières analyses</h3>
      {journal.error && <p className="error">{journal.error}</p>}
      {journal.data && (
        <table>
          <thead>
            <tr>
              <th>Date</th>
              <th>Appel d&apos;offres</th>
              <th>Statut</th>
              <th>Modèle / instructions</th>
              <th>Tokens (entrée / sortie)</th>
              <th>Coût</th>
              <th>Erreur</th>
            </tr>
          </thead>
          <tbody>
            {journal.data.map((entry) => (
              <tr key={entry.id} className={entry.status === "FAILED" ? "row-error" : ""}>
                <td>{formatDateTime(entry.createdAt)}</td>
                <td>
                  <Link href={`/tenders/${entry.tenderId}`}>{entry.tenderTitle ?? `#${entry.tenderId}`}</Link>
                  <span className="muted"> (v{entry.dossierVersion})</span>
                </td>
                <td>{ANALYSIS_STATUS_LABELS[entry.status]}</td>
                <td>
                  {entry.model}
                  <br />
                  <span className="muted">{entry.promptVersion}</span>
                </td>
                <td>{entry.inputTokens === null ? "–" : `${entry.inputTokens} / ${entry.outputTokens}`}</td>
                <td>{cost(entry.cost)}</td>
                <td className="error-cell">{entry.errorMessage ?? ""}</td>
              </tr>
            ))}
            {journal.data.length === 0 && (
              <tr>
                <td colSpan={7}>Aucune analyse pour l&apos;instant.</td>
              </tr>
            )}
          </tbody>
        </table>
      )}
    </div>
  );
}
