import type { Page } from "@/types/common";
import type { AiCallPage, AiUsageOverview, TenderCost } from "@/types/aiUsage";

import { api } from "./http";

/** @param since start of the period of the services (ISO); all the calls without it */
export function getAiUsage(since?: string): Promise<AiUsageOverview> {
  return api(`/api/ai-usage${since ? `?since=${encodeURIComponent(since)}` : ""}`);
}

export function getAiUsageByTender(page: number, size: number): Promise<Page<TenderCost>> {
  return api(`/api/ai-usage/tenders?page=${page}&size=${size}`);
}

/** @param params kind, status, tenderId, from, to, sort, page, size */
export function getAiCalls(params: URLSearchParams): Promise<AiCallPage> {
  return api(`/api/ai-usage/calls?${params}`);
}
