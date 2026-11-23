"use client";

import { useEffect, useState } from "react";

import { refreshData, useLoader } from "@/lib/useLoader";
import { getCollectJournal, startCollect } from "@/services/settingsApi";

/** Starts a collection now, and follows it until it is over; the banner of the last collection then says how it went. */
export function CollectButton() {
  const { data } = useLoader(() => getCollectJournal(1), "collect-running");
  const [starting, setStarting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const running = starting || Boolean(data?.running);

  useEffect(() => {
    if (!running) {
      return;
    }
    const timer = window.setInterval(async () => {
      const journal = await getCollectJournal(1).catch(() => null);
      if (journal && !journal.running) {
        setStarting(false);
        refreshData("last-run", "collect-running");
      }
    }, 3000);
    return () => window.clearInterval(timer);
  }, [running]);

  const start = async () => {
    setStarting(true);
    setError(null);
    try {
      await startCollect();
      refreshData("last-run");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Impossible de lancer la collecte.");
      setStarting(false);
    }
  };

  return (
    <div className="collect-button">
      <button type="button" className="primary" onClick={start} disabled={running}>
        {running ? "Collecte en cours…" : "Lancer une collecte"}
      </button>
      {error && <span className="error">{error}</span>}
    </div>
  );
}
