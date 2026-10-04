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
};

/**
 * Conditions of participation. Later filled from the validated analysis of
 * the consultation file.
 */
export type TenderConditions = {
  qualifications: string[];
  siteVisit: string | null;
  selectionCriteria: string | null;
  constraints: string | null;
};

export type TenderDetail = TenderSummary & {
  source: string;
  externalId: string;
  description: string | null;
  reference: string | null;
  procedureType: ProcedureType | null;
  conditions?: TenderConditions | null;
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

/** Breakdown of the open tenders (active, not excluded by the filter), for the dashboard. */
export type TenderStats = {
  byStatus: Record<FollowUpStatus, number>;
  /** By activity code. */
  byActivity: Record<string, number>;
  /** By department code. */
  byDepartment: Record<string, number>;
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

export type PieceType = "RC" | "CCAP" | "CCTP" | "AE" | "DPGF" | "BPU" | "DQE" | "PLAN" | "NOTICE" | "OTHER";

export type DocumentState = "PENDING" | "READ" | "SCANNED" | "FAILED" | "NOT_ANALYZED";

export type DossierStatus = "PENDING" | "PROCESSING" | "DONE" | "FAILED";

export type DocumentInfo = {
  id: number;
  fileName: string;
  sizeBytes: number;
  pieceType: PieceType;
  pieceTypeCorrected: boolean;
  state: DocumentState;
  stateDetail: string | null;
  pageCount: number | null;
  readable: boolean;
};

export type Dossier = {
  id: number;
  version: number;
  status: DossierStatus;
  errorMessage: string | null;
  uploadedBy: string | null;
  uploadedAt: string;
  processedAt: string | null;
  documents: DocumentInfo[];
};

export type FieldKind = "TEXT" | "DATE" | "DATE_TIME" | "AMOUNT" | "CHOICE";

export type SummarySection = "AT_A_GLANCE" | "ATTENTION" | "REQUESTED" | "TO_ANSWER";

export type FieldState = "VERIFIED" | "UNVERIFIED" | "NOT_INDICATED" | "AMBIGUOUS" | "CONTRADICTORY";

export type FieldDecision = "VALIDATED" | "CORRECTED" | "REJECTED";

export type AnalysisStatus = "PENDING" | "PROCESSING" | "DONE" | "FAILED";

export type GridField = {
  key: string;
  label: string;
  section: SummarySection;
  kind: FieldKind;
  multiple: boolean;
  question: string;
  choices: string[];
};

export type ExtractedField = {
  id: number;
  key: string;
  value: string | null;
  quote: string | null;
  documentId: number | null;
  documentName: string | null;
  pageNumber: number | null;
  state: FieldState;
  decision: FieldDecision | null;
  correctedValue: string | null;
  decidedBy: string | null;
  decidedAt: string | null;
};

export type AnalysisDocument = {
  id: number;
  fileName: string;
  pieceType: PieceType;
  state: DocumentState;
  stateDetail: string | null;
  analysed: boolean;
  readable: boolean;
};

export type Analysis = {
  id: number;
  dossierId: number;
  dossierVersion: number;
  status: AnalysisStatus;
  model: string;
  promptVersion: string;
  createdAt: string;
  finishedAt: string | null;
  errorMessage: string | null;
  inputTokens: number | null;
  outputTokens: number | null;
  cost: number | null;
  noticeDeadline: string | null;
  validatedBy: string | null;
  validatedAt: string | null;
  fields: ExtractedField[];
  documents: AnalysisDocument[];
};

export type AnalysisJournalEntry = {
  id: number;
  tenderId: number;
  tenderTitle: string | null;
  dossierVersion: number;
  status: AnalysisStatus;
  model: string;
  promptVersion: string;
  createdAt: string;
  finishedAt: string | null;
  errorMessage: string | null;
  inputTokens: number | null;
  outputTokens: number | null;
  cost: number | null;
};

export type AnalysisQuality = {
  done: number;
  failed: number;
  totalCost: number | null;
  averageCost: number | null;
  fields: {
    key: string;
    label: string;
    answers: number;
    verified: number;
    notIndicated: number;
    validated: number;
    corrected: number;
    rejected: number;
  }[];
};

export type TenderAnalysis = {
  configured: boolean;
  grid: GridField[];
  latest: Analysis | null;
  history: Analysis[];
};

export type User = {
  id: number;
  email: string;
  displayName: string;
  role: Role;
  enabled: boolean;
  createdAt: string;
};
