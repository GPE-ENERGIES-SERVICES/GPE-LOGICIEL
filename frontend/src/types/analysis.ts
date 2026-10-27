import type { DocumentState, PieceType } from "./document";

export type FieldKind = "TEXT" | "DATE" | "DATE_TIME" | "AMOUNT" | "CHOICE";

export type SummarySection = "AT_A_GLANCE" | "ATTENTION" | "REQUESTED" | "TO_ANSWER";

export type FieldState = "VERIFIED" | "UNVERIFIED" | "NOT_INDICATED" | "AMBIGUOUS" | "CONTRADICTORY";

export type FieldDecision = "VALIDATED" | "CORRECTED" | "REJECTED";

export type AnalysisStatus = "PENDING" | "PROCESSING" | "DONE" | "FAILED" | "RETRY_WAITING" | "CANCELLED";

/** Why an analysis failed or waits for a new attempt. */
export type FailureKind =
  | "NOT_CONFIGURED"
  | "AUTHENTICATION"
  | "RATE_LIMITED"
  | "QUOTA_EXCEEDED"
  | "BUDGET_REACHED"
  | "TIMEOUT"
  | "TEMPORARY"
  | "INVALID_RESPONSE"
  | "UNUSABLE_DOCUMENT"
  | "REFUSED"
  | "CONTEXT_TOO_LARGE"
  | "INTERRUPTED"
  | "UNEXPECTED";

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
  provider: string | null;
  model: string;
  promptVersion: string;
  errorKind: FailureKind | null;
  attempts: number;
  nextAttemptAt: string | null;
  requestedBy: string | null;
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
  provider: string | null;
  model: string;
  promptVersion: string;
  errorKind: FailureKind | null;
  attempts: number;
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
  /** Whether an analysis can be started now. */
  configured: boolean;
  /** Why not, when not configured. */
  unavailableReason: string | null;
  grid: GridField[];
  latest: Analysis | null;
  history: Analysis[];
};

export type AnalysisState =
  | "DISABLED"
  | "MISCONFIGURED"
  | "NOT_CONFIGURED"
  | "TRANSMISSION_NOT_ALLOWED"
  | "BUDGET_REACHED"
  | "READY";

/** Configuration of the AI analysis as the server applies it (administrator); never holds a key. */
export type AnalysisConfiguration = {
  state: AnalysisState;
  message: string;
  enabled: boolean;
  provider: string;
  model: string | null;
  externalTransmissionAllowed: boolean;
  availableProviders: string[];
  problems: string[];
  limits: {
    maxCharacters: number;
    maxDocumentsPerTender: number;
    maxOutputTokens: number;
    maxConcurrentRequests: number;
    maxAttempts: number;
    requestTimeoutSeconds: number;
    monthlyBudget: number | null;
    pricesSet: boolean;
  };
  queue: Record<AnalysisStatus, number>;
  /** Tokens counted by GPE; the cost is an estimate, not the invoice of the provider. */
  monthToDate: { inputTokens: number; outputTokens: number; estimatedCost: number | null };
};
