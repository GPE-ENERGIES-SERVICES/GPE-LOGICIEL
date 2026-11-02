export type MemoirStatus = "DRAFT" | "OUTLINE_TO_VALIDATE" | "READY_TO_WRITE" | "WRITING" | "TO_REVIEW" | "DONE";

export type Memoir = {
  id: number;
  tenderId: number | null;
  tenderTitle: string | null;
  lotNumber: string | null;
  title: string;
  status: MemoirStatus;
  outlineValidatedBy: string | null;
  outlineValidatedAt: string | null;
  createdBy: string | null;
  createdAt: string;
  updatedBy: string | null;
  updatedAt: string;
  /** Sections of the outline, proposed ones excluded. */
  sections: number;
  /** Of them, those with a content. */
  written: number;
};

export type MemoirSection = {
  id: number;
  memoirId: number;
  parentId: number | null;
  position: number;
  /** 1 for a part, 2 for a sub-part… */
  level: number;
  /** « 2.1 »; empty for a part proposed by the AI. */
  number: string;
  title: string;
  description: string | null;
  /** Proposed by the AI, waiting to be accepted. */
  proposed: boolean;
  /** Cleaned HTML. */
  content: string | null;
  contentOrigin: "MANUAL" | "AI" | "COMPANY" | null;
  /** A content waiting for the user before it replaces the content. */
  proposal: string | null;
  proposalNote: string | null;
  requirementIds: number[];
  updatedBy: string | null;
  updatedAt: string;
};

export type RequirementCategory =
  | "CRITERION"
  | "SUBCRITERION"
  | "MANDATORY"
  | "FORM"
  | "MEANS"
  | "DEADLINE"
  | "METHOD"
  | "OTHER";

export type MemoirRequirement = {
  id: number;
  position: number;
  category: RequirementCategory;
  text: string;
  weight: string | null;
  documentId: number | null;
  page: number | null;
  /** Kept only when found on the page cited. */
  quote: string | null;
  verified: boolean;
  origin: "AI" | "USER";
};

export type MemoirJob = {
  id: number;
  kind: "ANALYSIS" | "WRITING";
  status: "RUNNING" | "DONE" | "FAILED" | "CANCELLED";
  currentSectionId: number | null;
  doneSteps: number;
  totalSteps: number;
  cancelRequested: boolean;
  message: string | null;
  startedBy: string | null;
  startedAt: string;
  finishedAt: string | null;
};

export type SourceDocument = { id: number; fileName: string; pieceType: string; pageCount: number | null };

export type MemoirWorkspace = {
  memoir: Memoir;
  sections: MemoirSection[];
  requirements: MemoirRequirement[];
  job: MemoirJob | null;
  documents: SourceDocument[];
  ai: { available: boolean; message: string };
};

export type MemoirRevision = { id: number; content: string; origin: string | null; replacedBy: string | null; createdAt: string };

export type AiAction = "WRITE" | "REWRITE" | "EXPAND" | "SHORTEN" | "IMPROVE" | "CORRECT" | "DETAIL" | "ADAPT";

export type CompanyBlock = "PRESENTATION" | "STAFF" | "EQUIPMENT" | "CERTIFICATIONS" | "REFERENCES";

export type FormerMemoir = {
  id: number;
  title: string;
  writtenOn: string | null;
  context: string | null;
  marketType: string | null;
  activityCode: string | null;
  useForAi: boolean;
  fileName: string;
  sizeBytes: number;
  /** Length of the text read in the file; 0 when none could be read. */
  textChars: number;
  uploadedBy: string | null;
  uploadedAt: string;
};
