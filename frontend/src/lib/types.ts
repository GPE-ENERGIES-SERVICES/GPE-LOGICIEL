export type Role = "USER" | "ADMIN";

export type CurrentUser = {
  id: number;
  email: string;
  displayName: string;
  role: Role;
};

export type MarketType = "WORKS" | "SERVICES" | "SUPPLIES";

export type ProcedureType =
  | "OPEN"
  | "RESTRICTED"
  | "NEGOTIATED"
  | "COMPETITIVE_DIALOGUE"
  | "ADAPTED"
  | "CONCESSION"
  | "OTHER";

export type TenderStatus = "ACTIVE" | "CLOSED" | "CANCELLED" | "UNSUCCESSFUL";

export type FollowUpStatus = "NEW" | "TO_STUDY" | "RESPONDING" | "DISMISSED";

export type TenderTab = "NEW" | "TO_STUDY" | "RESPONDING" | "DISMISSED" | "FILTERED_OUT";

export type NoticeKind = "INITIAL" | "RECTIFICATION" | "AWARD" | "CANCELLATION";

export type TenderSummary = {
  id: number;
  title: string;
  buyer: string | null;
  location: string | null;
  departments: string[];
  marketTypes: MarketType[];
  estimatedAmount: number | null;
  publishedAt: string | null;
  deadline: string | null;
  status: TenderStatus;
  followUpStatus: FollowUpStatus;
  retained: boolean | null;
  filterReason: string | null;
};

export type TenderDetail = TenderSummary & {
  source: string;
  externalId: string;
  description: string | null;
  reference: string | null;
  procedureType: ProcedureType | null;
  cpvCodes: string[];
  noticeUrl: string | null;
  platformUrl: string | null;
  followUpComment: string | null;
  followUpBy: string | null;
  followUpAt: string | null;
  lots: { number: string; title: string | null; estimatedAmount: number | null }[];
  notices: {
    externalId: string;
    kind: NoticeKind;
    publishedAt: string | null;
    deadline: string | null;
    details: string | null;
  }[];
};

export type Page<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

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

export type User = {
  id: number;
  email: string;
  displayName: string;
  role: Role;
  enabled: boolean;
  createdAt: string;
};
