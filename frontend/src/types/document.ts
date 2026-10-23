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

/** Where the retrieval of the consultation file from a link stands (see DceLinkStatus of the backend). */
export type DceLinkStatus =
  | "AVAILABLE"
  | "NOT_ELIGIBLE"
  | "ELIGIBLE_PENDING"
  | "IN_PROGRESS"
  | "FETCHED"
  | "TEMPORARY_ERROR"
  | "PERMANENT_ERROR"
  | "LINK_UNAVAILABLE"
  | "ACCESS_REQUIRED"
  | "AUTOMATED_ACCESS_NOT_AUTHORIZED"
  | "STORAGE_LIMIT_REACHED";

/** Whether the consultation file of a tender may be retrieved: it matches an active saved search. */
export type EligibilityState = "ELIGIBLE" | "NOT_ELIGIBLE" | "INDETERMINATE";

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
  attempts: number;
  nextAttemptAt: string | null;
  fetchedAt: string | null;
  /** Files kept by the last retrieval. */
  fileCount: number | null;
  /** Null before the first evaluation. */
  eligibility: EligibilityState | null;
  /** Why, without the names of the saved searches of other users. */
  eligibilityReason: string;
  /** The saved searches of the logged-in user that match the tender. */
  yourAlerts: string[];
  /** Whether the automated retrieval is allowed for the platform. */
  platformAllowed: boolean;
  /** A person has to obtain the file by hand, or to decide. */
  actionRequired: boolean;
};

/** A change of status of a link. */
export type DceEvent = {
  id: number;
  dceLinkId: number;
  tenderId: number | null;
  platform: string | null;
  occurredAt: string;
  fromStatus: DceLinkStatus | null;
  toStatus: DceLinkStatus;
  detail: string | null;
};

/** The retrieval of the consultation files for the whole application (administrators). */
export type DceSummary = {
  eligibility: Partial<Record<EligibilityState, number>>;
  links: Partial<Record<DceLinkStatus, number>>;
  /** Tenders whose eligibility is missing or out of date. */
  outOfDate: number;
  evaluating: boolean;
  usedBytes: number;
  reservedBytes: number;
  /** In bytes (3 GB = 3,000,000,000). */
  quotaBytes: number;
  allowedPlatforms: string[];
  workerEnabled: boolean;
  concurrency: number;
  maxAttempts: number;
  maxFileSizeBytes: number;
};
