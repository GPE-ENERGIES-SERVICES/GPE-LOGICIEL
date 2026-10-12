import type { FollowUpStatus, NoticeKind, TenderStatus } from "./tender";

/** A tender of a group of probable duplicates, with what the team did on it. */
export type DuplicateCandidate = {
  id: number;
  source: string;
  externalId: string;
  title: string;
  buyer: string | null;
  publishedAt: string | null;
  deadline: string | null;
  status: TenderStatus;
  followUpStatus: FollowUpStatus;
  followUpComment: string | null;
  /** The team has a status, a comment, a follow or a dossier on it. */
  teamData: boolean;
  notices: { source: string; externalId: string; kind: NoticeKind; publishedAt: string | null; deadline: string | null }[];
};

/** Tenders not merged that share one eForms procedure identifier. */
export type DuplicateGroup = {
  procedureId: string;
  /** Two tenders of one source, one made by a rectification citing the other. */
  certain: boolean;
  explanation: string[];
  suggestedKeptId: number;
  tenders: DuplicateCandidate[];
};

export type DuplicateDecision = {
  id: number;
  kind: "MERGE" | "NOT_DUPLICATE";
  keptTenderId: number;
  otherTenderId: number;
  reason: string;
  decidedBy: string;
  decidedAt: string;
  undoneBy: string | null;
  undoneAt: string | null;
};

/** The merges a batch would make, to be read before confirming it. */
export type DuplicateBatchPreview = {
  token: string;
  merges: { procedureId: string; keptTenderId: number; mergedTenderId: number; reason: string }[];
  leftForReview: number;
};
