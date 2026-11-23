"use client";


import { useLoader } from "@/lib/useLoader";
import { getAnalysisConfiguration } from "@/services/analysisApi";
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
export function AnalysisConfigurationPanel() {
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

