import { useLoader } from "@/lib/useLoader";
import { getFinancialAnalysis, getTenderAnalysis } from "@/services/analysisApi";
import { getDossiers } from "@/services/documentApi";
import { getEstimates } from "@/services/estimateApi";
import { getMemoirs } from "@/services/memoirApi";
import { getResponse } from "@/services/responseApi";
import type { Analysis } from "@/types/analysis";

export type JourneyStep = "done" | "current" | "todo";

export type Journey = {
  dce: JourneyStep;
  summary: JourneyStep;
  financial: JourneyStep;
  estimate: JourneyStep;
  memoir: JourneyStep;
  response: JourneyStep;
};

function analysisStep(analysis: Analysis | null | undefined): JourneyStep {
  if (!analysis || analysis.status === "FAILED" || analysis.status === "CANCELLED") {
    return "todo";
  }
  return analysis.status === "DONE" ? "done" : "current";
}

/**
 * Where each step of the way of a tender stands, read from what each of them
 * holds. Nothing is decided here: a step not done never blocks the next one.
 */
export function useJourney(tenderId: number): Journey {
  const key = String(tenderId);
  // shortcut: read when the page opens, not after each change in a tab; a reload updates it.
  const dossiers = useLoader(() => getDossiers(tenderId), key);
  const summary = useLoader(() => getTenderAnalysis(tenderId), key);
  const financial = useLoader(() => getFinancialAnalysis(tenderId), key);
  const estimates = useLoader(() => getEstimates(tenderId), key);
  const memoirs = useLoader(() => getMemoirs(), key);
  const response = useLoader(() => getResponse(tenderId), key);

  const dossier = dossiers.data?.[0];
  const mine = (memoirs.data ?? []).filter((memoir) => memoir.tenderId === tenderId);
  const needed = (response.data?.pieces ?? []).filter((piece) => piece.state !== "NOT_NEEDED");

  return {
    dce: !dossier ? "todo" : dossier.status === "DONE" ? "done" : "current",
    summary: analysisStep(summary.data?.latest),
    financial: analysisStep(financial.data?.latest),
    estimate: estimates.data?.some((estimate) => estimate.status === "VALIDATED")
      ? "done"
      : estimates.data?.length
        ? "current"
        : "todo",
    memoir: mine.some((memoir) => memoir.status === "DONE") ? "done" : mine.length ? "current" : "todo",
    response:
      needed.length === 0 ? "todo" : needed.every((piece) => piece.state === "READY") ? "done" : "current",
  };
}
