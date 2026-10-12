import type { CollectJournal, CollectSource, FilterCriteria } from "@/types/settings";
import type { Role, User } from "@/types/user";

import { api } from "./http";

export function getFilterCriteria(): Promise<FilterCriteria> {
  return api("/api/filter-criteria");
}

/** Saves the company's criteria; every tender is filtered again. */
export function saveFilterCriteria(criteria: FilterCriteria): Promise<FilterCriteria> {
  return api("/api/filter-criteria", { method: "PUT", body: criteria });
}

/** The last collections, the latest first, and whether one is running. */
export function getCollectJournal(limit: number): Promise<CollectJournal> {
  return api(`/api/collect-runs?limit=${limit}`);
}

export function startCollect(): Promise<void> {
  return api("/api/collect-runs", { method: "POST" });
}

/** Every source, with its last run and its last successful one. */
export function getCollectSources(): Promise<CollectSource[]> {
  return api("/api/collect-runs/sources");
}

export function getUsers(): Promise<User[]> {
  return api("/api/users");
}

export function createUser(user: { email: string; displayName: string; password: string; role: Role }): Promise<User> {
  return api("/api/users", { method: "POST", body: user });
}

/** Changes an account; a null password keeps the current one. */
export function updateUser(
  id: number,
  changes: { displayName: string; role: Role; enabled: boolean; password: string | null },
): Promise<User> {
  return api(`/api/users/${id}`, { method: "PUT", body: changes });
}
