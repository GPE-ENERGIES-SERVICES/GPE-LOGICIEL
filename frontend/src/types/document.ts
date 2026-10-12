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
  /** UPLOAD: uploaded by a user; RETRIEVED: retrieved from a link. */
  origin: "UPLOAD" | "RETRIEVED";
  dceLinkId: number | null;
  status: DossierStatus;
  errorMessage: string | null;
  uploadedBy: string | null;
  uploadedAt: string;
  processedAt: string | null;
  documents: DocumentInfo[];
};

/** A page of a document of the consultation file containing the searched words. */
export type DocumentHit = {
  documentId: number;
  fileName: string;
  pieceType: PieceType;
  pageNumber: number;
  /** Text of the page around the words found. */
  excerpt: string;
  /** False when the original file cannot be shown as PDF. */
  readable: boolean;
};

/** Where the retrieval of the consultation file from a link stands. */
export type DceLinkStatus = "AVAILABLE" | "TO_FETCH" | "FETCHED" | "FAILED" | "ACCESS_REQUIRED";

/** An address where the consultation file can be obtained, as a notice gives it. A link, not a file. */
export type DceLink = {
  id: number;
  /** Source of the notice that gave the address. */
  source: string;
  url: string;
  /** Host of the address, without "www.". */
  platform: string | null;
  status: DceLinkStatus;
  statusDetail: string | null;
  firstSeenAt: string;
  lastSeenAt: string;
  checkedAt: string | null;
};
