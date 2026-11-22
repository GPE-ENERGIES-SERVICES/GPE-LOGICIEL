"use client";

import { useState } from "react";

import { formatDateTime } from "@/lib/format";
import { useLoader } from "@/lib/useLoader";
import { changeFormField, generateForm, getForm, reviewForm, startForm } from "@/services/responseApi";
import type { FormDecision, FormField, FormFieldSource, FormStatus, ResponseForm, ResponseView } from "@/types/response";

export const FORM_STATUS_LABELS: Record<FormStatus, string> = {
  TODO: "À faire",
  IN_PROGRESS: "En cours",
  TO_VALIDATE: "À valider",
  VALIDATED: "Validé",
  REJECTED: "Refusé",
};

export const FORM_STATUS_CLASSES: Record<FormStatus, string> = {
  TODO: "badge muted-badge",
  IN_PROGRESS: "badge",
  TO_VALIDATE: "badge urgent",
  VALIDATED: "badge verified",
  REJECTED: "badge urgent",
};

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

function FieldRow({
  field,
  disabled,
  onSaved,
  onError,
}: {
  field: FormField;
  disabled: boolean;
  onSaved: (field: FormField) => void;
  onError: (message: string) => void;
}) {
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
            <input
              type="checkbox"
              checked={field.checked}
              disabled={disabled}
              onChange={(event) => save(field.value, event.target.checked)}
            />{" "}
            Cocher
          </label>
        ) : (
          <input
            key={field.value ?? ""}
            defaultValue={field.value ?? ""}
            disabled={disabled}
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
  onChange,
}: {
  pieceId: number;
  lots: { number: string; title: string | null }[];
  onGenerated: (action: () => Promise<ResponseView>) => void;
  /** After a filling or a decision: where the form stands has changed. */
  onChange?: () => void;
}) {
  const loaded = useLoader(() => getForm(pieceId), `form-${pieceId}`);
  const [saved, setSaved] = useState<ResponseForm | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lot, setLot] = useState<string | null>(null);
  const [showOthers, setShowOthers] = useState(false);
  const [rejecting, setRejecting] = useState(false);
  const [reason, setReason] = useState("");
  const form = saved ?? loaded.data;

  if (!form) {
    return loaded.error ? <p className="error">{loaded.error}</p> : <p className="muted">Chargement…</p>;
  }

  const chosenLot = lot ?? form.lotNumber ?? "";
  const act = async (action: () => Promise<ResponseForm>, failure: string) => {
    setBusy(true);
    setError(null);
    try {
      setSaved(await action());
      onChange?.();
    } catch (err) {
      setError(err instanceof Error ? err.message : failure);
    } finally {
      setBusy(false);
    }
  };
  const start = (useAi: boolean) =>
    act(() => startForm(pieceId, { lotNumber: chosenLot || null, useAi }), "Remplissage impossible.");
  const review = (decision: FormDecision, comment?: string) =>
    act(() => reviewForm(pieceId, { decision, comment }), "Décision non enregistrée.");
  const validated = form.status === "VALIDATED";
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
            {!validated && (
              <button type="button" className="primary" disabled={busy || !form.aiAvailable} onClick={() => start(true)}>
                {busy ? "Patientez…" : form.started ? "Relancer l'IA" : "Déléguer à l'IA"}
              </button>
            )}
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
          <p>
            <span className={FORM_STATUS_CLASSES[form.status]}>{FORM_STATUS_LABELS[form.status]}</span>{" "}
            <span className="muted">
              {form.filledByAi ? "Proposé par l'IA, relu par un humain." : "Rempli à la main."}
              {form.reviewedBy && form.reviewedAt && ` Dernière décision : ${form.reviewedBy}, le ${formatDateTime(form.reviewedAt)}.`}
            </span>
          </p>
          {form.status === "REJECTED" && form.reviewComment && (
            <p className="banner error">
              Refusé : « {form.reviewComment} ». Corrigez les champs à la main, ou relancez l&apos;IA : elle tiendra compte de cette
              remarque.
            </p>
          )}
          <div className="form-fields">
            {shown.map((field) => (
              <FieldRow key={field.id} field={field} disabled={validated} onSaved={replace} onError={setError} />
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
                <FieldRow key={field.id} field={field} disabled={validated} onSaved={replace} onError={setError} />
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
            {validated ? (
              <button type="button" disabled={busy} onClick={() => review("REOPEN")}>
                Rouvrir pour modifier
              </button>
            ) : (
              <>
                {(form.status === "IN_PROGRESS" || form.status === "TO_VALIDATE") && (
                  <button
                    type="button"
                    className="primary"
                    disabled={busy}
                    onClick={() => {
                      const left = missing + toCheck;
                      if (
                        left === 0 ||
                        window.confirm(`${missing} champ(s) manquant(s) et ${toCheck} à vérifier. Valider quand même ?`)
                      ) {
                        review("VALIDATE");
                      }
                    }}
                  >
                    Valider : joindre le Word au dossier
                  </button>
                )}
                {form.status === "TO_VALIDATE" && (
                  <button type="button" className="danger" disabled={busy} onClick={() => setRejecting(!rejecting)}>
                    Refuser
                  </button>
                )}
                <button type="button" disabled={busy} onClick={() => onGenerated(() => generateForm(pieceId))}>
                  Aperçu Word, sans valider
                </button>
              </>
            )}
          </div>
          {rejecting && form.status === "TO_VALIDATE" && (
            <form
              className="inline-form"
              onSubmit={(event) => {
                event.preventDefault();
                setRejecting(false);
                review("REJECT", reason.trim());
                setReason("");
              }}
            >
              <label>
                Pourquoi ?{" "}
                <input
                  value={reason}
                  required
                  maxLength={4000}
                  placeholder="ex. le lot est le 3, l'adresse est celle du siège"
                  onChange={(event) => setReason(event.target.value)}
                />
              </label>
              <button type="submit" className="danger" disabled={busy || !reason.trim()}>
                Confirmer le refus
              </button>
            </form>
          )}
        </>
      )}
    </div>
  );
}
