import type { DocumentState, PieceType } from "./document";

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
