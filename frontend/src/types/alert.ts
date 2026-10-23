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

/** What the user sets on a saved search. */
export type AlertInput = Omit<Alert, "id" | "createdAt" | "lastViewedAt">;

/** A saved search with its results among the open consultations. */
export type AlertSummary = Alert & {
  /** Open consultations matching the criteria. */
  matchCount: number;
  /** Those published since the user last opened the results. */
  newCount: number;
  /** Open consultations for which the saved search cannot decide (missing information, types of works not evaluated). */
  indeterminateCount?: number;
  /** The results are being evaluated again after a change: the counts may still change. */
  evaluating?: boolean;
};
