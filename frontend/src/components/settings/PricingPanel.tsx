"use client";

import { useState, type FormEvent } from "react";

import {
  COMPONENT_TYPE_LABELS,
  COMPONENT_TYPES,
  formatMoney,
  numberInput,
  parseNumber,
  parsePercent,
  percentInput,
} from "@/lib/estimate";
import { formatDateTime } from "@/lib/format";
import { useLoader } from "@/lib/useLoader";
import { createRate, deleteRate, getPricingSettings, getRates, updatePricingSettings, updateRate } from "@/services/estimateApi";
import type { ComponentType, PricingRate, RateInput } from "@/types/estimate";

/**
 * The settings of GPE for its estimates: VAT rate and coefficients given to a
 * new estimate, and the rates picked in an estimate (hour of a profile,
 * kilometre, day of rental). An estimate copies them: a change here changes
 * no estimate already made.
 */
export function PricingPanel() {
  const settings = useLoader(() => getPricingSettings(), "pricing-settings");
  const rates = useLoader(() => getRates(), "pricing-rates");
  const [editing, setEditing] = useState<number | "new" | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const saveSettings = async (event: FormEvent<HTMLFormElement>) => {
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
      await updatePricingSettings({ label: null, vatRate, coefficients });
      setMessage("Paramètres enregistrés : ils s'appliquent aux prochains chiffrages.");
      settings.reload();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Enregistrement impossible.");
    } finally {
      setSaving(false);
    }
  };

  const saveRate = async (rate: RateInput, id?: number) => {
    setError(null);
    try {
      await (id === undefined ? createRate(rate) : updateRate(id, rate));
      setEditing(null);
      rates.reload();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Enregistrement impossible.");
    }
  };

  return (
    <>
      {settings.data && (
        <form onSubmit={saveSettings} key={settings.data.updatedAt}>
          <h3>Valeurs par défaut d&apos;un nouveau chiffrage</h3>
          <div className="form-grid">
            <label>
              TVA (%)
              <input name="vat" required inputMode="decimal" defaultValue={percentInput(settings.data.vatRate)} />
            </label>
            {COMPONENT_TYPES.map((type) => (
              <label key={type}>
                Coefficient {COMPONENT_TYPE_LABELS[type].toLowerCase()}
                <input name={type} required inputMode="decimal" defaultValue={numberInput(settings.data?.coefficients[type])} />
              </label>
            ))}
          </div>
          <p className="hint">
            Prix de vente = coût × coefficient (frais généraux et marge compris).
            {settings.data.updatedBy && ` Modifié par ${settings.data.updatedBy} le ${formatDateTime(settings.data.updatedAt)}.`}
          </p>
          <div className="form-actions">
            <button type="submit" className="primary" disabled={saving}>
              {saving ? "Enregistrement…" : "Enregistrer"}
            </button>
            {message && <span className="success">{message}</span>}
          </div>
        </form>
      )}

      <h3>Taux (coûts horaires, déplacements, location…)</h3>
      {rates.data && rates.data.length === 0 && <p className="muted">Aucun taux.</p>}
      {rates.data && rates.data.length > 0 && (
        <table>
          <thead>
            <tr>
              <th>Code</th>
              <th>Libellé</th>
              <th>Type</th>
              <th className="number">Coût</th>
              <th>Actif</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {rates.data.map((rate) =>
              editing === rate.id ? (
                <tr key={rate.id}>
                  <td colSpan={6}>
                    <RateForm rate={rate} onCancel={() => setEditing(null)} onSubmit={(input) => saveRate(input, rate.id)} />
                  </td>
                </tr>
              ) : (
                <tr key={rate.id} className={rate.active ? undefined : "row-muted"}>
                  <td>{rate.code}</td>
                  <td>{rate.label}</td>
                  <td>{COMPONENT_TYPE_LABELS[rate.type]}</td>
                  <td className="number">
                    {formatMoney(rate.cost)} / {rate.unit}
                  </td>
                  <td>{rate.active ? "Oui" : "Non"}</td>
                  <td className="row-actions">
                    <button type="button" className="secondary" onClick={() => setEditing(rate.id)}>
                      Modifier
                    </button>
                    <button
                      type="button"
                      className="secondary danger"
                      onClick={async () => {
                        if (window.confirm(`Supprimer le taux ${rate.code} ? Les chiffrages gardent le coût copié.`)) {
                          try {
                            await deleteRate(rate.id);
                            rates.reload();
                          } catch (err) {
                            setError(err instanceof Error ? err.message : "Suppression impossible.");
                          }
                        }
                      }}
                    >
                      Supprimer
                    </button>
                  </td>
                </tr>
              ),
            )}
          </tbody>
        </table>
      )}
      {editing === "new" ? (
        <RateForm onCancel={() => setEditing(null)} onSubmit={(input) => saveRate(input)} />
      ) : (
        <div className="form-actions">
          <button type="button" onClick={() => setEditing("new")}>
            Ajouter un taux
          </button>
        </div>
      )}
      {(error || settings.error || rates.error) && <p className="banner error">{error ?? settings.error ?? rates.error}</p>}
    </>
  );
}

function RateForm({
  rate,
  onSubmit,
  onCancel,
}: {
  rate?: PricingRate;
  onSubmit: (input: RateInput) => Promise<void>;
  onCancel: () => void;
}) {
  const [error, setError] = useState<string | null>(null);

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const cost = parseNumber(String(form.get("cost") ?? ""));
    if (cost === null || Number.isNaN(cost) || cost < 0) {
      setError("Coût invalide.");
      return;
    }
    onSubmit({
      code: String(form.get("code") ?? "").trim(),
      label: String(form.get("label") ?? "").trim(),
      type: String(form.get("type")) as ComponentType,
      unit: String(form.get("unit") ?? "").trim(),
      cost,
      active: form.get("active") === "on",
    });
  };

  return (
    <form className="estimate-form" onSubmit={submit}>
      <div className="form-grid">
        <label>
          Code
          <input name="code" required maxLength={30} pattern="[A-Za-z0-9_\-]+" defaultValue={rate?.code ?? ""} placeholder="ELEC" />
        </label>
        <label>
          Libellé
          <input name="label" required maxLength={200} defaultValue={rate?.label ?? ""} placeholder="Heure d'électricien" />
        </label>
        <label>
          Type
          <select name="type" defaultValue={rate?.type ?? "LABOR"}>
            {COMPONENT_TYPES.map((type) => (
              <option key={type} value={type}>
                {COMPONENT_TYPE_LABELS[type]}
              </option>
            ))}
          </select>
        </label>
        <label>
          Unité
          <input name="unit" required maxLength={20} defaultValue={rate?.unit ?? "h"} />
        </label>
        <label>
          Coût HT
          <input name="cost" required inputMode="decimal" defaultValue={numberInput(rate?.cost)} />
        </label>
      </div>
      <label>
        <input type="checkbox" name="active" defaultChecked={rate?.active ?? true} /> Actif (proposé dans les chiffrages)
      </label>
      <div className="form-actions">
        <button type="submit" className="primary">
          Enregistrer
        </button>
        <button type="button" className="secondary" onClick={onCancel}>
          Annuler
        </button>
        {error && <span className="error">{error}</span>}
      </div>
    </form>
  );
}
