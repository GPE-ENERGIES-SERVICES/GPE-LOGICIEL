"use client";

import { useState, type FormEvent } from "react";

import { followUpChoices, FOLLOW_UP_LABELS, formatDateTime } from "@/lib/format";
import { updateFollowUp } from "@/services/tenderApi";
import type { FollowUpStatus, TenderDetail } from "@/types/tender";

type Props = {
  tender: TenderDetail;
  onSaved: (tender: TenderDetail) => void;
};

/**
 * Decision of the company on the tender, with a comment; only the statuses the rules allow from the current one.
 */
export function FollowUpForm({ tender, onSaved }: Props) {
  const [status, setStatus] = useState<FollowUpStatus>(tender.followUpStatus);
  const [comment, setComment] = useState(tender.followUpComment ?? "");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setSaving(true);
    setError(null);
    try {
      onSaved(await updateFollowUp(tender.id, status, comment));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Enregistrement impossible.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <form className="follow-up" onSubmit={submit}>
      <label>
        Statut
        <select value={status} onChange={(e) => setStatus(e.target.value as FollowUpStatus)}>
          {followUpChoices(tender.followUpStatus).map(({ status: value, blocked }) => (
            <option key={value} value={value} disabled={blocked !== null}>
              {FOLLOW_UP_LABELS[value]}
              {blocked && ` (${blocked})`}
            </option>
          ))}
        </select>
      </label>
      <label>
        Commentaire
        <textarea rows={3} maxLength={2000} value={comment} onChange={(e) => setComment(e.target.value)} />
      </label>
      {error && <p className="error">{error}</p>}
      <div className="form-actions">
        <button type="submit" className="primary" disabled={saving}>
          {saving ? "Enregistrement…" : "Enregistrer"}
        </button>
        {tender.followUpBy && (
          <span className="muted">
            Modifié par {tender.followUpBy} le {formatDateTime(tender.followUpAt)}
          </span>
        )}
      </div>
    </form>
  );
}
