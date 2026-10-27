"use client";

import Link from "next/link";

import { ANALYSIS_STATUS_LABELS } from "@/lib/analysis";
import { formatDateTime } from "@/lib/format";
import { useLoader } from "@/lib/useLoader";
import { getAnalysisConfiguration, getAnalysisJournal, getAnalysisQuality } from "@/services/analysisApi";
import type { AnalysisState } from "@/types/analysis";

const costFormat = new Intl.NumberFormat("fr-FR", { style: "currency", currency: "EUR", maximumFractionDigits: 4 });

function cost(value: number | null): string {
  return value === null ? "–" : costFormat.format(value);
}

const STATE_LABELS: Record<AnalysisState, string> = {
  DISABLED: "Désactivée",
  MISCONFIGURED: "Configuration incohérente",
  NOT_CONFIGURED: "Fournisseur sans clé",
  TRANSMISSION_NOT_ALLOWED: "Envoi des documents non autorisé",
  BUDGET_REACHED: "Plafond mensuel atteint",
  READY: "Disponible",
};

/**
 * Configuration as the server applies it: read only. The provider, its key
 * and the permission to send documents are set on the server, never here.
 */
function ConfigurationPanel() {
  const loaded = useLoader(() => getAnalysisConfiguration(), "configuration");
  if (loaded.error) {
    return <p className="error">{loaded.error}</p>;
  }
  const configuration = loaded.data;
  if (!configuration) {
    return <p className="muted">Chargement…</p>;
  }
  const { limits, queue, monthToDate } = configuration;
  return (
    <div className="analysis-configuration">
      <p>
        <span className={configuration.state === "READY" ? "badge verified" : "badge"}>
          {STATE_LABELS[configuration.state]}
        </span>{" "}
        {configuration.message}
      </p>
      {configuration.problems.length > 0 && (
        <ul className="error">
          {configuration.problems.map((problem) => (
            <li key={problem}>{problem}</li>
          ))}
        </ul>
      )}
      <table>
        <tbody>
          <tr>
            <th>Fournisseur</th>
            <td>
              {configuration.provider}
              {configuration.model && <span className="muted"> ({configuration.model})</span>}
              {configuration.availableProviders.length === 0 && (
                <span className="muted"> — aucun fournisseur intégré à l&apos;application</span>
              )}
            </td>
          </tr>
          <tr>
            <th>Envoi des documents à un fournisseur externe</th>
            <td>{configuration.externalTransmissionAllowed ? "Autorisé" : "Non autorisé"}</td>
          </tr>
          <tr>
            <th>Limites d&apos;une analyse</th>
            <td>
              {limits.maxDocumentsPerTender} documents, {limits.maxCharacters.toLocaleString("fr-FR")} caractères,{" "}
              réponse de {limits.maxOutputTokens.toLocaleString("fr-FR")} tokens au plus, attente{" "}
              {Math.round(limits.requestTimeoutSeconds / 60)} min
            </td>
          </tr>
          <tr>
            <th>File</th>
            <td>
              {limits.maxConcurrentRequests} appel{limits.maxConcurrentRequests > 1 ? "s" : ""} simultané
              {limits.maxConcurrentRequests > 1 ? "s" : ""}, {limits.maxAttempts} tentatives — {queue.PENDING} en
              attente, {queue.PROCESSING} en cours, {queue.RETRY_WAITING} à retenter, {queue.DONE} terminées,{" "}
              {queue.FAILED} en échec, {queue.CANCELLED} annulées
            </td>
          </tr>
          <tr>
            <th>Consommation du mois</th>
            <td>
              {monthToDate.inputTokens.toLocaleString("fr-FR")} / {monthToDate.outputTokens.toLocaleString("fr-FR")}{" "}
              tokens (entrée / sortie), estimation {cost(monthToDate.estimatedCost)}
              {limits.monthlyBudget !== null && <> sur un plafond de {cost(limits.monthlyBudget)}</>}
              {!limits.pricesSet && <span className="muted"> — prix du modèle non renseignés</span>}
            </td>
          </tr>
        </tbody>
      </table>
      <p className="hint">
        Le coût affiché est une estimation de GPE (tokens × prix configurés), pas la facture du fournisseur : un plafond
        doit aussi être fixé chez lui. L&apos;activation se fait sur le serveur (voir la documentation technique).
      </p>
    </div>
  );
}

/**
 * Configuration of the AI analysis, journal of the analyses (status, errors,
 * cost) and measure of their quality from the decisions of the users.
 */
export function AnalysisJournal() {
  const journal = useLoader(() => getAnalysisJournal(30), "analyses");
  const quality = useLoader(() => getAnalysisQuality(), "quality");

  return (
    <div>
      <ConfigurationPanel />
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
                  {entry.provider ? `${entry.provider} / ${entry.model}` : entry.model}
                  <br />
                  <span className="muted">
                    {entry.promptVersion}
                    {entry.attempts > 1 && `, ${entry.attempts} tentatives`}
                  </span>
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
