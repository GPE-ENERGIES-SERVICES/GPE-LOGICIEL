import type {
  AiChoice,
  DceEvent,
  DceLink,
  DceReview,
  DceSummary,
  DocumentHit,
  DocumentInfo,
  Dossier,
  PieceType,
} from "@/types/document";

import { api, upload } from "./http";

/** Where the consultation file can be obtained, as the notices of each source give it: links, not files. */
export function getDceLinks(tenderId: number): Promise<DceLink[]> {
  return api(`/api/tenders/${tenderId}/dce-links`);
}

/** What a consultation file belongs to: the routes of its dossiers and analyses start with it. */
export function tenderOwner(tenderId: number): string {
  return `/api/tenders/${tenderId}`;
}

export function reviewOwner(reviewId: number): string {
  return `/api/dce-reviews/${reviewId}`;
}

/** The consultation files analysed without any tender, the latest first. */
export function getDceReviews(): Promise<DceReview[]> {
  return api("/api/dce-reviews");
}

export function getDceReview(reviewId: number): Promise<DceReview> {
  return api(reviewOwner(reviewId));
}

export function createDceReview(name: string): Promise<DceReview> {
  return api("/api/dce-reviews", { method: "POST", body: { name } });
}

export function renameDceReview(reviewId: number, name: string): Promise<DceReview> {
  return api(reviewOwner(reviewId), { method: "PUT", body: { name } });
}

/** Deletes the analysis, its dossiers and their files. */
export function deleteDceReview(reviewId: number): Promise<void> {
  return api(reviewOwner(reviewId), { method: "DELETE" });
}

/** Gives the dossiers and analyses to a tender: the analysis without tender no longer exists. */
export function attachDceReview(reviewId: number, tenderId: number): Promise<void> {
  return api(`${reviewOwner(reviewId)}/attachment`, { method: "POST", body: { tenderId } });
}

/** Versions of the consultation file, the latest first. */
export function getDossiers(owner: string): Promise<Dossier[]> {
  return api(`${owner}/dossiers`);
}

/** Uploads a new version of the consultation file (ZIP archive or files). */
export function uploadDossier(owner: string, files: FileList): Promise<Dossier> {
  const form = new FormData();
  Array.from(files).forEach((file) => form.append("files", file));
  return upload(`${owner}/dossiers`, form);
}

/** Reads again the documents whose reading failed. */
export function retryDossier(dossierId: number): Promise<void> {
  return api(`/api/dossiers/${dossierId}/retry`, { method: "POST" });
}

/** Lets the analysis of a dossier start by itself once read, or not. */
export function allowAutoAnalysis(dossierId: number, enabled: boolean): Promise<Dossier> {
  return api(`/api/dossiers/${dossierId}/auto-analysis`, { method: "PUT", body: { enabled } });
}

export function changePieceType(documentId: number, pieceType: PieceType): Promise<DocumentInfo> {
  return api(`/api/documents/${documentId}/piece-type`, { method: "PUT", body: { pieceType } });
}

/** Sends a document to the AI or keeps it out whatever the rules; null lets the rules decide again. */
export function changeAiChoice(documentId: number, choice: AiChoice | null): Promise<DocumentInfo> {
  return api(`/api/documents/${documentId}/ai-choice`, { method: "PUT", body: { choice } });
}

/**
 * Pages of the latest consultation file containing all the searched words,
 * in the order of the documents. Only the text read from the documents is
 * searched: spreadsheets and scanned plans are not.
 */
export function searchDocuments(tenderId: number, text: string): Promise<DocumentHit[]> {
  return api(`/api/tenders/${tenderId}/documents/search?q=${encodeURIComponent(text)}`);
}

/** The changes of status of a link, the latest first. */
export function getDceLinkEvents(linkId: number): Promise<DceEvent[]> {
  return api(`/api/dce-links/${linkId}/events`);
}

/** The retrieval of the consultation files for the whole application (administrators). */
export function getDceSummary(): Promise<DceSummary> {
  return api("/api/dce/summary");
}

/** The last changes of status of every link (administrators). */
export function getDceEvents(limit: number): Promise<DceEvent[]> {
  return api(`/api/dce/events?limit=${limit}`);
}

/** A new attempt after a failure, refused unless the tender is eligible and the platform allowed (administrators). */
export function retryDceLink(linkId: number): Promise<DceLink> {
  return api(`/api/dce-links/${linkId}/retry`, { method: "POST" });
}
