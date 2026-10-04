import type { Page } from "@/types/common";
import type { FollowUpStatus, TenderDetail, TenderSummary, TenderTab } from "@/types/tender";

import { api } from "./http";

/**
 * Search parameters of the tender list (tab, text, filters, sort, page), as
 * kept in the page URL. See docs/contrat-api-interface.md.
 */
type SearchParams = URLSearchParams | string;

export function getTenders(params: SearchParams): Promise<Page<TenderSummary>> {
  return api(`/api/tenders?${params}`);
}

/** Number of tenders of each tab matching the same search and filters. */
export function getTenderCounts(params: SearchParams = ""): Promise<Record<TenderTab, number>> {
  return api(`/api/tenders/counts?${params}`);
}

/** Number of tenders of each status in a tab, with the same search and filters except the status. */
export function getStatusCounts(params: SearchParams): Promise<Record<FollowUpStatus, number>> {
  return api(`/api/tenders/status-counts?${params}`);
}

export function getTender(id: number | string): Promise<TenderDetail> {
  return api(`/api/tenders/${id}`);
}

/** Status shared by the team, with a comment; a missing comment keeps the previous one. */
export function updateFollowUp(id: number, status: FollowUpStatus, comment?: string): Promise<TenderDetail> {
  return api(`/api/tenders/${id}/follow-up`, { method: "PUT", body: { status, comment } });
}

/** Personal follow of the logged-in user. */
export function setFollowed(id: number, followed: boolean): Promise<void> {
  return api(`/api/tenders/${id}/follow`, { method: followed ? "PUT" : "DELETE" });
}
