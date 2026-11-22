import { api } from "./http";

/**
 * The calls of a kind a user made since a moment, and how long such a call
 * usually takes.
 * @property typicalSeconds the middle duration of the last calls of the kind, null before any
 * @property cost in euros, null when the prices of the AI are not set
 */
export type AiCallSummary = {
  typicalSeconds: number | null;
  calls: number;
  inputTokens: number;
  outputTokens: number;
  cost: number | null;
  seconds: number | null;
};

/** @param since the moment the user asked; without it, only the typical duration */
export function getAiCallSummary(kind: string, since?: string): Promise<AiCallSummary> {
  const query = new URLSearchParams({ kind });
  if (since) query.set("since", since);
  return api(`/api/ai-calls/summary?${query}`);
}
