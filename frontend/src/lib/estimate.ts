import type { ComponentType, EstimateEvent, EstimateStatus, PriceMode } from "@/types/estimate";

export const COMPONENT_TYPES: ComponentType[] = [
  "SUPPLY",
  "LABOR",
  "SERVICE",
  "TRAVEL",
  "RENTAL",
  "SUBCONTRACT",
  "LUMP_SUM",
  "OTHER",
];

export const COMPONENT_TYPE_LABELS: Record<ComponentType, string> = {
  SUPPLY: "Fourniture",
  LABOR: "Main-d'œuvre",
  SERVICE: "Prestation",
  TRAVEL: "Déplacement et frais",
  RENTAL: "Location de matériel",
  SUBCONTRACT: "Sous-traitance",
  LUMP_SUM: "Forfait",
  OTHER: "Divers",
};

export const PRICE_MODE_LABELS: Record<PriceMode, string> = {
  COMPOSED: "Calculé (sous-détail × coefficients)",
  FIXED: "Prix imposé",
  TEXT: "Sans prix chiffré (PM, inclus…)",
};

export const ESTIMATE_STATUS_LABELS: Record<EstimateStatus, string> = {
  DRAFT: "Brouillon",
  VALIDATED: "Validé",
  SUPERSEDED: "Remplacé",
};

export const EVENT_LABELS: Record<EstimateEvent["kind"], string> = {
  CREATED: "Création",
  NEW_VERSION: "Nouvelle version",
  IMPORTED: "Import d'un bordereau",
  RATES_COPIED: "Taux recopiés",
  VALIDATED: "Validation",
  SUPERSEDED: "Remplacé",
  SUGGESTION_ACCEPTED: "Proposition de l'IA acceptée",
};

const money = new Intl.NumberFormat("fr-FR", { style: "currency", currency: "EUR", minimumFractionDigits: 2 });

const plain = new Intl.NumberFormat("fr-FR", { maximumFractionDigits: 4 });

/** An amount computed by the backend, with its cents; a dash when unknown. */
export function formatMoney(value: number | null | undefined): string {
  return value === null || value === undefined ? "—" : money.format(value);
}

/** A quantity, a cost or a coefficient, as entered. */
export function formatNumber(value: number | null | undefined): string {
  return value === null || value === undefined ? "—" : plain.format(value);
}

/** 0.2 shown as "20 %". */
export function formatRate(value: number | null | undefined): string {
  return value === null || value === undefined ? "—" : `${plain.format(value * 100)} %`;
}

/**
 * A number typed in French ("1 250,5") or not; null when empty, NaN when
 * unreadable.
 */
export function parseNumber(text: string): number | null {
  const cleaned = text.replace(/[\s  €%]/g, "").replace(",", ".");
  return cleaned === "" ? null : Number(cleaned);
}

/** A rate typed in percent ("20") as a share (0.2), with the 4 decimals the backend keeps. */
export function parsePercent(text: string): number | null {
  const value = parseNumber(text);
  return value === null || Number.isNaN(value) ? value : Math.round(value * 100) / 10000;
}

/** A share (0.2) written as percent for an input ("20"). */
export function percentInput(value: number | null | undefined): string {
  return value === null || value === undefined ? "" : String(Math.round(value * 10000) / 100).replace(".", ",");
}

/** A number written for an input, decimal comma. */
export function numberInput(value: number | null | undefined): string {
  return value === null || value === undefined ? "" : String(value).replace(".", ",");
}

/** Empty text is no value. */
export function textOrNull(text: string): string | null {
  return text.trim() === "" ? null : text.trim();
}
