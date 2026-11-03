"use client";

import { useState, type FormEvent } from "react";

import {
  DECISION_LABELS,
  effectiveValue,
  FIELD_STATE_LABELS,
  formatValue,
  isReliable,
  NOT_IN_DOCUMENTS,
} from "@/lib/analysis";
import { formatDateTime } from "@/lib/format";
import { decideField } from "@/services/analysisApi";
import type { ExtractedField, FieldDecision, GridField, TenderAnalysis } from "@/types/analysis";
import { KeyValues } from "@/components/analysis/PartTitle";
import { Icon } from "@/components/Icon";

type Props = {
  field: ExtractedField;
  grid: GridField | undefined;
  readable: boolean;
  onChanged: (analysis: TenderAnalysis) => void;
  /** Opens on the form that gives the value of a field not found. */
  filling?: boolean;
};

/**
 * One extracted value: only the value and what needs attention at first; its
 * source, the exact quote and what a user can do with it open on request.
 */
export function FieldItem({ field, grid, readable, onChanged, filling = false }: Props) {
  const [correcting, setCorrecting] = useState(filling);
  const [correction, setCorrection] = useState(field.correctedValue ?? field.value ?? "");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState(filling);
  const value = effectiveValue(field);

  const decide = async (decision: FieldDecision | null, correctedValue?: string) => {
    setSaving(true);
    setError(null);
    try {
      onChanged(await decideField(field.id, decision, correctedValue ?? null));
      setCorrecting(false);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Enregistrement impossible.");
    } finally {
      setSaving(false);
    }
  };

  const submitCorrection = (event: FormEvent) => {
    event.preventDefault();
    decide("CORRECTED", correction);
  };

  const notIndicated = field.state === "NOT_INDICATED" && field.decision === null;
  const reliable = isReliable(field);
  const stateLabel = field.decision ? DECISION_LABELS[field.decision] : FIELD_STATE_LABELS[field.state];

  return (
    <div className={field.decision === "REJECTED" ? "field rejected" : "field"}>
      <div className="field-value">
        <span className={field.decision === "REJECTED" ? "struck" : ""}>
          {notIndicated ? NOT_IN_DOCUMENTS : <KeyValues text={formatValue(grid?.kind, field.decision === "REJECTED" ? field.value : value)} />}
        </span>
        <button
          type="button"
          className={reliable && !field.decision ? "field-toggle quiet" : reliable ? "field-toggle" : "field-toggle warn"}
          aria-expanded={open}
          aria-label={`${stateLabel} : ${open ? "masquer" : "afficher"} la source et les actions`}
          title={open ? "Masquer la source et les actions" : "Source et actions"}
          onClick={() => setOpen(!open)}
        >
          {reliable && !field.decision ? <Icon name="check" size={14} /> : stateLabel}
          <span className="badge-chevron">
            <Icon name={open ? "chevronUp" : "chevronDown"} size={12} />
          </span>
        </button>
      </div>

      {open && (
        <div className="field-details">
          {field.documentId && field.pageNumber && (
            <p className="source">
              Source :{" "}
              {readable ? (
                <a href={`/api/documents/${field.documentId}/pdf#page=${field.pageNumber}`} target="_blank" rel="noopener noreferrer">
                  {field.documentName}, page {field.pageNumber}
                </a>
              ) : (
                <>
                  {field.documentName}, page {field.pageNumber}
                </>
              )}
            </p>
          )}
          {field.quote && <blockquote className="quote">« {field.quote} »</blockquote>}

          {field.decision && (
            <p className="hint">
              {DECISION_LABELS[field.decision]} par {field.decidedBy} le {formatDateTime(field.decidedAt)}
              {field.decision === "CORRECTED" && <> — valeur proposée par l&apos;analyse : {formatValue(grid?.kind, field.value)}</>}
            </p>
          )}

          {correcting ? (
            <form className="inline-form" onSubmit={submitCorrection}>
              <input
                value={correction}
                onChange={(e) => setCorrection(e.target.value)}
                maxLength={4000}
                aria-label="Valeur corrigée"
                placeholder={grid?.kind === "DATE_TIME" ? "AAAA-MM-JJTHH:MM" : undefined}
              />
              <button type="submit" className="primary" disabled={saving}>
                Enregistrer
              </button>
              <button type="button" className="secondary" onClick={() => setCorrecting(false)}>
                Annuler
              </button>
            </form>
          ) : (
            <div className="field-actions">
              {field.decision !== "VALIDATED" && !notIndicated && (
                <button type="button" className="link-button" onClick={() => decide("VALIDATED")} disabled={saving}>
                  Valider
                </button>
              )}
              <button type="button" className="link-button" onClick={() => setCorrecting(true)} disabled={saving}>
                {notIndicated ? "Renseigner" : "Corriger"}
              </button>
              {field.decision !== "REJECTED" && !notIndicated && (
                <button type="button" className="link-button danger" onClick={() => decide("REJECTED")} disabled={saving}>
                  Rejeter
                </button>
              )}
              {field.decision && (
                <button type="button" className="link-button" onClick={() => decide(null)} disabled={saving}>
                  Annuler la décision
                </button>
              )}
            </div>
          )}
          {error && <p className="error">{error}</p>}
        </div>
      )}
    </div>
  );
}
