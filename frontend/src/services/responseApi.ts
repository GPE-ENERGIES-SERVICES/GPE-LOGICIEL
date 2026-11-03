import type { PieceCategory, ResponseSummary, ResponseView } from "@/types/response";

import { api, upload } from "./http";

/** The tenders whose answer has begun, the nearest deadline first. */
export function getResponses(): Promise<ResponseSummary[]> {
  return api("/api/responses");
}

export function getResponse(tenderId: number): Promise<ResponseView> {
  return api(`/api/tenders/${tenderId}/response`);
}

/** Adds the pieces read by the latest analysis that are not listed yet; what is listed does not change. */
export function prepareResponse(tenderId: number): Promise<ResponseView> {
  return api(`/api/tenders/${tenderId}/response/preparation`, { method: "POST" });
}

export function addPiece(tenderId: number, category: PieceCategory, label: string): Promise<ResponseView> {
  return api(`/api/tenders/${tenderId}/response/pieces`, { method: "POST", body: { category, label } });
}

export function changePiece(
  id: number,
  body: { label: string; notNeeded: boolean; companyDocumentId: number | null },
): Promise<ResponseView> {
  return api(`/api/response-pieces/${id}`, { method: "PUT", body });
}

export function checkPiece(id: number, checked: boolean): Promise<ResponseView> {
  return api(`/api/response-pieces/${id}/check`, { method: "PUT", body: { checked } });
}

export function deletePiece(id: number): Promise<ResponseView> {
  return api(`/api/response-pieces/${id}`, { method: "DELETE" });
}

export function uploadPieceFile(id: number, file: File): Promise<ResponseView> {
  const form = new FormData();
  form.append("file", file);
  return upload(`/api/response-pieces/${id}/file`, form);
}

export function removePieceFile(id: number): Promise<ResponseView> {
  return api(`/api/response-pieces/${id}/file`, { method: "DELETE" });
}
