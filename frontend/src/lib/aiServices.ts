/**
 * The services of the application that call the AI, by the kind recorded with
 * each call, and the step of the processing of a tender each belongs to.
 */
const SERVICES: Record<string, { label: string; step: string }> = {
  DCE_SUMMARY: { label: "Analyse du DCE (fiche, Go / No-Go)", step: "Analyse DCE" },
  FINANCIAL: { label: "Analyse des pièces financières", step: "Analyse DCE" },
  ESTIMATE_TECHNICAL: { label: "Choix techniques des prestations", step: "Solutions techniques" },
  ESTIMATE_DRAFT: { label: "Chiffrage par l'IA", step: "Chiffrage" },
  ESTIMATE_SUPPLY: { label: "Fournitures d'un poste", step: "Chiffrage" },
  ESTIMATE_PRICE: { label: "Prix depuis la bibliothèque", step: "Chiffrage" },
  ESTIMATE_REVIEW: { label: "Relecture du chiffrage", step: "Chiffrage" },
  MEMOIR_ANALYSIS: { label: "Mémoire : lecture et plan", step: "Mémoire technique" },
  MEMOIR_SECTION: { label: "Mémoire : rédaction d'une partie", step: "Mémoire technique" },
  MEMOIR_EDIT: { label: "Mémoire : retouche d'une partie", step: "Mémoire technique" },
  RESPONSE_FORM: { label: "Formulaires administratifs", step: "Dossier de réponse" },
  RESPONSE_PIECE: { label: "Pièces du dossier de réponse", step: "Dossier de réponse" },
};

export const AI_SERVICE_KINDS = Object.keys(SERVICES);

export function serviceLabel(kind: string): string {
  return SERVICES[kind]?.label ?? kind;
}

export function serviceStep(kind: string): string {
  return SERVICES[kind]?.step ?? "Autre";
}

const precise = new Intl.NumberFormat("fr-FR", { style: "currency", currency: "EUR", maximumFractionDigits: 4 });
const cents = new Intl.NumberFormat("fr-FR", { style: "currency", currency: "EUR" });

/** A cost of the AI: to the cent, or to the hundredth of a cent below one cent; "—" when not priced. */
export function formatCost(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return "—";
  }
  return value !== 0 && Math.abs(value) < 0.01 ? precise.format(value) : cents.format(value);
}
