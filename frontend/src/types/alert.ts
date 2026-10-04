import type { MarketType } from "./tender";

/** A saved search of a user; its results are the open tenders matching all its criteria. */
export type Alert = {
  id: number;
  name: string;
  keywords: string[];
  region: string | null;
  departments: string[];
  marketTypes: MarketType[];
  /** Activity codes, see ACTIVITY_LABELS. */
  activities: string[];
  cpvPrefixes: string[];
  minAmount: number | null;
  maxAmount: number | null;
  active: boolean;
  createdAt: string;
  /** Last time the user opened the results: tenders published since are new. */
  lastViewedAt: string | null;
};
