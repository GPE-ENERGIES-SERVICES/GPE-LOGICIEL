import type { DuplicateBatchPreview, DuplicateDecision, DuplicateGroup } from "@/types/duplicate";

import { api } from "./http";

/** Groups of tenders sharing a procedure identifier, to be decided by an administrator. */
export function getDuplicateGroups(): Promise<DuplicateGroup[]> {
  return api("/api/duplicates");
}

export function getDuplicateDecisions(): Promise<DuplicateDecision[]> {
  return api("/api/duplicates/decisions");
}

/** Merges a duplicate into the tender kept; reversible. */
export function mergeDuplicate(
  keptTenderId: number,
  mergedTenderId: number,
  acknowledgeTeamData: boolean,
): Promise<DuplicateDecision> {
  return api("/api/duplicates/merge", { method: "POST", body: { keptTenderId, mergedTenderId, acknowledgeTeamData } });
}

export function markNotDuplicates(firstTenderId: number, secondTenderId: number): Promise<DuplicateDecision> {
  return api("/api/duplicates/not-duplicates", { method: "POST", body: { firstTenderId, secondTenderId } });
}

export function undoDuplicateDecision(id: number): Promise<DuplicateDecision> {
  return api(`/api/duplicates/decisions/${id}/undo`, { method: "POST" });
}

/** The merges a batch would make; nothing is merged. */
export function previewDuplicateBatch(): Promise<DuplicateBatchPreview> {
  return api("/api/duplicates/batch");
}

export function confirmDuplicateBatch(token: string): Promise<DuplicateDecision[]> {
  return api("/api/duplicates/batch", { method: "POST", body: { token } });
}
