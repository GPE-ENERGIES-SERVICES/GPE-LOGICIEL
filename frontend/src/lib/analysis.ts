import { formatAmount, formatDate, formatDateTime } from "./format";
import type { Analysis, AnalysisStatus, ExtractedField, FieldDecision, FieldState, GridField } from "./types";

/** Shown when the documents read do not contain an information. */
export const NOT_IN_DOCUMENTS = "Non indiqué dans les documents analysés";

/** Below this number of days before the deadline, the delay is a point of attention. */
const SHORT_DELAY_DAYS = 15;

export const FIELD_STATE_LABELS: Record<FieldState, string> = {
  VERIFIED: "Source vérifiée",
  UNVERIFIED: "Source non vérifiée",
  NOT_INDICATED: "Non indiqué",
  AMBIGUOUS: "Ambiguë",
  CONTRADICTORY: "Contradictoire",
};

export const DECISION_LABELS: Record<FieldDecision, string> = {
  VALIDATED: "Validée",
  CORRECTED: "Corrigée",
  REJECTED: "Rejetée",
};

export const ANALYSIS_STATUS_LABELS: Record<AnalysisStatus, string> = {
  PENDING: "En attente",
  PROCESSING: "Analyse en cours",
  DONE: "Analyse terminée",
  FAILED: "Échec de l'analyse",
};

/**
 * The value to use: a decision of a user prevails over the model (RM10), a
 * rejected value is not used.
 */
export function effectiveValue(field: ExtractedField): string | null {
  switch (field.decision) {
    case "CORRECTED":
      return field.correctedValue;
    case "REJECTED":
      return null;
    default:
      return field.value;
  }
}

/** A value is reliable once its source is found or a user confirmed it. */
export function isReliable(field: ExtractedField): boolean {
  if (field.decision === "VALIDATED" || field.decision === "CORRECTED") {
    return true;
  }
  return field.decision === null && field.state === "VERIFIED";
}

/** Why a field belongs to the "to check" section, or null when it does not. */
export function checkReason(field: ExtractedField): string | null {
  if (field.decision === "REJECTED") {
    return "valeur rejetée";
  }
  if (isReliable(field)) {
    return null;
  }
  switch (field.state) {
    case "NOT_INDICATED":
      return "non trouvée";
    case "AMBIGUOUS":
      return "plusieurs lectures possibles";
    case "CONTRADICTORY":
      return "valeurs différentes selon les documents";
    default:
      return field.value === null ? "pas de réponse de l'analyse" : "extrait non retrouvé dans le document";
  }
}

export function formatValue(kind: GridField["kind"] | undefined, value: string | null): string {
  if (value === null || value.trim() === "") {
    return NOT_IN_DOCUMENTS;
  }
  if (kind === "DATE_TIME" && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(value)) {
    const [date, time] = value.split("T");
    return `${formatDate(date)} à ${time}`;
  }
  if ((kind === "DATE" || kind === "DATE_TIME") && /^\d{4}-\d{2}-\d{2}$/.test(value)) {
    return formatDate(value);
  }
  if (kind === "AMOUNT" && /^\d+(\.\d+)?$/.test(value)) {
    return formatAmount(Number(value));
  }
  return value;
}

/** Fields of the analysis grouped by key, in the order of the grid. */
export function fieldsByKey(analysis: Analysis): Map<string, ExtractedField[]> {
  const groups = new Map<string, ExtractedField[]>();
  for (const field of analysis.fields) {
    groups.set(field.key, [...(groups.get(field.key) ?? []), field]);
  }
  return groups;
}

/** Days before a deadline written yyyy-MM-ddTHH:mm or yyyy-MM-dd, read in local time. */
export function daysBefore(value: string | null, now: Date = new Date()): number | null {
  if (!value || !/^\d{4}-\d{2}-\d{2}/.test(value)) {
    return null;
  }
  const deadline = new Date(value.length > 10 ? value : `${value}T23:59`);
  return Math.floor((deadline.getTime() - now.getTime()) / 86_400_000);
}

/**
 * Points computed by code from the extracted values: visit required, short
 * delay, qualifications, deadline different from the notice.
 */
export function attentionPoints(analysis: Analysis, grid: GridField[]): string[] {
  const groups = fieldsByKey(analysis);
  const values = (key: string) =>
    (groups.get(key) ?? []).map(effectiveValue).filter((value): value is string => Boolean(value));
  const points: string[] = [];

  if (values("site_visit").some((value) => value.toLowerCase().startsWith("obligatoire"))) {
    points.push("Visite du site obligatoire");
  }
  const deadline = values("deadline")[0] ?? null;
  const days = deadline ? daysBefore(deadline) : null;
  if (days !== null && days >= 0 && days < SHORT_DELAY_DAYS) {
    points.push(`Délai court : ${days} jour${days > 1 ? "s" : ""} avant la date limite`);
  }
  const qualifications = values("qualifications");
  if (qualifications.length > 0) {
    points.push(`Qualifications exigées : ${qualifications.join(" ; ")}`);
  }
  const deadlineField = groups.get("deadline")?.[0];
  if (deadlineField?.state === "CONTRADICTORY" && deadlineField.decision === null && analysis.noticeDeadline) {
    const kind = grid.find((field) => field.key === "deadline")?.kind;
    points.push(
      `Date limite différente entre l'avis (${formatDateTime(analysis.noticeDeadline)}) et le dossier ` +
        `(${formatValue(kind, deadlineField.value)})`,
    );
  }
  return points;
}
