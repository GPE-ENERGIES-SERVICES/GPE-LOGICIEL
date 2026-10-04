import type {
  AnalysisJournalEntry,
  AnalysisQuality,
  FieldDecision,
  TenderAnalysis,
} from "@/types/analysis";

import { api } from "./http";

export function getTenderAnalysis(tenderId: number): Promise<TenderAnalysis> {
  return api(`/api/tenders/${tenderId}/analysis`);
}

/** Starts the analysis of a dossier; force analyses again content already analysed. */
export function startAnalysis(dossierId: number, force: boolean): Promise<void> {
  return api(`/api/dossiers/${dossierId}/analyses?force=${force}`, { method: "POST" });
}

export function validateAnalysis(analysisId: number): Promise<TenderAnalysis> {
  return api(`/api/analyses/${analysisId}/validation`, { method: "POST" });
}

/** Decision of a user on an extracted value, null to cancel it; a correction carries the new value. */
export function decideField(
  fieldId: number,
  decision: FieldDecision | null,
  value: string | null,
): Promise<TenderAnalysis> {
  return api(`/api/extracted-fields/${fieldId}/decision`, { method: "PUT", body: { decision, value } });
}

export function getAnalysisJournal(limit: number): Promise<AnalysisJournalEntry[]> {
  return api(`/api/analyses?limit=${limit}`);
}

export function getAnalysisQuality(): Promise<AnalysisQuality> {
  return api("/api/analyses/quality");
}
