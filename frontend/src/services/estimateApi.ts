import type {
  AssistantAvailability,
  ComponentInput,
  Estimate,
  EstimateEvent,
  EstimateInput,
  EstimateSummary,
  ItemInput,
  ListedEstimate,
  OfferImport,
  PricingRate,
  PricingSettings,
  RateInput,
  Suggestion,
  Supplier,
  SupplierOffer,
} from "@/types/estimate";

import { api, upload } from "./http";

// Estimates: every change answers the whole estimate, computed again by the backend.

/** The estimates of every tender, the last changed first. */
export function getRecentEstimates(): Promise<ListedEstimate[]> {
  return api("/api/estimates");
}

export function getEstimates(tenderId: number): Promise<EstimateSummary[]> {
  return api(`/api/tenders/${tenderId}/estimates`);
}

export function createEstimate(tenderId: number, input: EstimateInput): Promise<Estimate> {
  return api(`/api/tenders/${tenderId}/estimates`, { method: "POST", body: input });
}

export function getEstimate(id: number): Promise<Estimate> {
  return api(`/api/estimates/${id}`);
}

export function updateEstimate(id: number, input: EstimateInput): Promise<Estimate> {
  return api(`/api/estimates/${id}`, { method: "PUT", body: input });
}

export function deleteEstimate(id: number): Promise<void> {
  return api(`/api/estimates/${id}`, { method: "DELETE" });
}

export function validateEstimate(id: number): Promise<Estimate> {
  return api(`/api/estimates/${id}/validation`, { method: "POST" });
}

export function newEstimateVersion(id: number): Promise<Estimate> {
  return api(`/api/estimates/${id}/versions`, { method: "POST" });
}

export function copyRatesAgain(id: number): Promise<Estimate> {
  return api(`/api/estimates/${id}/rates-copy`, { method: "POST" });
}

export function getEstimateEvents(id: number): Promise<EstimateEvent[]> {
  return api(`/api/estimates/${id}/events`);
}

export function importPriceSchedule(id: number, documentId: number): Promise<Estimate> {
  return api(`/api/estimates/${id}/imports`, { method: "POST", body: { documentId } });
}

/** The spreadsheet of the estimate, downloaded by the browser. */
export function estimateExportUrl(id: number): string {
  return `/api/estimates/${id}/export`;
}

export function addItem(estimateId: number, item: ItemInput): Promise<Estimate> {
  return api(`/api/estimates/${estimateId}/items`, { method: "POST", body: item });
}

/** Several lines at once (pasted from a spreadsheet): changed when they have an id, added otherwise. */
export function saveItems(estimateId: number, rows: { id: number | null; item: ItemInput }[]): Promise<Estimate> {
  return api(`/api/estimates/${estimateId}/items/batch`, { method: "POST", body: { rows } });
}

export function updateItem(itemId: number, item: ItemInput): Promise<Estimate> {
  return api(`/api/estimate-items/${itemId}`, { method: "PUT", body: item });
}

export function deleteItem(itemId: number): Promise<Estimate> {
  return api(`/api/estimate-items/${itemId}`, { method: "DELETE" });
}

export function reorderItems(estimateId: number, itemIds: number[]): Promise<Estimate> {
  return api(`/api/estimates/${estimateId}/items/order`, { method: "PUT", body: { itemIds } });
}

export function addComponent(itemId: number, component: ComponentInput): Promise<Estimate> {
  return api(`/api/estimate-items/${itemId}/components`, { method: "POST", body: component });
}

export function updateComponent(componentId: number, component: ComponentInput): Promise<Estimate> {
  return api(`/api/estimate-components/${componentId}`, { method: "PUT", body: component });
}

export function deleteComponent(componentId: number): Promise<Estimate> {
  return api(`/api/estimate-components/${componentId}`, { method: "DELETE" });
}

// Settings of GPE: read by every user, changed by an administrator.

export function getPricingSettings(): Promise<PricingSettings> {
  return api("/api/pricing-settings");
}

export function updatePricingSettings(input: EstimateInput): Promise<PricingSettings> {
  return api("/api/pricing-settings", { method: "PUT", body: input });
}

export function getRates(): Promise<PricingRate[]> {
  return api("/api/pricing-rates");
}

export function createRate(rate: RateInput): Promise<PricingRate> {
  return api("/api/pricing-rates", { method: "POST", body: rate });
}

export function updateRate(id: number, rate: RateInput): Promise<PricingRate> {
  return api(`/api/pricing-rates/${id}`, { method: "PUT", body: rate });
}

export function deleteRate(id: number): Promise<void> {
  return api(`/api/pricing-rates/${id}`, { method: "DELETE" });
}

// Suppliers and their prices, imported from their files.

export function getSuppliers(): Promise<Supplier[]> {
  return api("/api/suppliers");
}

export function createSupplier(code: string, name: string): Promise<Supplier> {
  return api("/api/suppliers", { method: "POST", body: { code, name } });
}

export function importOffers(supplierId: number, file: File, validFrom: string | null): Promise<OfferImport> {
  const form = new FormData();
  form.append("file", file);
  const query = validFrom ? `?validFrom=${encodeURIComponent(validFrom)}` : "";
  return upload(`/api/suppliers/${supplierId}/offers${query}`, form);
}

export function searchOffers(query: string): Promise<SupplierOffer[]> {
  return api(`/api/supplier-offers?q=${encodeURIComponent(query)}`);
}

// Assistance of the AI: proposals only, each decided by a user.

export function getAssistant(): Promise<AssistantAvailability> {
  return api("/api/estimate-assistant");
}

export function getSuggestions(estimateId: number): Promise<Suggestion[]> {
  return api(`/api/estimates/${estimateId}/suggestions`);
}

export function suggestSupplies(itemId: number): Promise<Suggestion[]> {
  return api(`/api/estimate-items/${itemId}/supply-suggestions`, { method: "POST" });
}

export function reviewEstimate(estimateId: number): Promise<Suggestion[]> {
  return api(`/api/estimates/${estimateId}/review`, { method: "POST" });
}

export function acceptSuggestion(id: number): Promise<Estimate> {
  return api(`/api/estimate-suggestions/${id}/acceptance`, { method: "POST" });
}

export function rejectSuggestion(id: number): Promise<void> {
  return api(`/api/estimate-suggestions/${id}/rejection`, { method: "POST" });
}
