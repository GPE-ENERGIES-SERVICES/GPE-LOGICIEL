import type {
  AiAction,
  CompanyBlock,
  FormerMemoir,
  Memoir,
  MemoirRevision,
  MemoirSection,
  MemoirStatus,
  MemoirWorkspace,
} from "@/types/memoir";

import { api, upload } from "./http";

export function getMemoirs(): Promise<Memoir[]> {
  return api("/api/memoirs");
}

export function createMemoir(body: { title: string | null; tenderId: number | null; lotNumber: string | null }): Promise<Memoir> {
  return api("/api/memoirs", { method: "POST", body });
}

export function getWorkspace(id: number): Promise<MemoirWorkspace> {
  return api(`/api/memoirs/${id}`);
}

export function updateMemoir(
  id: number,
  body: { title: string; lotNumber: string | null; status: MemoirStatus },
): Promise<Memoir> {
  return api(`/api/memoirs/${id}`, { method: "PUT", body });
}

export function deleteMemoir(id: number): Promise<void> {
  return api(`/api/memoirs/${id}`, { method: "DELETE" });
}

// Outline: every change answers the whole workspace.

export function applyTemplate(id: number): Promise<MemoirWorkspace> {
  return api(`/api/memoirs/${id}/outline/template`, { method: "POST" });
}

export function addSection(
  id: number,
  body: { parentId: number | null; title: string; description: string | null },
): Promise<MemoirWorkspace> {
  return api(`/api/memoirs/${id}/sections`, { method: "POST", body });
}

export function renameSection(
  sectionId: number,
  body: { title: string; description: string | null },
): Promise<MemoirWorkspace> {
  return api(`/api/memoir-sections/${sectionId}`, { method: "PUT", body: { parentId: null, ...body } });
}

export function deleteSection(sectionId: number): Promise<MemoirWorkspace> {
  return api(`/api/memoir-sections/${sectionId}`, { method: "DELETE" });
}

export function acceptSection(sectionId: number): Promise<MemoirWorkspace> {
  return api(`/api/memoir-sections/${sectionId}/acceptance`, { method: "POST" });
}

export function acceptAllSections(id: number): Promise<MemoirWorkspace> {
  return api(`/api/memoirs/${id}/outline/acceptance`, { method: "POST" });
}

export function reorderOutline(
  id: number,
  sections: { id: number; parentId: number | null; position: number }[],
): Promise<MemoirWorkspace> {
  return api(`/api/memoirs/${id}/outline`, { method: "PUT", body: { sections } });
}

export function validateOutline(id: number): Promise<MemoirWorkspace> {
  return api(`/api/memoirs/${id}/outline/validation`, { method: "POST" });
}

export function linkRequirements(sectionId: number, requirementIds: number[]): Promise<MemoirWorkspace> {
  return api(`/api/memoir-sections/${sectionId}/requirements`, { method: "PUT", body: { requirementIds } });
}

export function addRequirement(id: number, category: string, text: string): Promise<MemoirWorkspace> {
  return api(`/api/memoirs/${id}/requirements`, { method: "POST", body: { category, text } });
}

export function deleteRequirement(id: number, requirementId: number): Promise<MemoirWorkspace> {
  return api(`/api/memoirs/${id}/requirements/${requirementId}`, { method: "DELETE" });
}

// Content

export function saveContent(sectionId: number, html: string): Promise<MemoirSection> {
  return api(`/api/memoir-sections/${sectionId}/content`, { method: "PUT", body: { html } });
}

export function acceptProposal(sectionId: number, append: boolean): Promise<MemoirSection> {
  return api(`/api/memoir-sections/${sectionId}/proposal/acceptance`, { method: "POST", body: { append } });
}

export function rejectProposal(sectionId: number): Promise<MemoirSection> {
  return api(`/api/memoir-sections/${sectionId}/proposal`, { method: "DELETE" });
}

export function getRevisions(sectionId: number): Promise<MemoirRevision[]> {
  return api(`/api/memoir-sections/${sectionId}/revisions`);
}

export function restoreRevision(sectionId: number, revisionId: number): Promise<MemoirSection> {
  return api(`/api/memoir-sections/${sectionId}/revisions/${revisionId}/restoration`, { method: "POST" });
}

export function insertCompany(sectionId: number, block: CompanyBlock): Promise<MemoirSection> {
  return api(`/api/memoir-sections/${sectionId}/company/${block}`, { method: "POST" });
}

export function askAi(
  sectionId: number,
  body: { action: AiAction; instruction: string | null; requirementId: number | null },
): Promise<MemoirSection> {
  return api(`/api/memoir-sections/${sectionId}/ai`, { method: "POST", body });
}

// Work of the AI, followed by reading the workspace again.

export function startAnalysis(id: number): Promise<MemoirWorkspace> {
  return api(`/api/memoirs/${id}/analysis`, { method: "POST" });
}

export function startWriting(id: number, sectionIds: number[] = []): Promise<MemoirWorkspace> {
  return api(`/api/memoirs/${id}/writing`, { method: "POST", body: { sectionIds } });
}

export function cancelJob(id: number): Promise<MemoirWorkspace> {
  return api(`/api/memoirs/${id}/job/cancellation`, { method: "POST" });
}

export function uploadMemoirImage(id: number, file: File): Promise<{ id: number; url: string }> {
  const form = new FormData();
  form.append("file", file);
  return upload(`/api/memoirs/${id}/files`, form);
}

// Library of the former memoirs

export function getLibrary(): Promise<FormerMemoir[]> {
  return api("/api/memoir-library");
}

export function importFormerMemoir(form: FormData): Promise<FormerMemoir> {
  return upload("/api/memoir-library", form);
}

export function updateFormerMemoir(
  id: number,
  body: Pick<FormerMemoir, "title" | "writtenOn" | "context" | "marketType" | "activityCode" | "useForAi">,
): Promise<FormerMemoir> {
  return api(`/api/memoir-library/${id}`, { method: "PUT", body });
}

export function deleteFormerMemoir(id: number): Promise<void> {
  return api(`/api/memoir-library/${id}`, { method: "DELETE" });
}

export function formerMemoirUrl(id: number): string {
  return `/api/memoir-library/${id}/file`;
}
