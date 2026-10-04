import type { DocumentHit, DocumentInfo, Dossier, PieceType } from "@/types/document";

import { api, upload } from "./http";

/** Versions of the consultation file uploaded for a tender, the latest first. */
export function getDossiers(tenderId: number): Promise<Dossier[]> {
  return api(`/api/tenders/${tenderId}/dossiers`);
}

/** Uploads a new version of the consultation file (ZIP archive or files). */
export function uploadDossier(tenderId: number, files: FileList): Promise<Dossier> {
  const form = new FormData();
  Array.from(files).forEach((file) => form.append("files", file));
  return upload(`/api/tenders/${tenderId}/dossiers`, form);
}

/** Reads again the documents whose reading failed. */
export function retryDossier(dossierId: number): Promise<void> {
  return api(`/api/dossiers/${dossierId}/retry`, { method: "POST" });
}

export function changePieceType(documentId: number, pieceType: PieceType): Promise<DocumentInfo> {
  return api(`/api/documents/${documentId}/piece-type`, { method: "PUT", body: { pieceType } });
}

/**
 * Pages of the latest consultation file containing all the searched words,
 * in the order of the documents. Only the text read from the documents is
 * searched: spreadsheets and scanned plans are not.
 */
export function searchDocuments(tenderId: number, text: string): Promise<DocumentHit[]> {
  return api(`/api/tenders/${tenderId}/documents/search?q=${encodeURIComponent(text)}`);
}
