import type { Analysis } from "@/types/analysis";
import type { Dossier } from "@/types/document";

export type Progress = { value: number | null; label: string; detail?: string };

/** The reading of a dossier: files read out of all, while it goes on; null once read. */
export function readingProgress(dossier: Dossier): Progress | null {
  if (dossier.status !== "PENDING" && dossier.status !== "PROCESSING") {
    return null;
  }
  const total = dossier.documents.length;
  const read = dossier.documents.filter((document) => document.state !== "PENDING").length;
  return {
    value: total === 0 ? null : read / total,
    label: total === 0 ? "Lecture du dossier…" : `Lecture du dossier : ${read} / ${total} fichiers lus`,
    detail: "Les pages scannées des pièces utiles sont lues par OCR : quelques secondes par page.",
  };
}

function minutes(milliseconds: number): string {
  const value = Math.max(1, Math.round(milliseconds / 60000));
  return `${value} min`;
}

/**
 * An analysis waiting or sent to the AI, whose length cannot be known: how
 * long it has been going on, and how long the earlier ones took.
 * @param now the current time, in milliseconds
 */
export function analysisProgress(analysis: Analysis, history: Analysis[], now: number): Progress | null {
  if (analysis.status === "PENDING") {
    return { value: null, label: "Analyse en attente d'envoi à l'IA", detail: "Elle peut encore être annulée." };
  }
  if (analysis.status !== "PROCESSING") {
    return null;
  }
  const durations = history
    .filter((item) => item.status === "DONE" && item.finishedAt)
    .map((item) => new Date(item.finishedAt!).getTime() - new Date(item.createdAt).getTime())
    .sort((a, b) => a - b);
  const usual = durations.length ? durations[Math.floor(durations.length / 2)] : null;
  const elapsed = now - new Date(analysis.createdAt).getTime();
  return {
    value: null,
    label: `Analyse par l'IA en cours depuis ${minutes(elapsed)}`,
    detail:
      (usual ? `Durée habituelle : environ ${minutes(usual)}. ` : "") +
      "Envoyée à l'IA : elle ne peut plus être annulée.",
  };
}
