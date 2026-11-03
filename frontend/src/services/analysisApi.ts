import type {
  AnalysisConfiguration,
  AnalysisJournalEntry,
  AnalysisQuality,
  FieldDecision,
  FinancialAnalysis,
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

export function getFinancialAnalysis(tenderId: number): Promise<FinancialAnalysis> {
  return api(`/api/tenders/${tenderId}/financial-analysis`);
}

/** Starts the analysis of the price schedules of a dossier, on request only; force analyses them again. */
export function startFinancialAnalysis(dossierId: number, force: boolean): Promise<void> {
  return api(`/api/dossiers/${dossierId}/financial-analyses?force=${force}`, { method: "POST" });
}

/** Sends at once an analysis waiting to be sent. */
export function sendAnalysisNow(analysisId: number): Promise<TenderAnalysis> {
  return api(`/api/analyses/${analysisId}/send-now`, { method: "POST" });
}

/** Cancels an analysis not sent to the provider yet. */
export function cancelAnalysis(analysisId: number): Promise<TenderAnalysis> {
  return api(`/api/analyses/${analysisId}/cancel`, { method: "POST" });
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

/** Configuration of the AI analysis, queue and consumption of the month (administrator). */
export function getAnalysisConfiguration(): Promise<AnalysisConfiguration> {
  return api("/api/analyses/configuration");
}
