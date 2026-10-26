"use client";

import { useState } from "react";

import { setFollowed } from "@/services/tenderApi";

import { Icon } from "./Icon";

type Props = {
  tenderId: number;
  followed: boolean;
  onChanged: (followed: boolean) => void;
};

/**
 * Star to follow a tender. The follow is personal; the status stays shared by
 * the team. The star changes at once and comes back if the follow cannot be
 * saved.
 */
export function FollowButton({ tenderId, followed: initial, onChanged }: Props) {
  const [followed, setFollowedState] = useState(initial);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const toggle = async () => {
    setBusy(true);
    setError(null);
    setFollowedState(!followed);
    try {
      await setFollowed(tenderId, !followed);
      onChanged(!followed);
    } catch (err) {
      setFollowedState(followed);
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
        aria-label={followed ? "Ne plus suivre" : "Suivre"}
        disabled={busy}
        onClick={toggle}
      >
        <Icon name="star" size={18} filled={followed} />
      </button>
      {error && <span className="error small">{error}</span>}
    </>
  );
}
