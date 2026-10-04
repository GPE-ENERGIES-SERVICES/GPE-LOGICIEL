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
