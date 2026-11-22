import { api, upload } from "./http";

/** A price schedule GPE filled and sent, kept in the price library. */
export type PriceLibraryImport = {
  id: number;
  label: string;
  fileName: string;
  priceYear: number | null;
  domain: string | null;
  itemCount: number;
  importedBy: string | null;
  importedAt: string;
  /** Its price schedule can be opened; not for the imports made before it was kept. */
  hasFile: boolean;
};

export function priceLibraryFileUrl(id: number): string {
  return `/api/price-library/imports/${id}/file`;
}

/** A line of a schedule GPE sent: its sale price, never a cost. */
export type PriceLibraryItem = {
  id: number;
  designation: string;
  unit: string | null;
  unitPrice: number;
  quantity: number | null;
  chapter: string | null;
  reference: string | null;
  importId: number;
  label: string;
  priceYear: number | null;
};

export function getPriceLibraryImports(): Promise<PriceLibraryImport[]> {
  return api("/api/price-library/imports");
}

export function importPriceSchedule(
  file: File,
  label: string,
  priceYear: number | null,
  domain: string | null,
): Promise<PriceLibraryImport> {
  const form = new FormData();
  form.append("file", file);
  const query = new URLSearchParams({ label });
  if (priceYear) query.set("priceYear", String(priceYear));
  if (domain) query.set("domain", domain);
  return upload(`/api/price-library/imports?${query}`, form);
}

export function deletePriceLibraryImport(id: number): Promise<void> {
  return api(`/api/price-library/imports/${id}`, { method: "DELETE" });
}

/** @param domain ELECTRICITY or PHOTOVOLTAIC, empty for every domain */
export function searchPriceLibrary(query: string, unit: string, domain = ""): Promise<PriceLibraryItem[]> {
  const params = new URLSearchParams({ q: query });
  if (unit) params.set("unit", unit);
  if (domain) params.set("domain", domain);
  return api(`/api/price-library?${params}`);
}
