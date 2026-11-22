import type { EquipmentSheet } from "./equipmentApi";
import { api } from "./http";

/** How the energy produced is used: it decides the template memoir. */
export const VALORISATION_LABELS: Record<string, string> = {
  SELF_TOTAL: "Autoconsommation totale",
  SELF_SURPLUS: "Autoconsommation avec revente du surplus",
  RESALE_TOTAL: "Revente totale",
};

export const REQUIREMENT_STATUS_LABELS: Record<string, string> = {
  TO_CHECK: "À vérifier",
  COMPLIANT: "Conforme",
  NOT_COMPLIANT: "Non conforme",
  VARIANT: "Variante",
};

export const QUESTION_STATUS_LABELS: Record<string, string> = {
  TO_ASK: "À poser",
  ASKED: "Posée",
  ANSWERED: "Répondue",
};

/** What a user chose; every value may be empty. */
export type TechnicalChoices = {
  valorisation: string | null;
  roof: string | null;
  requestedKwc: number | null;
  yieldKwhKwc: number | null;
  productionMwh: number | null;
  constraints: string | null;
  siteAccess: string | null;
  safety: string | null;
  subcontracting: string | null;
  planning: string | null;
  notes: string | null;
};

/** Where the AI read a value. */
export type TechnicalSource = {
  documentId: number | null;
  fileName: string | null;
  page: number | null;
  quote: string | null;
};

export type LineKind = "REQUIREMENT" | "QUESTION" | "QUANTITY" | "PLANE";

/** A requirement, a question, a key quantity or a roof plane. */
export type TechnicalLine = {
  id: number;
  kind: LineKind;
  text: string;
  /** GPE's answer to a requirement, the buyer's to a question. */
  answer: string | null;
  status: string | null;
  value: number | null;
  unit: string | null;
  moduleCount: number | null;
  orientation: string | null;
  tilt: number | null;
  /** AI while a user did not change it. */
  origin: "AI" | "USER";
  source: TechnicalSource | null;
};

export type LineInput = Omit<
  TechnicalLine,
  "id" | "kind" | "origin" | "source"
>;

/** An equipment chosen, with how many and how powerful (Wc for a module, kVA for an inverter). */
export type ChosenEquipment = {
  sheet: EquipmentSheet;
  quantity: number | null;
  unitPower: number | null;
  /** The power was read in the reference, not entered. */
  powerGuessed: boolean;
  note: string | null;
};

export type TechnicalView = {
  saved: boolean;
  choices: TechnicalChoices;
  /** For each value proposed by the AI and not changed since, where it was read. */
  aiSources: Record<string, TechnicalSource>;
  equipment: ChosenEquipment[];
  requirements: TechnicalLine[];
  questions: TechnicalLine[];
  quantities: TechnicalLine[];
  planes: TechnicalLine[];
  sizing: {
    modules: number;
    installedKwc: number | null;
    gapPercent: number | null;
    inverterKva: number | null;
    dcAcRatio: number | null;
    planeModules: number | null;
    expectedMwh: number | null;
    warnings: string[];
  };
  updatedBy: string | null;
  updatedAt: string | null;
  validatedBy: string | null;
  validatedAt: string | null;
  prefilledAt: string | null;
  powers: { kwc: number; source: string }[];
  domains: string[];
  hasFile: boolean;
  analysisDone: boolean;
};

export function getTechnicalChoices(tenderId: number): Promise<TechnicalView> {
  return api(`/api/tenders/${tenderId}/technical-choices`);
}

/** A value changed loses its AI source; any change takes the validation back. */
export function saveTechnicalChoices(
  tenderId: number,
  body: TechnicalChoices,
): Promise<TechnicalView> {
  return api(`/api/tenders/${tenderId}/technical-choices`, {
    method: "PUT",
    body,
  });
}

export function validateTechnicalChoices(
  tenderId: number,
): Promise<TechnicalView> {
  return api(`/api/tenders/${tenderId}/technical-choices/validation`, {
    method: "POST",
  });
}

/** What the latest analysis of the file read, where nothing is written yet. */
export function prefillTechnicalChoices(
  tenderId: number,
): Promise<{ added: number; view: TechnicalView }> {
  return api(`/api/tenders/${tenderId}/technical-choices/prefill`, {
    method: "POST",
  });
}

export function addTechnicalLine(
  tenderId: number,
  kind: LineKind,
  body: Partial<LineInput>,
): Promise<TechnicalView> {
  return api(`/api/tenders/${tenderId}/technical-lines/${kind}`, {
    method: "POST",
    body,
  });
}

export function updateTechnicalLine(
  tenderId: number,
  lineId: number,
  body: LineInput,
): Promise<TechnicalView> {
  return api(`/api/tenders/${tenderId}/technical-lines/${lineId}`, {
    method: "PUT",
    body,
  });
}

export function deleteTechnicalLine(
  tenderId: number,
  lineId: number,
): Promise<TechnicalView> {
  return api(`/api/tenders/${tenderId}/technical-lines/${lineId}`, {
    method: "DELETE",
  });
}

/** How many of an equipment chosen and how powerful. */
export function detailEquipment(
  tenderId: number,
  sheetId: number,
  body: {
    quantity: number | null;
    unitPower: number | null;
    note: string | null;
  },
): Promise<TechnicalView> {
  return api(`/api/tenders/${tenderId}/equipment/${sheetId}`, {
    method: "PUT",
    body,
  });
}
