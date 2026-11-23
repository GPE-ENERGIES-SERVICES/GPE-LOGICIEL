"use client";

import { useState } from "react";

import { useAiRun } from "@/components/AiProgress";
import { ComponentsPanel } from "@/components/estimate/ComponentsPanel";
import { ImportForm } from "@/components/estimate/EstimateEditor";
import { formatNumber } from "@/lib/estimate";
import { formatDateTime } from "@/lib/format";
import { refreshData, useLoader } from "@/lib/useLoader";
import {
  createEstimate,
  draftEstimateWithAi,
  getAssistant,
  getEstimate,
  getEstimates,
  getRates,
  getSuggestions,
  proposeTechnicalSolutions,
  validateItemTechnical,
  writeItemTechnical,
} from "@/services/estimateApi";
import type { Estimate, EstimateItem, TechnicalStatus } from "@/types/estimate";

const STATUS_LABELS: Record<TechnicalStatus, string> = {
  COMPLETE: "Complète",
  TO_CONFIRM: "À confirmer",
  INSUFFICIENT: "Insuffisante",
};

const STATUS_CLASSES: Record<TechnicalStatus, string> = {
  COMPLETE: "badge verified",
  TO_CONFIRM: "badge",
  INSUFFICIENT: "badge urgent",
};

function StatusBadge({ item }: { item: EstimateItem }) {
  const status = item.technical.status;
  return (
    <>
      <span className={status ? STATUS_CLASSES[status] : "badge muted-badge"}>
        {status ? STATUS_LABELS[status] : "Non étudiée"}
      </span>
      {item.technical.validatedAt && (
        <span className="badge verified">Validée</span>
      )}
    </>
  );
}

/**
 * The lines of the price schedule of a tender, studied one by one: what the
 * documents say, the technical solution retained and its resources. They are
 * the items of the draft estimate and their components: what is decided here
 * is what the estimate prices, without any copy.
 */
export function TechnicalItems({ tenderId }: { tenderId: number }) {
  const list = useLoader(() => getEstimates(tenderId), String(tenderId));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!list.data) {
    return list.error ? (
      <p className="banner error">{list.error}</p>
    ) : (
      <p className="muted">Chargement…</p>
    );
  }
  const draft = list.data
    .filter((estimate) => estimate.status === "DRAFT")
    .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))[0];
  if (draft) {
    return <ItemsWorkspace estimateId={draft.id} tenderId={tenderId} />;
  }
  if (list.data.length > 0) {
    return (
      <p className="muted">
        Le chiffrage est validé : pour changer ses choix techniques, créez-en
        une nouvelle version dans l&apos;onglet « Chiffrage ».
      </p>
    );
  }
  return (
    <div>
      <p className="muted">
        Les prestations viennent du DPGF : créez le chiffrage, puis importez-le.
        Les choix faits ici sont ceux que le chiffrage calcule.
      </p>
      {error && <p className="banner error">{error}</p>}
      <button
        type="button"
        className="primary"
        disabled={busy}
        onClick={async () => {
          setBusy(true);
          setError(null);
          try {
            await createEstimate(tenderId, {
              label: null,
              vatRate: null,
              coefficients: {},
            });
            refreshData(String(tenderId));
          } catch (err) {
            setError(
              err instanceof Error ? err.message : "Création impossible.",
            );
          } finally {
            setBusy(false);
          }
        }}
      >
        Créer le chiffrage
      </button>
    </div>
  );
}

function ItemsWorkspace({
  estimateId,
  tenderId,
}: {
  estimateId: number;
  tenderId: number;
}) {
  const loaded = useLoader(() => getEstimate(estimateId), String(estimateId));
  const rates = useLoader(() => getRates(), "rates");
  const assistant = useLoader(() => getAssistant(), "assistant");
  const [revision, setRevision] = useState(0);
  const suggestions = useLoader(
    () => getSuggestions(estimateId),
    `${estimateId}#${revision}#technical`,
  );
  const [selected, setSelected] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const solutionsAi = useAiRun(
    "ESTIMATE_TECHNICAL",
    "L'IA étudie chaque prestation à partir du DPGF et du DCE…",
  );
  const resourcesAi = useAiRun(
    "ESTIMATE_DRAFT",
    "L'IA propose les ressources des prestations sans coût…",
  );
  const estimate = loaded.data;

  if (!estimate) {
    return loaded.error ? (
      <p className="banner error">{loaded.error}</p>
    ) : (
      <p className="muted">Chargement…</p>
    );
  }
  const editable = estimate.status === "DRAFT";
  const aiAvailable = editable && assistant.data?.available === true;
  // The tab of the estimate shows the same data: it is read again at once.
  const changed = () => {
    refreshData(String(estimateId), String(tenderId));
    setRevision((value) => value + 1);
  };
  const act = async (
    action: () => Promise<unknown>,
    done?: (result: unknown) => void,
  ) => {
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      done?.(await action());
      changed();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Action impossible.");
    } finally {
      setBusy(false);
    }
  };

  const items = estimate.items.filter((item) => item.priceMode !== "TEXT");
  if (items.length === 0) {
    return (
      <div>
        <h3>Importer les prestations du DPGF</h3>
        <ImportForm estimate={estimate} onImported={changed} />
      </div>
    );
  }
  const current = items.find((item) => item.id === selected) ?? items[0];
  const count = (status: TechnicalStatus | null) =>
    items.filter((item) => item.technical.status === status).length;

  return (
    <div>
      <p className="hint">
        {items.length} prestation(s) : {count("COMPLETE")} complète(s),{" "}
        {count("TO_CONFIRM")} à confirmer, {count("INSUFFICIENT")}{" "}
        insuffisante(s), {count(null)} non étudiée(s) ;{" "}
        {items.filter((item) => item.technical.validatedAt).length} validée(s).
      </p>
      {editable && (
        <div className="inline-form">
          <button
            type="button"
            className="primary"
            disabled={busy || !aiAvailable}
            title={aiAvailable ? undefined : assistant.data?.message}
            onClick={() =>
              act(
                () =>
                  solutionsAi.track(() =>
                    proposeTechnicalSolutions(estimate.id),
                  ),
                (result) => {
                  const { proposed, notSent } = result as {
                    proposed: number;
                    notSent: string[];
                  };
                  setNotice(
                    `${proposed} solution(s) proposée(s), à vérifier.` +
                      (notSent.length > 0
                        ? ` Non lus (trop longs) : ${notSent.join(", ")}.`
                        : ""),
                  );
                },
              )
            }
          >
            Proposer les solutions (IA)
          </button>
          <button
            type="button"
            disabled={busy || !aiAvailable}
            title="Fournitures du catalogue et main-d'œuvre aux taux GPE, pour les prestations sans coût, selon la solution retenue"
            onClick={() =>
              act(
                () => resourcesAi.track(() => draftEstimateWithAi(estimate.id)),
                (result) =>
                  setNotice(
                    `${(result as unknown[]).length} proposition(s) de ressources, à accepter sous chaque prestation.`,
                  ),
              )
            }
          >
            Proposer les ressources (IA)
          </button>
        </div>
      )}
      {solutionsAi.view}
      {resourcesAi.view}
      {notice && <p className="banner info">{notice}</p>}
      {error && <p className="banner error">{error}</p>}

      <div className="tech-items">
        <ul className="tech-items-list" aria-label="Prestations du DPGF">
          {items.map((item) => (
            <li key={item.id}>
              <button
                type="button"
                aria-current={item.id === current.id ? "true" : undefined}
                onClick={() => setSelected(item.id)}
              >
                <span className="tech-item-name">
                  {item.reference && <strong>{item.reference} </strong>}
                  {item.designation}
                </span>
                <span className="tech-item-meta">
                  {item.quantity !== null ? formatNumber(item.quantity) : "–"}{" "}
                  {item.unit ?? ""} <StatusBadge item={item} />
                </span>
              </button>
            </li>
          ))}
        </ul>
        <ItemDetail
          key={`${current.id}#${current.technical.origin}#${current.technical.validatedAt}#${current.technical.type}`}
          estimate={estimate}
          item={current}
          editable={editable}
          aiAvailable={aiAvailable}
          rates={rates.data ?? []}
          suggestions={(suggestions.data ?? []).filter(
            (suggestion) => suggestion.itemId === current.id,
          )}
          onChanged={changed}
          onSuggestions={() => setRevision((value) => value + 1)}
        />
      </div>
    </div>
  );
}

function ItemDetail({
  estimate,
  item,
  editable,
  aiAvailable,
  rates,
  suggestions,
  onChanged,
  onSuggestions,
}: {
  estimate: Estimate;
  item: EstimateItem;
  editable: boolean;
  aiAvailable: boolean;
  rates: Parameters<typeof ComponentsPanel>[0]["rates"];
  suggestions: Parameters<typeof ComponentsPanel>[0]["suggestions"];
  onChanged: () => void;
  onSuggestions: () => void;
}) {
  const solution = item.technical;
  const [type, setType] = useState(solution.type ?? "");
  const [characteristics, setCharacteristics] = useState(
    solution.characteristics ?? "",
  );
  const [method, setMethod] = useState(solution.method ?? "");
  const [status, setStatus] = useState<string>(solution.status ?? "");
  const [missing, setMissing] = useState(solution.missing.join("\n"));
  const [dirty, setDirty] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const edit = (setter: (value: string) => void) => (value: string) => {
    setter(value);
    setDirty(true);
  };

  const act = async (action: () => Promise<unknown>) => {
    setBusy(true);
    setError(null);
    try {
      await action();
      setDirty(false);
      onChanged();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Action impossible.");
    } finally {
      setBusy(false);
    }
  };
  const save = () =>
    writeItemTechnical(item.id, {
      type: type.trim() || null,
      characteristics: characteristics.trim() || null,
      method: method.trim() || null,
      status: status || null,
      missing: missing
        .split("\n")
        .map((line) => line.trim())
        .filter(Boolean),
    });

  return (
    <section
      className="tech-item-detail"
      aria-label={`Prestation ${item.designation}`}
    >
      <h3>
        {item.reference && `${item.reference} · `}
        {item.designation}
      </h3>
      <p className="muted">
        {item.quantity !== null
          ? formatNumber(item.quantity)
          : "Quantité non indiquée"}{" "}
        {item.unit ?? ""}
        {item.chapter && ` · ${item.chapter}`}
        {item.sourceRow !== null &&
          ` · ligne ${item.sourceRow}${item.sourceSheet ? ` de « ${item.sourceSheet} »` : ""}`}
      </p>

      <h4>Ce que disent les documents</h4>
      {solution.facts.length === 0 ? (
        <p className="muted">
          Aucune information sourcée : la désignation du DPGF seule.
        </p>
      ) : (
        <ul>
          {solution.facts.map((fact) => (
            <li key={`${fact.text}#${fact.page}`}>
              {fact.text}{" "}
              <span className="badge" title={fact.quote ?? undefined}>
                {fact.fileName?.replace(/^.*\//, "")}
                {fact.page ? ` p. ${fact.page}` : ""}
              </span>
            </li>
          ))}
        </ul>
      )}

      <h4>
        Solution retenue <StatusBadge item={item} />
        {solution.origin === "AI" && (
          <span className="badge urgent">Proposée par l&apos;IA</span>
        )}
      </h4>
      <div className="form-grid">
        <label className="wide">
          Type de solution
          <input
            value={type}
            maxLength={300}
            disabled={!editable}
            onChange={(event) => edit(setType)(event.target.value)}
          />
        </label>
        <label className="wide">
          Caractéristiques techniques
          <textarea
            rows={3}
            value={characteristics}
            disabled={!editable}
            onChange={(event) => edit(setCharacteristics)(event.target.value)}
          />
        </label>
        <label className="wide">
          Méthode d&apos;exécution
          <textarea
            rows={2}
            value={method}
            disabled={!editable}
            onChange={(event) => edit(setMethod)(event.target.value)}
          />
        </label>
        <label>
          Informations
          <select
            value={status}
            disabled={!editable}
            onChange={(event) => edit(setStatus)(event.target.value)}
          >
            <option value="">Non étudiée</option>
            {Object.entries(STATUS_LABELS).map(([code, label]) => (
              <option key={code} value={code}>
                {label}
              </option>
            ))}
          </select>
        </label>
        <label className="wide">
          Manquant ou à confirmer (une ligne par point)
          <textarea
            rows={3}
            value={missing}
            disabled={!editable}
            onChange={(event) => edit(setMissing)(event.target.value)}
          />
        </label>
      </div>
      {error && <p className="banner error">{error}</p>}
      {editable && (
        <div className="form-actions">
          <button
            type="button"
            className="primary"
            disabled={busy || !dirty}
            onClick={() => act(save)}
          >
            Enregistrer
          </button>
          <button
            type="button"
            disabled={busy || (!dirty && solution.validatedAt !== null)}
            onClick={() =>
              act(async () => {
                if (dirty) {
                  await save();
                }
                await validateItemTechnical(item.id);
              })
            }
          >
            {dirty ? "Enregistrer et valider" : "Valider la solution"}
          </button>
          {solution.validatedAt && (
            <span className="hint">
              Validée par {solution.validatedBy} le{" "}
              {formatDateTime(solution.validatedAt)}.
            </span>
          )}
        </div>
      )}

      <h4>Ressources (une unité de la prestation)</h4>
      <p className="hint">
        Fournitures, main-d&apos;œuvre, location, sous-traitance… : elles font
        le coût de la prestation dans le chiffrage. Un prix absent de la base
        fournisseurs reste « à saisir ».
      </p>
      <ComponentsPanel
        estimate={estimate}
        item={item}
        rates={rates}
        editable={editable}
        aiAvailable={aiAvailable}
        suggestions={suggestions}
        onChanged={onChanged}
        onSuggestions={onSuggestions}
      />
    </section>
  );
}
