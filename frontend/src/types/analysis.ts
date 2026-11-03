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
  /** Why the rules send it to the AI or not. */
  aiReason?: string | null;
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
  /** When it was last sent to the AI. */
  startedAt?: string | null;
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
  /** Decision help; null in the history and for the analyses made before it existed. */
  decision: Decision | null;
  fields: ExtractedField[];
  documents: AnalysisDocument[];
};

export type Verdict = "PRIORITY" | "EXAMINE" | "POOR_FIT" | "INSUFFICIENT";

/** CONFIRMED only when the quote was found in the document; TO_CHECK is plausible; MISSING prevents concluding. */
export type ObstacleKind = "CONFIRMED" | "TO_CHECK" | "MISSING";

export type DecisionPoint = {
  text: string;
  /** Null for an argument. */
  kind: ObstacleKind | null;
  /** Inferred from several elements rather than written as such. */
  deduced: boolean;
  /** Kept only when found in the document. */
  quote: string | null;
  documentId: number | null;
  page: number | null;
  verified: boolean;
};

export type DecisionCheck = {
  action: string;
  reason: string | null;
  documentId: number | null;
};

export type Decision = {
  verdict: Verdict;
  conclusion: string | null;
  strengths: DecisionPoint[];
  obstacles: DecisionPoint[];
  checks: DecisionCheck[];
  limits: string[];
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
  /** How long an analysis usually takes once sent, in seconds; null before any. */
  typicalSeconds?: number | null;
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

/** Checks of one sheet of a price schedule, made by the software without AI. */
export type PriceCheck = {
  documentId: number;
  fileName: string;
  sheet: string;
  columns: { role: "REFERENCE" | "DESIGNATION" | "UNIT" | "QUANTITY" | "UNIT_PRICE" | "AMOUNT"; ref: string; header: string }[];
  headerRow: number | null;
  items: number;
  withQuantity: number;
  withUnitPrice: number;
  withAmount: number;
  textPrices: number;
  computedTotal: number | null;
  amountBasis: "HT" | "TTC" | null;
  blankPrices: boolean;
  totals: {
    kind: "HT" | "VAT" | "TTC";
    label: string;
    ref: string;
    row: number;
    declared: number;
    computed: number | null;
    consistent: boolean | null;
  }[];
  issues: { kind: string; message: string; ref: string | null; row: number | null }[];
  moreIssues: number;
};

/** A point of the synthesis, with the place it comes from (row of a sheet, or page of a PDF). */
export type FinancialPoint = {
  text: string;
  documentId: number | null;
  page: number | null;
  row: number | null;
  sheet: string | null;
  /** Kept only when found at that place. */
  quote: string | null;
  verified: boolean;
};

export type FinancialResult = {
  overview: string | null;
  items: FinancialPoint[];
  points: FinancialPoint[];
  limits: string[];
  checks: PriceCheck[];
  notSent: string[];
};

export type FinancialAnalysis = {
  configured: boolean;
  unavailableReason: string | null;
  /** DPGF, BPU and DQE of the latest dossier; analysed tells whether the latest analysis used them. */
  pieces: AnalysisDocument[];
  latest: Analysis | null;
  result: FinancialResult | null;
  previous: number;
  typicalSeconds?: number | null;
};
