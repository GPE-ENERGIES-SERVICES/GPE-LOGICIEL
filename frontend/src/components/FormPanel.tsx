"use client";

import { useState } from "react";

import { useLoader } from "@/lib/useLoader";
import { changeFormField, generateForm, getForm, startForm } from "@/services/responseApi";
import type { FormField, FormFieldSource, ResponseForm, ResponseView } from "@/types/response";

const SOURCE_LABELS: Record<FormFieldSource, string> = {
  COMPANY: "Entreprise",
  ESTIMATE: "Chiffrage",
  TENDER: "Appel d'offres",
  AI: "IA : à vérifier",
  MANUAL: "Modifié à la main",
  MISSING: "Manquant",
  NONE: "À remplir",
};

const SOURCE_CLASSES: Record<FormFieldSource, string> = {
  COMPANY: "badge verified",
  ESTIMATE: "badge verified",
  TENDER: "badge verified",
  AI: "badge urgent",
  MANUAL: "badge",
  MISSING: "badge urgent",
  NONE: "badge muted-badge",
};

/** The end of what surrounds a place: what is closest says what it asks. */
function shortLabel(label: string): string {
  return label.length <= 130 ? label : `…${label.slice(-130)}`;
}

function FieldRow({ field, onSaved, onError }: { field: FormField; onSaved: (field: FormField) => void; onError: (message: string) => void }) {
  const save = async (value: string | null, checked: boolean) => {
    try {
      onSaved(await changeFormField(field.id, { value, checked }));
    } catch (err) {
      onError(err instanceof Error ? err.message : "Enregistrement impossible.");
    }
  };

  return (
    <div className="form-field">
      <div className="form-field-label">{shortLabel(field.label)}</div>
      <div className="form-field-value">
        {field.kind === "CHECK" ? (
          <label>
            <input type="checkbox" checked={field.checked} onChange={(event) => save(field.value, event.target.checked)} /> Cocher
          </label>
        ) : (
          <input
            key={field.value ?? ""}
            defaultValue={field.value ?? ""}
            aria-label={shortLabel(field.label)}
            onBlur={(event) => {
              const value = event.target.value.trim();
              if (value !== (field.value ?? "")) {
                save(value || null, field.checked);
              }
            }}
          />
        )}
        <span className={SOURCE_CLASSES[field.source]}>{SOURCE_LABELS[field.source]}</span>
        {field.note && <span className="hint">{field.note}</span>}
      </div>
    </div>
  );
}

/**
 * A form of the consultation file filled for the answer: the AI proposes, each
 * value shows where it comes from, a user corrects it here; the Word document is
 * written on request. Declarations and signature stay on the document.
 */
export function FormPanel({
  pieceId,
  lots,
  onGenerated,
}: {
  pieceId: number;
  lots: { number: string; title: string | null }[];
  onGenerated: (action: () => Promise<ResponseView>) => void;
}) {
  const loaded = useLoader(() => getForm(pieceId), `form-${pieceId}`);
  const [saved, setSaved] = useState<ResponseForm | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lot, setLot] = useState<string | null>(null);
  const [showOthers, setShowOthers] = useState(false);
  const form = saved ?? loaded.data;

  if (!form) {
    return loaded.error ? <p className="error">{loaded.error}</p> : <p className="muted">Chargement…</p>;
  }

  const chosenLot = lot ?? form.lotNumber ?? "";
  const start = async (useAi: boolean) => {
    setBusy(true);
    setError(null);
    try {
      setSaved(await startForm(pieceId, { lotNumber: chosenLot || null, useAi }));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Remplissage impossible.");
    } finally {
      setBusy(false);
    }
  };
  const replace = (field: FormField) =>
    setSaved({ ...form, fields: form.fields.map((item) => (item.id === field.id ? field : item)) });

  const shown = form.fields.filter((field) => !field.skipped);
  const others = form.fields.filter((field) => field.skipped);
  const missing = shown.filter((field) => field.source === "MISSING").length;
  const toCheck = shown.filter((field) => field.source === "AI").length;

  return (
    <div className="form-panel" aria-busy={busy}>
      {form.problem ? (
        <p className="muted">{form.problem}</p>
      ) : (
        <>
          <p className="hint">Formulaire du DCE : {form.documentName}</p>
          <div className="inline-form">
            {lots.length > 0 ? (
              <label>
                Lot{" "}
                <select value={chosenLot} onChange={(event) => setLot(event.target.value)}>
                  <option value="">Tous lots</option>
                  {lots.map((item) => (
                    <option key={item.number} value={item.number}>
                      {item.number}
                      {item.title ? ` – ${item.title}` : ""}
                    </option>
                  ))}
                </select>
              </label>
            ) : (
              <label>
                Lot{" "}
                <input value={chosenLot} maxLength={50} placeholder="Facultatif" onChange={(event) => setLot(event.target.value)} />
              </label>
            )}
            <button type="button" className="primary" disabled={busy || !form.aiAvailable} onClick={() => start(true)}>
              {busy ? "Remplissage…" : form.started ? "Relancer l'IA" : "Remplir avec l'IA"}
            </button>
            {!form.started && (
              <button type="button" disabled={busy} onClick={() => start(false)}>
                Remplir à la main
              </button>
            )}
          </div>
          {!form.aiAvailable && form.aiMessage && <p className="hint">{form.aiMessage}</p>}
          <p className="hint">
            Un formulaire par lot : pour un autre lot, ajoutez une pièce (par exemple « Acte d&apos;engagement lot 2 »). Relancer l&apos;IA
            ne change jamais ce que vous avez modifié.
          </p>
        </>
      )}
      {error && <p className="banner error">{error}</p>}

      {form.started && (
        <>
          <p>
            <strong>
              {shown.length} champ{shown.length > 1 ? "s" : ""}
            </strong>
            {missing > 0 && <span className="badge urgent">{missing} manquant{missing > 1 ? "s" : ""}</span>}
            {toCheck > 0 && <span className="badge urgent">{toCheck} à vérifier</span>}
          </p>
          <div className="form-fields">
            {shown.map((field) => (
              <FieldRow key={field.id} field={field} onSaved={replace} onError={setError} />
            ))}
          </div>
          {others.length > 0 && (
            <p>
              <button type="button" className="link-button" aria-expanded={showOthers} onClick={() => setShowOthers(!showOthers)}>
                {showOthers ? "Masquer" : "Voir"} les {others.length} autres emplacements écartés par l&apos;IA
              </button>
            </p>
          )}
          {showOthers && (
            <div className="form-fields">
              {others.map((field) => (
                <FieldRow key={field.id} field={field} onSaved={replace} onError={setError} />
              ))}
            </div>
          )}
          {form.lockedCount > 0 && (
            <p className="hint">
              {form.lockedCount} emplacement{form.lockedCount > 1 ? "s" : ""} à compléter vous-même sur le document : déclarations sur
              l&apos;honneur, lieu, date et signature, parties réservées à l&apos;acheteur.
            </p>
          )}
          <div className="form-actions">
            <button type="button" className="primary" disabled={busy} onClick={() => onGenerated(() => generateForm(pieceId))}>
              Générer le Word et le joindre
            </button>
          </div>
        </>
      )}
    </div>
  );
}
