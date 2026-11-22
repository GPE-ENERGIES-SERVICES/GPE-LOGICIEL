import { api, upload } from "./http";

/** The kinds of equipment, in the order of the equipment table of GPE. */
export const EQUIPMENT_CATEGORY_LABELS: Record<string, string> = {
  MODULE: "Panneaux photovoltaïques",
  MOUNTING: "Système de fixation",
  INVERTER: "Onduleur",
  MONITORING: "Supervision",
  ELECTRICAL_BOX: "Coffret et protections",
  CABLE_DC: "Câbles PV (DC)",
  CABLE_AC: "Câbles AC",
  CONNECTOR: "Connecteurs",
  CABLE_TRAY: "Chemin de câbles",
  SAFETY: "Coupure d'urgence",
  OTHER: "Autre matériel",
};

/** A technical sheet of the library: what it is was guessed from its file name, and can be corrected. */
export type EquipmentSheet = {
  id: number;
  category: string;
  brand: string | null;
  reference: string;
  fileName: string;
  sizeBytes: number;
  uploadedBy: string | null;
  uploadedAt: string;
};

export function getEquipmentSheets(): Promise<EquipmentSheet[]> {
  return api("/api/equipment-sheets");
}

/** What is not given is guessed from the file name; the same file again is the sheet already kept. */
export function addEquipmentSheet(
  file: File,
  fields: { category?: string; brand?: string; reference?: string },
): Promise<EquipmentSheet> {
  const form = new FormData();
  form.append("file", file);
  const query = new URLSearchParams();
  Object.entries(fields).forEach(([key, value]) => {
    if (value) query.set(key, value);
  });
  const params = query.toString();
  return upload(`/api/equipment-sheets${params ? `?${params}` : ""}`, form);
}

export function updateEquipmentSheet(
  id: number,
  body: { category: string; brand: string | null; reference: string },
): Promise<EquipmentSheet> {
  return api(`/api/equipment-sheets/${id}`, { method: "PUT", body });
}

export function deleteEquipmentSheet(id: number): Promise<void> {
  return api(`/api/equipment-sheets/${id}`, { method: "DELETE" });
}

export function equipmentSheetUrl(id: number): string {
  return `/api/equipment-sheets/${id}/file`;
}

/** The equipment chosen for a tender, in the order of the equipment table. */
export function getTenderEquipment(tenderId: number): Promise<EquipmentSheet[]> {
  return api(`/api/tenders/${tenderId}/equipment`);
}

export function chooseTenderEquipment(tenderId: number, sheetIds: number[]): Promise<EquipmentSheet[]> {
  return api(`/api/tenders/${tenderId}/equipment`, { method: "PUT", body: { sheetIds } });
}

export function equipmentTableUrl(tenderId: number): string {
  return `/api/tenders/${tenderId}/equipment/table`;
}
