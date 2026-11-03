"use client";

import { useState, type FormEvent } from "react";

import {
  COMPONENT_TYPE_LABELS,
  COMPONENT_TYPES,
  formatMoney,
  formatNumber,
  formatRate,
  numberInput,
  parseNumber,
  parsePercent,
  percentInput,
  textOrNull,
} from "@/lib/estimate";
import { formatDate } from "@/lib/format";
import {
  acceptSuggestion,
  addComponent,
  deleteComponent,
  rejectSuggestion,
  searchOffers,
  suggestSupplies,
  updateComponent,
} from "@/services/estimateApi";
import type {
  ComponentInput,
  ComponentType,
  Estimate,
  EstimateComponent,
  EstimateItem,
  PricingRate,
  Suggestion,
  SupplierOffer,
} from "@/types/estimate";

type Props = {
  estimate: Estimate;
  item: EstimateItem;
  rates: PricingRate[];
  editable: boolean;
  aiAvailable: boolean;
  suggestions: Suggestion[];
  onChanged: (estimate: Estimate) => void;
  onSuggestions: () => void;
};

/**
 * The costs of one unit of an item (« sous-détail »), the forms to add one
 * by hand, from a rate of GPE or from an offer of a supplier, and the
 * proposals of the AI for its supplies.
 */
export function ComponentsPanel({
  estimate,
  item,
  rates,
  editable,
  aiAvailable,
  suggestions,
  onChanged,
  onSuggestions,
}: Props) {
  const [editing, setEditing] = useState<number | "new" | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const pending = suggestions.filter((suggestion) => suggestion.kind === "SUPPLY" && suggestion.status === "PENDING");

  const run = async (action: () => Promise<Estimate | void>) => {
    setBusy(true);
    setError(null);
    try {
      const result = await action();
      if (result) {
        onChanged(result);
      }
      return true;
    } catch (err) {
      setError(err instanceof Error ? err.message : "Action impossible.");
      return false;
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="estimate-components">
      {item.components.length === 0 ? (
        <p className="muted">Aucun coût : ajoutez la fourniture, la main-d&apos;œuvre… d&apos;une unité de ce poste.</p>
      ) : (
        <table>
          <thead>
            <tr>
              <th>Type</th>
              <th>Libellé</th>
              <th className="number">Qté / unité</th>
              <th>Unité</th>
              <th className="number">Coût unitaire</th>
              <th className="number">Perte</th>
              <th className="number">Coeff.</th>
              <th>Origine du coût</th>
              {editable && <th />}
            </tr>
          </thead>
          <tbody>
            {item.components.map((component) =>
              editing === component.id ? (
                <tr key={component.id}>
                  <td colSpan={editable ? 9 : 8}>
                    <ComponentForm
                      component={component}
                      rates={rates}
                      submitLabel="Enregistrer"
                      onCancel={() => setEditing(null)}
                      onSubmit={async (input) => {
                        if (await run(() => updateComponent(component.id, input))) {
                          setEditing(null);
                        }
                      }}
                    />
                  </td>
                </tr>
              ) : (
                <tr key={component.id} className={component.unitCost === null ? "row-error" : undefined}>
                  <td>{COMPONENT_TYPE_LABELS[component.type]}</td>
                  <td>{component.label}</td>
                  <td className="number">{formatNumber(component.quantity)}</td>
                  <td>{component.unit ?? ""}</td>
                  <td className="number">{component.unitCost === null ? "à saisir" : formatMoney(component.unitCost)}</td>
                  <td className="number">{component.wasteRate ? formatRate(component.wasteRate) : ""}</td>
                  <td className="number">
                    {component.coefficient !== null
                      ? formatNumber(component.coefficient)
                      : formatNumber(estimate.coefficients[component.type])}
                  </td>
                  <td className="muted">{origin(component)}</td>
                  {editable && (
                    <td className="row-actions">
                      <button type="button" className="secondary" onClick={() => setEditing(component.id)} disabled={busy}>
                        Modifier
                      </button>
                      <button
                        type="button"
                        className="secondary danger"
                        disabled={busy}
                        onClick={() => run(() => deleteComponent(component.id))}
                      >
                        Supprimer
                      </button>
                    </td>
                  )}
                </tr>
              ),
            )}
          </tbody>
        </table>
      )}

      {pending.length > 0 && (
        <div className="estimate-suggestions">
          <h4>Propositions de l&apos;IA (prix lu dans le catalogue une fois acceptée)</h4>
          <ul className="plain-list">
            {pending.map((suggestion) => (
              <li key={suggestion.id}>
                <span>
                  Offre n° {suggestion.offerId}
                  {suggestion.quantity !== null && ` · ${formatNumber(suggestion.quantity)} par unité`} — {suggestion.text}
                </span>{" "}
                {editable && (
                  <>
                    <button
                      type="button"
                      disabled={busy}
                      onClick={async () => {
                        if (await run(() => acceptSuggestion(suggestion.id))) {
                          onSuggestions();
                        }
                      }}
                    >
                      Accepter
                    </button>
                    <button
                      type="button"
                      className="secondary"
                      disabled={busy}
                      onClick={async () => {
                        if (await run(() => rejectSuggestion(suggestion.id))) {
                          onSuggestions();
                        }
                      }}
                    >
                      Refuser
                    </button>
                  </>
                )}
              </li>
            ))}
          </ul>
        </div>
      )}

      {editable &&
        (editing === "new" ? (
          <ComponentForm
            rates={rates}
            submitLabel="Ajouter ce coût"
            onCancel={() => setEditing(null)}
            onSubmit={async (input) => {
              if (await run(() => addComponent(item.id, input))) {
                setEditing(null);
              }
            }}
          />
        ) : (
          <div className="form-actions">
            <button type="button" onClick={() => setEditing("new")} disabled={busy}>
              Ajouter un coût
            </button>
            {aiAvailable && (
              <button
                type="button"
                className="secondary"
                disabled={busy}
                onClick={async () => {
                  if (await run(() => suggestSupplies(item.id).then(() => undefined))) {
                    onSuggestions();
                  }
                }}
              >
                {busy ? "Recherche…" : "Proposer des fournitures (IA)"}
              </button>
            )}
          </div>
        ))}
      {error && <p className="error">{error}</p>}
    </div>
  );
}

function origin(component: EstimateComponent): string {
  if (component.rateCode) {
    return `Taux ${component.rateCode}`;
  }
  if (component.supplierName || component.supplierRef) {
    return [component.supplierName, component.supplierRef, component.costDate && `prix du ${formatDate(component.costDate)}`]
      .filter(Boolean)
      .join(" · ");
  }
  return "Saisi";
}

/**
 * A cost entered by hand, or picked from a rate of GPE or an offer of a
 * supplier: then the backend copies its cost, unit and references.
 */
function ComponentForm({
  component,
  rates,
  submitLabel,
  onSubmit,
  onCancel,
}: {
  component?: EstimateComponent;
  rates: PricingRate[];
  submitLabel: string;
  onSubmit: (input: ComponentInput) => Promise<void>;
  onCancel: () => void;
}) {
  const [type, setType] = useState<ComponentType>(component?.type ?? "SUPPLY");
  const [label, setLabel] = useState(component?.label ?? "");
  const [rateCode, setRateCode] = useState<string>(component?.rateCode ?? "");
  const [offer, setOffer] = useState<SupplierOffer | null>(null);
  const [query, setQuery] = useState("");
  const [offers, setOffers] = useState<SupplierOffer[] | null>(null);
  const [searchError, setSearchError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const picked = Boolean(rateCode) || offer !== null;

  const search = async () => {
    setSearchError(null);
    try {
      setOffers(await searchOffers(query));
    } catch (err) {
      setSearchError(err instanceof Error ? err.message : "Recherche impossible.");
    }
  };

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const text = (name: string) => String(form.get(name) ?? "");
    const quantity = parseNumber(text("quantity"));
    const unitCost = picked ? null : parseNumber(text("unitCost"));
    const waste = parsePercent(text("waste"));
    const coefficient = parseNumber(text("coefficient"));
    if (quantity === null || [quantity, unitCost, waste, coefficient].some((value) => Number.isNaN(value))) {
      setError("Quantité, coût, perte ou coefficient illisible.");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await onSubmit({
        type: offer ? "SUPPLY" : type,
        label: label.trim(),
        quantity,
        // A cost picked again keeps its copied values; a new pick is copied by the backend.
        unit: picked ? (component?.unit ?? null) : textOrNull(text("unit")),
        unitCost: picked ? (component?.unitCost ?? null) : unitCost,
        wasteRate: waste,
        coefficient,
        supplierName: picked ? (component?.supplierName ?? null) : textOrNull(text("supplierName")),
        supplierRef: picked ? (component?.supplierRef ?? null) : textOrNull(text("supplierRef")),
        manufacturerRef: picked ? (component?.manufacturerRef ?? null) : textOrNull(text("manufacturerRef")),
        costDate: picked ? (component?.costDate ?? null) : textOrNull(text("costDate")),
        rateCode: offer ? null : textOrNull(rateCode),
        offerId: offer?.id ?? (rateCode ? null : (component?.offerId ?? null)),
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Enregistrement impossible.");
    } finally {
      setSaving(false);
    }
  };

  const activeRates = rates.filter((rate) => rate.active);

  return (
    <form className="estimate-form component-form" onSubmit={submit}>
      <div className="form-grid">
        <label>
          Taux GPE
          <select
            value={rateCode}
            onChange={(event) => {
              const rate = activeRates.find((candidate) => candidate.code === event.target.value);
              setRateCode(event.target.value);
              setOffer(null);
              if (rate) {
                setType(rate.type);
                if (!label) {
                  setLabel(rate.label);
                }
              }
            }}
          >
            <option value="">Aucun (coût saisi)</option>
            {activeRates.map((rate) => (
              <option key={rate.code} value={rate.code}>
                {rate.label} — {formatMoney(rate.cost)} / {rate.unit}
              </option>
            ))}
          </select>
        </label>
        <label>
          Type
          <select value={offer ? "SUPPLY" : type} disabled={picked} onChange={(event) => setType(event.target.value as ComponentType)}>
            {COMPONENT_TYPES.map((value) => (
              <option key={value} value={value}>
                {COMPONENT_TYPE_LABELS[value]}
              </option>
            ))}
          </select>
        </label>
        <label className="wide">
          Libellé
          <input required maxLength={300} value={label} onChange={(event) => setLabel(event.target.value)} />
        </label>
        <label>
          Quantité par unité du poste
          <input name="quantity" required inputMode="decimal" defaultValue={numberInput(component?.quantity ?? 1)} />
        </label>
        {!picked && (
          <>
            <label>
              Unité
              <input name="unit" maxLength={20} defaultValue={component?.unit ?? ""} placeholder="u, h, m…" />
            </label>
            <label>
              Coût unitaire HT
              <input name="unitCost" inputMode="decimal" defaultValue={numberInput(component?.unitCost)} placeholder="à saisir" />
            </label>
          </>
        )}
        <label>
          Perte (%)
          <input name="waste" inputMode="decimal" defaultValue={percentInput(component?.wasteRate || null)} placeholder="0" />
        </label>
        <label>
          Coefficient propre
          <input name="coefficient" inputMode="decimal" defaultValue={numberInput(component?.coefficient)} placeholder="celui du chiffrage" />
        </label>
        {!picked && (type === "SUPPLY" || type === "RENTAL") && (
          <>
            <label>
              Fournisseur
              <input name="supplierName" maxLength={100} defaultValue={component?.supplierName ?? ""} />
            </label>
            <label>
              Réf. fournisseur
              <input name="supplierRef" maxLength={100} defaultValue={component?.supplierRef ?? ""} />
            </label>
            <label>
              Réf. fabricant
              <input name="manufacturerRef" maxLength={100} defaultValue={component?.manufacturerRef ?? ""} />
            </label>
            <label>
              Date du prix
              <input name="costDate" type="date" defaultValue={component?.costDate ?? ""} />
            </label>
          </>
        )}
      </div>

      {!rateCode && (
        <fieldset>
          <legend>Prix d&apos;un fournisseur (catalogues importés)</legend>
          {offer ? (
            <p>
              <strong>{offer.supplierName}</strong> · {offer.supplierRef} · {offer.designation} — prix net{" "}
              {formatMoney(offer.netPrice)}
              {offer.packQuantity !== 1 && ` les ${formatNumber(offer.packQuantity)} ${offer.purchaseUnit ?? ""}`}{" "}
              <button type="button" className="secondary danger" onClick={() => setOffer(null)}>
                Retirer
              </button>
            </p>
          ) : (
            <div className="inline-form">
              <input
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder="Référence, EAN ou mots de la désignation"
                onKeyDown={(event) => {
                  if (event.key === "Enter") {
                    event.preventDefault();
                    search();
                  }
                }}
              />
              <button type="button" onClick={search} disabled={query.trim().length < 2}>
                Chercher
              </button>
              {searchError && <span className="error">{searchError}</span>}
            </div>
          )}
          {!offer && offers !== null && offers.length === 0 && (
            <p className="muted">Aucune offre trouvée : saisissez le coût à la main ou importez le tarif du fournisseur.</p>
          )}
          {!offer && offers && offers.length > 0 && (
            <ul className="plain-list offer-results">
              {offers.map((candidate) => (
                <li key={candidate.id}>
                  <button
                    type="button"
                    className="link-button"
                    onClick={() => {
                      setOffer(candidate);
                      setType("SUPPLY");
                      if (!label) {
                        setLabel(candidate.designation.slice(0, 300));
                      }
                    }}
                  >
                    {candidate.supplierName} · {candidate.supplierRef}
                    {candidate.manufacturerRef && ` (${candidate.manufacturerRef})`} — {candidate.designation} —{" "}
                    {formatMoney(candidate.netPrice)}
                    {candidate.packQuantity !== 1 && ` les ${formatNumber(candidate.packQuantity)}`}
                    {candidate.validFrom && ` · prix du ${formatDate(candidate.validFrom)}`}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </fieldset>
      )}
      {picked && (
        <p className="hint">Le coût, l&apos;unité et les références sont recopiés par le logiciel depuis {offer ? "l'offre" : "le taux"} choisi.</p>
      )}

      <div className="form-actions">
        <button type="submit" className="primary" disabled={saving}>
          {saving ? "Enregistrement…" : submitLabel}
        </button>
        <button type="button" className="secondary" onClick={onCancel} disabled={saving}>
          Annuler
        </button>
        {error && <span className="error">{error}</span>}
      </div>
    </form>
  );
}
