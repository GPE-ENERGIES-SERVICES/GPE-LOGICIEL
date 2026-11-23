import type {
  AdministrativeForm,
  FormDecision,
  FormField,
  PieceCategory,
  ResponseForm,
  ResponseSummary,
  ResponseView,
} from "@/types/response";

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
  return api(`/api/tenders/${tenderId}/response/preparation`, {
    method: "POST",
  });
}

export function addPiece(
  tenderId: number,
  category: PieceCategory,
  label: string,
): Promise<ResponseView> {
  return api(`/api/tenders/${tenderId}/response/pieces`, {
    method: "POST",
    body: { category, label },
  });
}

export function changePiece(
  id: number,
  body: { label: string; notNeeded: boolean; companyDocumentId: number | null },
): Promise<ResponseView> {
  return api(`/api/response-pieces/${id}`, { method: "PUT", body });
}

export function checkPiece(
  id: number,
  checked: boolean,
): Promise<ResponseView> {
  return api(`/api/response-pieces/${id}/check`, {
    method: "PUT",
    body: { checked },
  });
}

export function deletePiece(id: number): Promise<ResponseView> {
  return api(`/api/response-pieces/${id}`, { method: "DELETE" });
}

export function uploadPieceFile(id: number, file: File): Promise<ResponseView> {
  const form = new FormData();
  form.append("file", file);
  return upload(`/api/response-pieces/${id}/file`, form);
}

/** The model of a form, when the DCE gives none in Word: the AI fills it as it does the one of the DCE. */
export function uploadFormTemplate(pieceId: number, file: File): Promise<ResponseForm> {
  const form = new FormData();
  form.append("file", file);
  return upload(`/api/response-pieces/${pieceId}/form/template`, form);
}

/** The forms to fill of every answer begun, or of one tender. */
export function getAdministrativeForms(
  tenderId?: number,
): Promise<AdministrativeForm[]> {
  return api(
    `/api/administrative-forms${tenderId ? `?tenderId=${tenderId}` : ""}`,
  );
}

/** Validating writes the Word, joins it and checks the piece; a rejection needs its reason. */
export function reviewForm(
  pieceId: number,
  body: { decision: FormDecision; comment?: string },
): Promise<ResponseForm> {
  return api(`/api/response-pieces/${pieceId}/form/review`, {
    method: "POST",
    body,
  });
}

export function getForm(pieceId: number): Promise<ResponseForm> {
  return api(`/api/response-pieces/${pieceId}/form`);
}

/** Reads the form of the consultation file; with the AI, proposes its values. What a user wrote stays. */
export function startForm(
  pieceId: number,
  body: { lotNumber: string | null; useAi: boolean },
): Promise<ResponseForm> {
  return api(`/api/response-pieces/${pieceId}/form`, { method: "POST", body });
}

export function changeFormField(
  fieldId: number,
  body: { value: string | null; checked: boolean },
): Promise<FormField> {
  return api(`/api/response-form-fields/${fieldId}`, { method: "PUT", body });
}

/** Writes the Word document and joins it to the piece. */
export function generateForm(pieceId: number): Promise<ResponseView> {
  return api(`/api/response-pieces/${pieceId}/form/document`, {
    method: "POST",
  });
}

export function removePieceFile(id: number): Promise<ResponseView> {
  return api(`/api/response-pieces/${id}/file`, { method: "DELETE" });
}

/** The text of a piece written here: saved and joined as a Word file in the style of GPE. */
export function writePiece(id: number, html: string): Promise<ResponseView> {
  return api(`/api/response-pieces/${id}/content`, {
    method: "PUT",
    body: { html },
  });
}

/** A draft of the piece by the AI, joined as a Word file to read and check. */
export function draftPiece(id: number): Promise<ResponseView> {
  return api(`/api/response-pieces/${id}/draft`, { method: "POST" });
}

/** Joins the file of another step: the memoir in Word, the price schedule filled. */
export function pieceFromStep(id: number): Promise<ResponseView> {
  return api(`/api/response-pieces/${id}/from-step`, { method: "POST" });
}
