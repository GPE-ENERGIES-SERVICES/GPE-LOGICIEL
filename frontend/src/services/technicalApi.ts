import { api } from "./http";

/** How the energy produced is used: it decides the template memoir. */
export const VALORISATION_LABELS: Record<string, string> = {
  SELF_TOTAL: "Autoconsommation totale",
  SELF_SURPLUS: "Autoconsommation avec revente du surplus",
  RESALE_TOTAL: "Revente totale",
};

/** What a user chose; every value may be empty. */
export type TechnicalChoices = {
  valorisation: string | null;
  roof: string | null;
  requestedKwc: number | null;
  moduleCount: number | null;
  moduleWc: number | null;
  inverterCount: number | null;
  inverterKva: number | null;
  constraints: string | null;
  openPoints: string | null;
  notes: string | null;
};

export type TechnicalView = {
  /** False while nothing was entered. */
  saved: boolean;
  choices: TechnicalChoices;
  sizing: {
    installedKwc: number | null;
    /** The installed power against the power asked, + above. */
    gapPercent: number | null;
    inverterKva: number | null;
    dcAcRatio: number | null;
    warnings: string[];
  };
  updatedBy: string | null;
  updatedAt: string | null;
  validatedBy: string | null;
  validatedAt: string | null;
  /** The powers the consultation file names, with their sentence: several when it contradicts itself. */
  powers: { kwc: number; source: string }[];
  /** The power of the module chosen, read in its reference. */
  moduleWc: number | null;
  mountings: string[];
  hasFile: boolean;
};

export function getTechnicalChoices(tenderId: number): Promise<TechnicalView> {
  return api(`/api/tenders/${tenderId}/technical-choices`);
}

/** A change after the validation takes it back. */
export function saveTechnicalChoices(tenderId: number, body: TechnicalChoices): Promise<TechnicalView> {
  return api(`/api/tenders/${tenderId}/technical-choices`, { method: "PUT", body });
}

export function validateTechnicalChoices(tenderId: number): Promise<TechnicalView> {
  return api(`/api/tenders/${tenderId}/technical-choices/validation`, { method: "POST" });
}
