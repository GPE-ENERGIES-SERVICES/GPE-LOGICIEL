"use client";

import { useState } from "react";

import { api } from "@/lib/api";

type Props = {
  tenderId: number;
  followed: boolean;
  onChanged: (followed: boolean) => void;
};

/** Star to follow a tender. The follow is personal; the status stays shared by the team. */
export function FollowButton({ tenderId, followed, onChanged }: Props) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const toggle = async () => {
    setBusy(true);
    setError(null);
    try {
      await api(`/api/tenders/${tenderId}/follow`, { method: followed ? "DELETE" : "PUT" });
      onChanged(!followed);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Action impossible.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <button
        type="button"
        className="star"
        aria-pressed={followed}
        title={followed ? "Ne plus suivre" : "Suivre"}
        disabled={busy}
        onClick={toggle}
      >
        {followed ? "★" : "☆"}
      </button>
      {error && <span className="error small">{error}</span>}
    </>
  );
}
