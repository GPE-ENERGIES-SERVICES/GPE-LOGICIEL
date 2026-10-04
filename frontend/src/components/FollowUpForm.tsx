"use client";

import { useState, type FormEvent } from "react";

import { api } from "@/lib/api";
import { FOLLOW_UP_LABELS, formatDateTime } from "@/lib/format";
import type { FollowUpStatus, TenderDetail } from "@/lib/types";

type Props = {
  tender: TenderDetail;
  onSaved: (tender: TenderDetail) => void;
};

/**
 * Decision of the company on the tender, with a comment.
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
      onSaved(
        await api<TenderDetail>(`/api/tenders/${tender.id}/follow-up`, {
          method: "PUT",
          body: { status, comment },
        }),
      );
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
          {(Object.keys(FOLLOW_UP_LABELS) as FollowUpStatus[]).map((value) => (
            <option key={value} value={value}>
              {FOLLOW_UP_LABELS[value]}
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
