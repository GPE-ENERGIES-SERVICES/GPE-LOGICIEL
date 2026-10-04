import type { AwardedContract } from "@/types/award";

import { api } from "./http";

/** Contracts awarded for a consultation, one per lot. */
export function getTenderAwards(tenderId: number): Promise<AwardedContract[]> {
  return api(`/api/tenders/${tenderId}/awards`);
}
