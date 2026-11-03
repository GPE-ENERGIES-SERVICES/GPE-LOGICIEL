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
