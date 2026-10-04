import type { AwardedContract, AwardSearch } from "@/types/award";

import { api } from "./http";

/**
 * Awarded contracts matching a search (q, winner, buyer, year, activity,
 * marketType, region, department, city, minAmount, maxAmount, sort, page,
 * size), as kept in the page URL.
 */
export function getAwardedContracts(params: URLSearchParams | string): Promise<AwardSearch> {
  return api(`/api/awards?${params}`);
}

/** Contracts awarded for a consultation, one per lot. */
export function getTenderAwards(tenderId: number): Promise<AwardedContract[]> {
  return api(`/api/tenders/${tenderId}/awards`);
}
