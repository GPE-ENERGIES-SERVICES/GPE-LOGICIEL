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
  /** Already stored: duplicates skipped. */
  noticesKnown: number;
  /** Without identifier: not stored. */
  noticesRejected: number;
  tendersCreated: number;
  tendersUpdated: number;
  /** Notices that make no tender (not a call for tenders, initial notice not collected...). */
  noticesIgnored: number;
  /** Notices that could not be read, tried again at the next run. */
  noticeErrors: number;
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

/** AUTOMATED and AUTHENTICATED sources are collected; the others are never requested. */
export type SourceAccess = "AUTOMATED" | "AUTHENTICATED" | "MANUAL" | "NOT_AUTOMATABLE";

export type CollectSource = {
  source: string;
  access: SourceAccess;
  /** Collected by the schedule. */
  active: boolean;
  running: boolean;
  lastRun: CollectRun | null;
  lastSuccess: CollectRun | null;
};
