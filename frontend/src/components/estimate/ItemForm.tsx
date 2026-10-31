"use client";

import { useState, type FormEvent } from "react";

import { numberInput, parseNumber, PRICE_MODE_LABELS, textOrNull } from "@/lib/estimate";
import type { EstimateItem, ItemInput, PriceMode } from "@/types/estimate";

/** An item of the estimate, as in the price schedule. */
export function ItemForm({
  item,
  submitLabel,
  onSubmit,
  onCancel,
}: {
  item?: EstimateItem;
  submitLabel: string;
  onSubmit: (input: ItemInput) => Promise<void>;
  onCancel?: () => void;
}) {
  const [mode, setMode] = useState<PriceMode>(item?.priceMode ?? "COMPOSED");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const text = (name: string) => String(form.get(name) ?? "");
    const quantity = parseNumber(text("quantity"));
    const fixed = parseNumber(text("fixedUnitPrice"));
    if (Number.isNaN(quantity) || Number.isNaN(fixed)) {
      setError("Quantité ou prix illisible.");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await onSubmit({
        chapter: textOrNull(text("chapter")),
        reference: textOrNull(text("reference")),
        designation: text("designation").trim(),
        unit: textOrNull(text("unit")),
        quantity,
        priceMode: mode,
        fixedUnitPrice: mode === "FIXED" ? fixed : null,
        textPrice: mode === "TEXT" ? textOrNull(text("textPrice")) : null,
        option: form.get("option") === "on",
        toCheck: form.get("toCheck") === "on",
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Enregistrement impossible.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <form className="estimate-form" onSubmit={submit}>
      <div className="form-grid">
        <label>
          Chapitre
          <input name="chapter" maxLength={200} defaultValue={item?.chapter ?? ""} />
        </label>
        <label>
          N°
          <input name="reference" maxLength={50} defaultValue={item?.reference ?? ""} />
        </label>
        <label className="wide">
          Désignation
          <input name="designation" required maxLength={4000} defaultValue={item?.designation ?? ""} />
        </label>
        <label>
          Unité
          <input name="unit" maxLength={20} defaultValue={item?.unit ?? ""} placeholder="u, ml, m², ens…" />
        </label>
        <label>
          Quantité
          <input name="quantity" inputMode="decimal" defaultValue={numberInput(item?.quantity)} />
        </label>
        <label>
          Prix
          <select value={mode} onChange={(event) => setMode(event.target.value as PriceMode)}>
            {(Object.keys(PRICE_MODE_LABELS) as PriceMode[]).map((value) => (
              <option key={value} value={value}>
                {PRICE_MODE_LABELS[value]}
              </option>
            ))}
          </select>
        </label>
        {mode === "FIXED" && (
          <label>
            Prix unitaire HT imposé
            <input name="fixedUnitPrice" inputMode="decimal" defaultValue={numberInput(item?.fixedUnitPrice)} />
          </label>
        )}
        {mode === "TEXT" && (
          <label>
            Mention
            <input name="textPrice" required maxLength={30} defaultValue={item?.textPrice ?? "PM"} />
          </label>
        )}
      </div>
      <div className="checkboxes">
        <label>
          <input type="checkbox" name="option" defaultChecked={item?.option ?? false} /> Option (PSE), hors total
        </label>
        {item?.toCheck && (
          <label>
            <input type="checkbox" name="toCheck" defaultChecked /> À vérifier (ligne importée)
          </label>
        )}
      </div>
      <div className="form-actions">
        <button type="submit" className="primary" disabled={saving}>
          {saving ? "Enregistrement…" : submitLabel}
        </button>
        {onCancel && (
          <button type="button" className="secondary" onClick={onCancel} disabled={saving}>
            Annuler
          </button>
        )}
        {error && <span className="error">{error}</span>}
      </div>
    </form>
  );
}
