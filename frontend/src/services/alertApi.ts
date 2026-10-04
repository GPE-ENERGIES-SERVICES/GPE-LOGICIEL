import type { AlertInput, AlertSummary } from "@/types/alert";

import { api } from "./http";

/** Saved searches of the logged-in user, with their results among the open consultations. */
export function getAlerts(): Promise<AlertSummary[]> {
  return api("/api/alerts");
}

export function createAlert(alert: AlertInput): Promise<AlertSummary> {
  return api("/api/alerts", { method: "POST", body: alert });
}

export function updateAlert(id: number, alert: AlertInput): Promise<AlertSummary> {
  return api(`/api/alerts/${id}`, { method: "PUT", body: alert });
}

export function deleteAlert(id: number): Promise<void> {
  return api(`/api/alerts/${id}`, { method: "DELETE" });
}

/** The user opens the results: the consultations published until now are no longer new. */
export function markAlertViewed(id: number): Promise<AlertSummary> {
  return api(`/api/alerts/${id}/viewed`, { method: "POST" });
}
