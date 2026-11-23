import type { Page } from "./common";

/** One service of the AI over a period; costs in euros, null where no price was set. */
export type ServiceUse = {
  kind: string;
  calls: number;
  failed: number;
  cost: number | null;
  averageCost: number | null;
  /** Part of the cost of all the services, from 0 to 1. */
  share: number | null;
};

export type TenderGroup = { tenders: number; cost: number | null; average: number | null };

export type AiHealthState = "GREEN" | "ORANGE" | "RED";

export type AiUsageOverview = {
  month: { spent: number; budget: number | null; pricesSet: boolean };
  since: string | null;
  services: ServiceUse[];
  calls: number;
  cost: number | null;
  perTender: {
    finished: TenderGroup;
    ongoing: TenderGroup;
    unattributedCalls: number;
    unattributedCost: number | null;
  };
  health: {
    state: AiHealthState;
    running: number;
    waiting: number;
    failedDay: number;
    failedWeek: number;
    doneWeek: number;
  };
};

export type TenderCost = {
  tenderId: number;
  title: string;
  finished: boolean;
  calls: number;
  cost: number | null;
  services: ServiceUse[];
};

export type AiCallStatus = "RUNNING" | "DONE" | "FAILED" | "INTERRUPTED";

export type AiCall = {
  id: number;
  kind: string;
  status: AiCallStatus;
  errorKind: string | null;
  model: string | null;
  tenderId: number | null;
  tenderTitle: string | null;
  analysisId: number | null;
  createdAt: string;
  durationMs: number | null;
  inputTokens: number;
  outputTokens: number;
  cost: number | null;
  costConfirmed: boolean;
  createdBy: string | null;
};

export type AiCallPage = Page<AiCall>;
