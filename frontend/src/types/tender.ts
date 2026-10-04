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

export type FollowUpStatus = "NEW" | "TO_STUDY" | "RESPONDING" | "SUBMITTED" | "DISMISSED";

export type TenderTab = "NEW" | "TO_PROCESS" | "FOLLOWED" | "ALL" | "DISMISSED" | "ARCHIVED" | "FILTERED_OUT";

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
  // Provided by the simulated API; the backend does not send them yet.
  reference?: string | null;
  procedureType?: ProcedureType | null;
  source?: string;
  lotCount?: number;
  /** Activity codes, see ACTIVITY_LABELS. */
  activities?: string[];
  /** Distance from the company's headquarters, null when the place is unknown. */
  distanceKm?: number | null;
  /** 0 to 100, null when not computed. */
  relevanceScore?: number | null;
  /** Followed by the logged-in user; the follow-up status is shared by the team. */
  followed?: boolean;
  documentCount?: number;
  postalCode?: string | null;
  /** Outcome of a past consultation, null while it is open or when unknown. */
  result?: TenderResult | null;
  /** Companies awarded, one per lot at most, for an awarded consultation. */
  winners?: string[];
  /** Total awarded, null when an amount is unknown. */
  awardedAmount?: number | null;
};

/** Outcome of a past consultation; awarded contracts point to it by tenderId, one per lot. */
export type TenderResult = {
  kind: "AWARDED" | "UNSUCCESSFUL" | "CANCELLED";
};

/** A selection criterion and its weight in percent, null when not given. */
export type AwardCriterion = {
  label: string;
  weight: number | null;
};

/** What to know before answering, from the notice and the consultation rules. */
export type TenderKeyInfo = {
  siteVisit: { required: boolean; date: string | null } | null;
  requiredDocuments: string[];
  specialConditions: string | null;
  qualifications: string[];
  certifications: string[];
};

export type TenderDetail = TenderSummary & {
  source: string;
  externalId: string;
  description: string | null;
  reference: string | null;
  procedureType: ProcedureType | null;
  cpvCodes: string[];
  // Provided by the simulated API; the backend does not send them yet.
  cpv?: { code: string; label: string }[];
  awardCriteria?: AwardCriterion[];
  keyInfo?: TenderKeyInfo | null;
  platformName?: string | null;
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

/** Breakdown of the open tenders (active, not excluded by the filter), for the dashboard. */
export type TenderStats = {
  byStatus: Record<FollowUpStatus, number>;
  /** By activity code. */
  byActivity: Record<string, number>;
  /** By department code. */
  byDepartment: Record<string, number>;
};
