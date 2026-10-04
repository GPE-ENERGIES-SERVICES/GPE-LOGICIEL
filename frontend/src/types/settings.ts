import type { MarketType } from "./tender";

export type CollectRun = {
  id: number;
  source: string;
  trigger: "SCHEDULED" | "MANUAL";
  status: "RUNNING" | "SUCCESS" | "FAILED";
  sinceDate: string;
  startedAt: string;
  finishedAt: string | null;
  noticesReceived: number;
  noticesNew: number;
  errorMessage: string | null;
};

export type CollectJournal = {
  running: boolean;
  runs: CollectRun[];
};

export type FilterCriteria = {
  departments: string[];
  marketTypes: MarketType[];
  minDaysBeforeDeadline: number | null;
  cpvPrefixes: string[];
  keywords: string[];
  excludedKeywords: string[];
  minAmount: number | null;
  maxAmount: number | null;
  updatedAt?: string;
  updatedBy?: string | null;
};
