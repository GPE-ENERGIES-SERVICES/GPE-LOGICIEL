export type PieceCategory = "APPLICATION" | "OFFER";

export type PieceState = "MISSING" | "EXPIRED" | "TO_CHECK" | "READY" | "NOT_NEEDED";

export type ResponsePiece = {
  id: number;
  category: PieceCategory;
  label: string;
  /** Read in the consultation file: it stays listed, marked not needed if so. */
  fromAnalysis: boolean;
  state: PieceState;
  /** What to do next, or what is joined. */
  detail: string;
  companyDocumentId: number | null;
  /** The file uploaded by a user. */
  fileName: string | null;
  sizeBytes: number | null;
  /** The file that goes in the archive. */
  attachedName: string | null;
  checkedBy: string | null;
  checkedAt: string | null;
  /** A form of the consultation file the software can fill. */
  fillable: boolean;
};

export type FormFieldSource = "COMPANY" | "ESTIMATE" | "TENDER" | "AI" | "MANUAL" | "MISSING" | "NONE";

export type FormField = {
  id: number;
  kind: "TEXT" | "CHECK";
  label: string;
  value: string | null;
  checked: boolean;
  source: FormFieldSource;
  note: string | null;
  /** The AI found it asks nothing here: shown on request. */
  skipped: boolean;
};

export type ResponseForm = {
  pieceId: number;
  /** The form of the consultation file, or why there is none. */
  documentName: string | null;
  problem: string | null;
  aiAvailable: boolean;
  aiMessage: string | null;
  lotNumber: string | null;
  started: boolean;
  fields: FormField[];
  /** To complete on the document itself: declarations, signature. */
  lockedCount: number;
};

export type ResponseStep = { title: string; ready: boolean; detail: string; anchor: string };

export type DocumentChoice = {
  id: number;
  category: string;
  name: string;
  expiresOn: string | null;
  validity: "NONE" | "VALID" | "EXPIRING" | "EXPIRED";
  hasFile: boolean;
};

export type ResponseView = {
  steps: ResponseStep[];
  analysisDone: boolean;
  pieces: ResponsePiece[];
  documents: DocumentChoice[];
};

export type ResponseSummary = {
  tenderId: number;
  title: string;
  buyer: string | null;
  deadline: string | null;
  total: number;
  ready: number;
};
