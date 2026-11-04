"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";

import {
  COMPONENT_TYPE_LABELS,
  COMPONENT_TYPES,
  ESTIMATE_STATUS_LABELS,
  EVENT_LABELS,
  formatMoney,
  formatNumber,
  formatRate,
  numberInput,
  parseNumber,
  parsePercent,
  percentInput,
  textOrNull,
} from "@/lib/estimate";
import { formatDateTime } from "@/lib/format";
import { useLoader } from "@/lib/useLoader";
import { getDossiers } from "@/services/documentApi";
import {
  acceptSuggestion,
  checkSupplierPrices,
  copyRatesAgain,
  copySupplierPrices,
  deleteEstimate,
  estimateExportUrl,
  getAssistant,
  getEstimate,
  getEstimateEvents,
  getRates,
  getSuggestions,
  getSupplierConnectors,
  importPriceSchedule,
  newEstimateVersion,
  rejectSuggestion,
  reviewEstimate,
  updateEstimate,
  validateEstimate,
} from "@/services/estimateApi";
import { DEMO_DATA } from "@/services/http";
import type { ComponentType, Estimate, SupplierPriceLine } from "@/types/estimate";

import { AvailabilityLine } from "./ComponentsPanel";
import { EstimateGrid } from "./EstimateGrid";

type Panel = "import" | "settings" | "history" | "supplier" | null;

/**
 * An estimate: its items as in the price schedule, the costs of each item,
 * the totals computed by the backend, and what can be done with it. A
 * validated version is shown read-only.
 */
export function EstimateEditor({ id }: { id: number }) {
  const router = useRouter();
  const loaded = useLoader(() => getEstimate(id), String(id));
  const rates = useLoader(() => getRates(), "rates");
  const assistant = useLoader(() => getAssistant(), "assistant");
  const connectors = useLoader(() => getSupplierConnectors(), "supplier-connectors");
  // Bumped after each change: the journal and the proposals are read again.
  const [revision, setRevision] = useState(0);
  const events = useLoader(() => getEstimateEvents(id), `${id}#${revision}`);
  const suggestions = useLoader(() => getSuggestions(id), `${id}#${revision}`);
  const [saved, setSaved] = useState<Estimate | null>(null);
  const [busy, setBusy] = useState(false);
  const [panel, setPanel] = useState<Panel>(null);
  const [error, setError] = useState<string | null>(null);
  const estimate = saved && saved.id === id ? saved : loaded.data;

  if (loaded.error) {
    return <p className="banner error">{loaded.error}</p>;
  }
  if (!estimate) {
    return <p className="page-message">Chargement…</p>;
  }

  const editable = estimate.status === "DRAFT";
  const aiAvailable = editable && assistant.data?.available === true;
  const changed = (next: Estimate) => {
    setSaved(next);
    setRevision((value) => value + 1);
  };
  const run = async (action: () => Promise<Estimate | void>, after?: (result: Estimate) => void) => {
    setBusy(true);
    setError(null);
    try {
      const result = await action();
      if (result) {
        changed(result);
        after?.(result);
      } else {
        setRevision((value) => value + 1);
      }
      return true;
    } catch (err) {
      setError(err instanceof Error ? err.message : "Action impossible.");
      return false;
    } finally {
      setBusy(false);
    }
  };
  const checks = (suggestions.data ?? []).filter((suggestion) => suggestion.kind === "CHECK" && suggestion.status === "PENDING");
  const show = (name: Panel) => setPanel((current) => (current === name ? null : name));

  return (
    <article className="estimate-page">
      <p>
        <Link href={`/tenders/${estimate.tenderId}#chiffrage`}>← Retour au marché</Link>
      </p>

      <div className="sheet-header">
        <h1>
          Chiffrage v{estimate.version}
          {estimate.lotNumber && ` · lot ${estimate.lotNumber}`}
          {estimate.label && ` · ${estimate.label}`}
        </h1>
        <span className={estimate.status === "VALIDATED" ? "badge verified" : "badge"}>
          {ESTIMATE_STATUS_LABELS[estimate.status]}
          {estimate.validatedBy && ` par ${estimate.validatedBy}`}
        </span>
      </div>

      <div className="sheet-toolbar" role="toolbar" aria-label="Actions du chiffrage">
        {editable ? (
          <button
            type="button"
            className="primary"
            disabled={busy}
            onClick={() => {
              if (window.confirm("Valider ce chiffrage ? Il sera figé ; une modification passera par une nouvelle version.")) {
                run(() => validateEstimate(estimate.id));
              }
            }}
          >
            Valider
          </button>
        ) : (
          <button
            type="button"
            className="primary"
            disabled={busy}
            onClick={() => run(() => newEstimateVersion(estimate.id), (created) => router.push(`/estimates/${created.id}`))}
          >
            Nouvelle version
          </button>
        )}
        {!DEMO_DATA && (
          <a className="button" href={estimateExportUrl(estimate.id)} download>
            Exporter Excel
          </a>
        )}
        {editable && (
          <button type="button" aria-pressed={panel === "import"} onClick={() => show("import")}>
            Importer un DPGF
          </button>
        )}
        {editable && (
          <button type="button" aria-pressed={panel === "settings"} onClick={() => show("settings")}>
            TVA {formatRate(estimate.vatRate)}
          </button>
        )}
        {editable && connectors.data?.some((connector) => connector.available) && (
          <button type="button" aria-pressed={panel === "supplier"} onClick={() => show("supplier")}>
            Actualiser les prix fournisseurs
          </button>
        )}
        <button type="button" className="secondary" aria-pressed={panel === "history"} onClick={() => show("history")}>
          Historique
        </button>
        {aiAvailable && (
          <button type="button" className="secondary" disabled={busy} onClick={() => run(() => reviewEstimate(estimate.id).then(() => undefined))}>
            Relecture IA
          </button>
        )}
        {editable && (
          <button
            type="button"
            className="secondary danger"
            disabled={busy}
            onClick={() => {
              if (window.confirm("Supprimer ce brouillon et toutes ses lignes ?")) {
                run(() => deleteEstimate(estimate.id).then(() => router.push(`/tenders/${estimate.tenderId}#chiffrage`)));
              }
            }}
          >
            Supprimer
          </button>
        )}
      </div>

      {panel === "import" && editable && (
        <div className="sheet-panel">
          <ImportForm estimate={estimate} onImported={changed} />
        </div>
      )}
      {panel === "settings" && editable && (
        <div className="sheet-panel">
          <SettingsForm estimate={estimate} onSaved={changed} />
          <button type="button" className="secondary" disabled={busy} onClick={() => run(() => copyRatesAgain(estimate.id))}>
            Recopier les taux GPE actuels dans le détail
          </button>
        </div>
      )}
      {panel === "supplier" && editable && (
        <div className="sheet-panel">
          <SupplierPricesPanel
            estimate={estimate}
            onCopied={(next) => {
              changed(next);
              setPanel(null);
            }}
          />
        </div>
      )}
      {panel === "history" && (
        <div className="sheet-panel">
          {events.data && events.data.length > 0 ? (
            <ul className="plain-list">
              {events.data.map((event) => (
                <li key={event.id}>
                  {formatDateTime(event.createdAt)} — <strong>{EVENT_LABELS[event.kind]}</strong>
                  {event.detail && ` : ${event.detail}`}
                  {event.createdBy && <span className="muted"> · {event.createdBy}</span>}
                </li>
              ))}
            </ul>
          ) : (
            <p className="muted">Aucun événement.</p>
          )}
        </div>
      )}
      {error && <p className="banner error">{error}</p>}

      {checks.length > 0 && (
        <div className="sheet-panel">
          <strong>Points à vérifier signalés par l&apos;IA</strong>
          <ul className="plain-list">
            {checks.map((check) => {
              const item = estimate.items.find((candidate) => candidate.id === check.itemId);
              return (
                <li key={check.id}>
                  {item && <strong>{item.reference ?? `Ligne ${item.position}`} : </strong>}
                  {check.text}{" "}
                  <button type="button" className="secondary" disabled={busy} onClick={() => run(() => acceptSuggestion(check.id))}>
                    Vu
                  </button>
                  <button type="button" className="secondary" disabled={busy} onClick={() => run(() => rejectSuggestion(check.id))}>
                    Sans objet
                  </button>
                </li>
              );
            })}
          </ul>
        </div>
      )}

      <EstimateGrid
        estimate={estimate}
        editable={editable}
        rates={rates.data ?? []}
        aiAvailable={aiAvailable}
        suggestions={suggestions.data ?? []}
        onChanged={changed}
        onSuggestions={() => setRevision((value) => value + 1)}
      />

      {(estimate.incompleteItems > 0 || estimate.itemsToCheck > 0) && (
        <p className="sheet-status">
          {estimate.incompleteItems > 0 && `${estimate.incompleteItems} ligne(s) sans montant (prix ou quantité manquant). `}
          {estimate.itemsToCheck > 0 && `${estimate.itemsToCheck} ligne(s) importée(s) à vérifier.`}
        </p>
      )}
    </article>
  );
}

const NO_PRICE_LABELS: Partial<Record<SupplierPriceLine["status"], string>> = {
  NOT_PRICED: "Pas de prix du fournisseur",
  NOT_CONNECTED: "Sans trigramme ou fournisseur non connecté",
};

/**
 * The prices of the suppliers beside the costs of the draft, for the quantities
 * of the whole estimate. Nothing changes in the draft until the user copies them.
 */
function SupplierPricesPanel({ estimate, onCopied }: { estimate: Estimate; onCopied: (estimate: Estimate) => void }) {
  const lines = useLoader(() => checkSupplierPrices(estimate.id), String(estimate.id));
  const [copying, setCopying] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (lines.error) {
    return <p className="banner error">{lines.error}</p>;
  }
  if (!lines.data) {
    return <p className="muted">Demande au fournisseur…</p>;
  }
  if (lines.data.length === 0) {
    return <p className="muted">Aucun coût de ce chiffrage ne vient d&apos;une offre de fournisseur.</p>;
  }
  const changes = lines.data.filter((line) => line.newCost !== null && line.newCost !== line.currentCost);
  const copy = async () => {
    setCopying(true);
    setError(null);
    try {
      onCopied(await copySupplierPrices(estimate.id));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Copie impossible.");
      setCopying(false);
    }
  };
  return (
    <>
      {lines.data.some((line) => line.priceSource === "REXEL_TEST") && (
        <p className="banner info">Connexion en TEST : ces prix ne sont pas ceux du compte réel, ne chiffrez pas avec.</p>
      )}
      <table>
        <thead>
          <tr>
            <th>Poste</th>
            <th>Fourniture</th>
            <th className="number">À acheter</th>
            <th className="number">Coût actuel</th>
            <th className="number">Prix fournisseur</th>
            <th>Disponibilité</th>
          </tr>
        </thead>
        <tbody>
          {lines.data.map((line) => (
            <tr key={line.componentId}>
              <td>{line.itemReference ?? line.itemDesignation}</td>
              <td>{line.label}</td>
              <td className="number">{formatNumber(line.quantityToBuy)}</td>
              <td className="number">{formatMoney(line.currentCost)}</td>
              <td className="number">
                {line.newCost === null ? (
                  <span className="muted">{NO_PRICE_LABELS[line.status]}</span>
                ) : (
                  <>
                    {line.newCost === line.currentCost ? formatMoney(line.newCost) : <strong>{formatMoney(line.newCost)}</strong>}
                    {line.priceLabel && !/unitaire/i.test(line.priceLabel) && <span className="error"> {line.priceLabel}</span>}
                  </>
                )}
              </td>
              <td>{line.availability && <AvailabilityLine availability={line.availability} />}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <div className="form-actions">
        <button type="button" className="primary" disabled={copying || changes.length === 0} onClick={copy}>
          {changes.length === 0 ? "Aucun écart" : `Recopier ${changes.length} prix dans le chiffrage`}
        </button>
        {error && <span className="error">{error}</span>}
      </div>
    </>
  );
}

/** The VAT rate and the sale coefficient of each type of cost of this estimate. */
function SettingsForm({ estimate, onSaved }: { estimate: Estimate; onSaved: (estimate: Estimate) => void }) {
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const vatRate = parsePercent(String(form.get("vat") ?? ""));
    const coefficients: Partial<Record<ComponentType, number>> = {};
    for (const type of COMPONENT_TYPES) {
      const value = parseNumber(String(form.get(type) ?? ""));
      if (value === null || Number.isNaN(value) || value <= 0) {
        setError(`Coefficient « ${COMPONENT_TYPE_LABELS[type]} » invalide.`);
        return;
      }
      coefficients[type] = value;
    }
    if (vatRate === null || Number.isNaN(vatRate)) {
      setError("Taux de TVA invalide.");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      onSaved(await updateEstimate(estimate.id, { label: textOrNull(String(form.get("label") ?? "")), vatRate, coefficients }));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Enregistrement impossible.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <form onSubmit={submit} key={estimate.updatedAt}>
      <div className="form-grid">
        <label>
          Libellé
          <input name="label" maxLength={200} defaultValue={estimate.label ?? ""} />
        </label>
        <label>
          TVA (%)
          <input name="vat" required inputMode="decimal" defaultValue={percentInput(estimate.vatRate)} />
        </label>
        {COMPONENT_TYPES.map((type) => (
          <label key={type}>
            Coefficient {COMPONENT_TYPE_LABELS[type].toLowerCase()}
            <input name={type} required inputMode="decimal" defaultValue={numberInput(estimate.coefficients[type])} />
          </label>
        ))}
      </div>
      <p className="hint">Prix de vente d&apos;un coût = coût × coefficient de son type (frais généraux et marge compris).</p>
      <div className="form-actions">
        <button type="submit" className="primary" disabled={saving}>
          {saving ? "Enregistrement…" : "Enregistrer et recalculer"}
        </button>
        {error && <span className="error">{error}</span>}
      </div>
    </form>
  );
}

/** The price schedules of the DCE of the tender, the DPGF, BPU and DQE first. */
function ImportForm({ estimate, onImported }: { estimate: Estimate; onImported: (estimate: Estimate) => void }) {
  const dossiers = useLoader(() => getDossiers(estimate.tenderId), String(estimate.tenderId));
  const [importing, setImporting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const documents = (dossiers.data ?? [])
    .flatMap((dossier) => dossier.documents.map((document) => ({ ...document, version: dossier.version })))
    .filter((document) => document.state === "READ" || document.state === "SCANNED")
    .sort((a, b) => Number(isSchedule(b.pieceType)) - Number(isSchedule(a.pieceType)));

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const documentId = Number(new FormData(event.currentTarget).get("document"));
    setImporting(true);
    setError(null);
    try {
      onImported(await importPriceSchedule(estimate.id, documentId));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Import impossible.");
    } finally {
      setImporting(false);
    }
  };

  if (dossiers.data && documents.length === 0) {
    return <p className="muted">Aucun document lu dans le DCE de ce marché : déposez-le dans l&apos;onglet « DCE et documents ».</p>;
  }
  return (
    <form className="inline-form" onSubmit={submit}>
      <select name="document" required>
        {documents.map((document) => (
          <option key={document.id} value={document.id}>
            {document.pieceType} · {document.fileName} (DCE v{document.version})
          </option>
        ))}
      </select>
      <button type="submit" disabled={importing || documents.length === 0}>
        {importing ? "Import…" : "Importer les lignes"}
      </button>
      {error && <span className="error">{error}</span>}
      <p className="hint">
        Les lignes (n°, désignation, unité, quantité, chapitre) sont ajoutées à la fin ; les prix du fichier ne sont pas repris.
        Les lignes d&apos;un PDF sont à vérifier.
      </p>
    </form>
  );
}

function isSchedule(type: string): boolean {
  return type === "DPGF" || type === "BPU" || type === "DQE";
}
