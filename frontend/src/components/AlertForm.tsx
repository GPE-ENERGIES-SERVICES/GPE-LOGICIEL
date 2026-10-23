"use client";

import { useState, type FormEvent } from "react";

import { ACTIVITY_LABELS, MARKET_TYPE_LABELS } from "@/lib/format";
import { REGIONS } from "@/lib/regions";
import { available } from "@/services/http";
import type { AlertInput } from "@/types/alert";
import type { MarketType } from "@/types/tender";

type Props = {
  title: string;
  initial: AlertInput;
  submitLabel: string;
  onSubmit: (alert: AlertInput) => Promise<void>;
  onCancel: () => void;
};

function toList(value: FormDataEntryValue | null): string[] {
  return String(value ?? "")
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
}

function toNumber(value: FormDataEntryValue | null): number | null {
  const text = String(value ?? "").trim();
  return text === "" ? null : Number(text);
}

/** Criteria of a saved search, to create or change it. */
export function AlertForm({ title, initial, submitLabel, onSubmit, onCancel }: Props) {
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const alert: AlertInput = {
      name: String(form.get("name") ?? "").trim(),
      keywords: toList(form.get("keywords")),
      region: String(form.get("region") ?? "") || null,
      departments: toList(form.get("departments")),
      marketTypes: form.getAll("marketTypes").map(String) as MarketType[],
      activities: form.getAll("activities").map(String),
      cpvPrefixes: toList(form.get("cpvPrefixes")),
      minAmount: toNumber(form.get("minAmount")),
      maxAmount: toNumber(form.get("maxAmount")),
      active: initial.active,
    };
    const lists = [alert.keywords, alert.departments, alert.marketTypes, alert.activities, alert.cpvPrefixes];
    if (!alert.name) {
      setError("Donnez un nom à la veille.");
      return;
    }
    if (!alert.region && alert.minAmount === null && alert.maxAmount === null && lists.every((list) => !list.length)) {
      setError("Indiquez au moins un critère.");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await onSubmit(alert);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Enregistrement impossible.");
      setSaving(false);
    }
  };

  return (
    <form className="card" onSubmit={submit}>
      <h2>{title}</h2>
      <div className="form-grid">
        <label>
          Nom
          <input name="name" required defaultValue={initial.name} placeholder="ex. GPE Énergie PACA" />
        </label>
        <label>
          Mots-clés
          <input name="keywords" defaultValue={initial.keywords.join(", ")} placeholder="ex. photovoltaïque, IRVE" />
          <span className="hint">Séparés par des virgules : un seul suffit. Les synonymes du métier comptent.</span>
        </label>
        <label>
          Région
          <select name="region" defaultValue={initial.region ?? ""}>
            <option value="">Toutes</option>
            {Object.keys(REGIONS).map((region) => (
              <option key={region}>{region}</option>
            ))}
          </select>
        </label>
        <label>
          Départements
          <input name="departments" defaultValue={initial.departments.join(", ")} placeholder="ex. 13, 83, 84" />
        </label>
        <label>
          Codes CPV
          <input name="cpvPrefixes" defaultValue={initial.cpvPrefixes.join(", ")} placeholder="ex. 0933, 4531" />
          <span className="hint">Début du code, séparés par des virgules.</span>
        </label>
        <label>
          Montant minimum (€ HT)
          <input type="number" name="minAmount" min={0} step={1000} defaultValue={initial.minAmount ?? ""} />
        </label>
        <label>
          Montant maximum (€ HT)
          <input type="number" name="maxAmount" min={0} step={1000} defaultValue={initial.maxAmount ?? ""} />
        </label>
      </div>
      <fieldset>
        <legend>Type de marché</legend>
        <div className="checkboxes">
          {(Object.entries(MARKET_TYPE_LABELS) as [MarketType, string][]).map(([type, label]) => (
            <label key={type} className="inline">
              <input type="checkbox" name="marketTypes" value={type} defaultChecked={initial.marketTypes.includes(type)} />
              {label}
            </label>
          ))}
        </div>
      </fieldset>
      <fieldset>
        <legend>Types de travaux</legend>
        {!available("activity") && (
          <p className="hint">
            Les types de travaux ne sont pas encore connus pour les consultations : une veille qui en utilise donne des
            résultats indéterminés, sans récupération de DCE.
          </p>
        )}
        <div className="checkboxes">
          {Object.entries(ACTIVITY_LABELS).map(([code, label]) => (
            <label key={code} className="inline">
              <input type="checkbox" name="activities" value={code} defaultChecked={initial.activities.includes(code)} />
              {label}
            </label>
          ))}
        </div>
      </fieldset>
      {error && <p className="error">{error}</p>}
      <div className="form-actions">
        <button type="submit" className="primary" disabled={saving}>
          {saving ? "Enregistrement…" : submitLabel}
        </button>
        <button type="button" className="secondary" onClick={onCancel} disabled={saving}>
          Annuler
        </button>
      </div>
    </form>
  );
}
