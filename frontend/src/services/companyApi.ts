import type { CompanyFile, CompanyOverview, CompanyProfile, CompanyRow } from "@/types/company";

import { api, upload } from "./http";

/** The lists of the company, each at /api/company/{list}. */
export type CompanyList =
  | "contacts"
  | "activities"
  | "employees"
  | "credentials"
  | "equipment"
  | "certifications"
  | "references"
  | "documents"
  | "economic-parameters";

export function getRows(list: CompanyList): Promise<CompanyRow[]> {
  return api(`/api/company/${list}`);
}

export function createRow(list: CompanyList, body: Record<string, unknown>): Promise<CompanyRow> {
  return api(`/api/company/${list}`, { method: "POST", body });
}

export function updateRow(list: CompanyList, id: number, body: Record<string, unknown>): Promise<CompanyRow> {
  return api(`/api/company/${list}/${id}`, { method: "PUT", body });
}

export function deleteRow(list: CompanyList, id: number): Promise<void> {
  return api(`/api/company/${list}/${id}`, { method: "DELETE" });
}

/** Adds a file to a line; for a document it becomes the current file, the former ones stay as history. */
export function uploadRowFile(list: CompanyList, id: number, file: File): Promise<CompanyFile> {
  const form = new FormData();
  form.append("file", file);
  return upload(`/api/company/${list}/${id}/files`, form);
}

export function deleteCompanyFile(id: number): Promise<void> {
  return api(`/api/company/files/${id}`, { method: "DELETE" });
}

/** Images are shown, the other files downloaded. */
export function companyFileUrl(id: number): string {
  return `/api/company/files/${id}`;
}

export function getProfile(): Promise<CompanyProfile> {
  return api("/api/company/profile");
}

export function updateProfile(body: Record<string, unknown>): Promise<CompanyProfile> {
  return api("/api/company/profile", { method: "PUT", body });
}

export function uploadLogo(file: File): Promise<CompanyFile> {
  const form = new FormData();
  form.append("file", file);
  return upload("/api/company/profile/logo", form);
}

export function getOverview(): Promise<CompanyOverview> {
  return api("/api/company/overview");
}
