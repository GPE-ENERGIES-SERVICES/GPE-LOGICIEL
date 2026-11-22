"use client";

import { useEffect, useState } from "react";

import { getAiCallSummary } from "@/services/aiApi";

/** What a finished treatment took. */
export type AiResult = { seconds: number | null; cost: number | null };

/** A cost under a cent keeps its four decimals: 0,0011 €. */
export function formatAiCost(cost: number): string {
  return new Intl.NumberFormat("fr-FR", {
    style: "currency",
    currency: "EUR",
    minimumFractionDigits: cost < 0.01 ? 4 : 2,
    maximumFractionDigits: cost < 0.01 ? 4 : 2,
  }).format(cost);
}

function duration(seconds: number): string {
  return seconds < 60 ? `${seconds} s` : `${Math.floor(seconds / 60)} min ${String(seconds % 60).padStart(2, "0")} s`;
}

// The clock of the browser and the one of the server may differ by a few seconds.
const CLOCK_MARGIN_MS = 10_000;

/**
 * The progress of a treatment by the AI, shown beside the button that started
 * it, then what it took and cost. The progress is real when the steps are
 * counted (sections written); otherwise it is estimated from how long the last
 * calls of the same kind took, and said so, never full before the answer.
 * @param kind the kind of the calls (MEMOIR_SECTION...), to read their cost once done
 * @param since when it started (ISO); nothing shown before
 * @param result what it took, when the caller knows it (an analysis); read from the calls otherwise
 */
export function AiProgress({
  kind,
  since,
  running,
  steps,
  typicalSeconds,
  result,
  label = "L'IA travaille…",
}: {
  kind?: string;
  since: string | null;
  running: boolean;
  steps?: { done: number; total: number };
  typicalSeconds?: number | null;
  result?: AiResult | null;
  label?: string;
}) {
  const [now, setNow] = useState(() => Date.now());
  const [typical, setTypical] = useState<number | null>(typicalSeconds ?? null);
  const [read, setRead] = useState<AiResult | null>(null);

  useEffect(() => {
    if (!running) {
      return;
    }
    const timer = window.setInterval(() => setNow(Date.now()), 500);
    return () => window.clearInterval(timer);
  }, [running]);

  // The typical duration, once, when the caller does not give it.
  useEffect(() => {
    if (!running || typicalSeconds !== undefined || !kind) {
      return;
    }
    let cancelled = false;
    getAiCallSummary(kind)
      .then((summary) => !cancelled && setTypical(summary.typicalSeconds))
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [running, typicalSeconds, kind]);

  // What the calls cost, once over, when the caller does not know it.
  useEffect(() => {
    if (running || !since || result !== undefined || !kind) {
      return;
    }
    let cancelled = false;
    getAiCallSummary(kind, new Date(new Date(since).getTime() - CLOCK_MARGIN_MS).toISOString())
      .then((summary) => !cancelled && setRead(summary.calls > 0 ? { seconds: summary.seconds, cost: summary.cost } : null))
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [running, since, result, kind]);

  if (!since) {
    return null;
  }

  if (running) {
    const elapsed = Math.max(0, Math.round((now - new Date(since).getTime()) / 1000));
    const expected = typicalSeconds ?? typical;
    const value = steps && steps.total > 0 ? steps.done / steps.total : expected ? Math.min(0.95, elapsed / expected) : null;
    const detail =
      steps && steps.total > 0
        ? `${steps.done} / ${steps.total}`
        : expected
          ? elapsed < expected
            ? `≈ ${duration(expected - elapsed)} restantes (estimation)`
            : `${duration(elapsed)}, un peu plus long que d'habitude`
          : duration(elapsed);
    return (
      <div className="progress ai-progress" aria-live="polite">
        <div className="progress-text">
          <span>{label}</span>
          <span className="progress-percent">{detail}</span>
        </div>
        <div
          className={value === null ? "progress-track indeterminate" : "progress-track"}
          role="progressbar"
          aria-label={label}
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={value === null ? undefined : Math.round(value * 100)}
        >
          <div className="progress-fill" style={value === null ? undefined : { width: `${Math.round(value * 100)}%` }} />
        </div>
      </div>
    );
  }

  const done = result === undefined ? read : result;
  if (!done) {
    return null;
  }
  return (
    <p className="hint ai-done">
      IA : fait{done.seconds !== null ? ` en ${duration(done.seconds)}` : ""}
      {done.cost !== null ? ` · ${formatAiCost(done.cost)}` : ""}
    </p>
  );
}

/**
 * Follows a call to the AI the page waits for: wrap the call in track, and
 * show view beside its button.
 */
export function useAiRun(kind: string, label?: string) {
  const [since, setSince] = useState<string | null>(null);
  const [running, setRunning] = useState(false);
  const track = async <T,>(action: () => Promise<T>): Promise<T> => {
    setSince(new Date().toISOString());
    setRunning(true);
    try {
      return await action();
    } finally {
      setRunning(false);
    }
  };
  return { track, running, view: <AiProgress kind={kind} since={since} running={running} label={label} /> };
}
