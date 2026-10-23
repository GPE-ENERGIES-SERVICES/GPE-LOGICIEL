import type { DocumentState, DossierStatus, PieceType } from "@/types/document";
import type {
  FollowUpStatus,
  MarketType,
  NoticeKind,
  ProcedureType,
  TenderStatus,
  TenderTab,
} from "@/types/tender";

export const NOT_INDICATED = "Non indiqué";

/** Text in lower case and without accents, to compare words as a search does. */
export function normalizeText(text: string): string {
  return text
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase();
}

const TIME_ZONE = "Europe/Paris";

const dateTimeFormat = new Intl.DateTimeFormat("fr-FR", {
  timeZone: TIME_ZONE,
  day: "2-digit",
  month: "2-digit",
  year: "numeric",
  hour: "2-digit",
  minute: "2-digit",
});

const amountFormat = new Intl.NumberFormat("fr-FR", {
  style: "currency",
  currency: "EUR",
  maximumFractionDigits: 0,
});

/** An instant shown in Paris time, for example "15/11/2026 à 12:00". */
export function formatDateTime(value: string | null): string {
  if (!value) {
    return NOT_INDICATED;
  }
  const parts = Object.fromEntries(dateTimeFormat.formatToParts(new Date(value)).map((p) => [p.type, p.value]));
  return `${parts.day}/${parts.month}/${parts.year} à ${parts.hour}:${parts.minute}`;
}

const longDateTimeFormat = new Intl.DateTimeFormat("fr-FR", {
  timeZone: TIME_ZONE,
  day: "numeric",
  month: "long",
  year: "numeric",
  hour: "2-digit",
  minute: "2-digit",
});

/** An instant shown in full in Paris time, for example "15 novembre 2026 à 12:00". */
export function formatLongDateTime(value: string | null): string {
  if (!value) {
    return NOT_INDICATED;
  }
  const parts = Object.fromEntries(longDateTimeFormat.formatToParts(new Date(value)).map((p) => [p.type, p.value]));
  return `${parts.day} ${parts.month} ${parts.year} à ${parts.hour}:${parts.minute}`;
}

/** A calendar date (yyyy-MM-dd), shown as dd/MM/yyyy. */
export function formatDate(value: string | null): string {
  if (!value) {
    return NOT_INDICATED;
  }
  const [year, month, day] = value.slice(0, 10).split("-");
  return `${day}/${month}/${year}`;
}

export function formatAmount(value: number | null): string {
  return value === null ? NOT_INDICATED : `${amountFormat.format(value)} HT`;
}

export function formatDaysLeft(deadline: string | null, now: Date = new Date()): string {
  if (!deadline) {
    return "Date limite non indiquée";
  }
  const remaining = new Date(deadline).getTime() - now.getTime();
  if (remaining < 0) {
    return "Date limite dépassée";
  }
  const days = Math.floor(remaining / 86_400_000);
  if (days === 0) {
    return "Dernier jour";
  }
  return days === 1 ? "1 jour restant" : `${days} jours restants`;
}

export type DeadlineLevel = "unknown" | "passed" | "critical" | "close" | "normal";

/** A deadline in less than 3 days is critical, in less than a week it is close. */
export function deadlineLevel(deadline: string | null, now: Date = new Date()): DeadlineLevel {
  if (!deadline) {
    return "unknown";
  }
  const remaining = new Date(deadline).getTime() - now.getTime();
  if (remaining < 0) {
    return "passed";
  }
  if (remaining < 3 * 86_400_000) {
    return "critical";
  }
  return remaining < 7 * 86_400_000 ? "close" : "normal";
}

export function orNotIndicated(value: string | null | undefined): string {
  return value && value.trim() ? value : NOT_INDICATED;
}

/**
 * Title of a lot as read: simplified BOAMP notices write "2 : Bardage. Bardage"
 * (number, title, then a description repeating it), shown "Bardage". Any
 * other wording is kept as published.
 */
export function lotTitle(number: string, title: string | null): string | null {
  if (!title) {
    return title;
  }
  const prefix = `${number} : `;
  if (!title.startsWith(prefix)) {
    return title;
  }
  const text = title.slice(prefix.length).trim();
  const repeated = text.match(/^([\s\S]+?)\.\s+([\s\S]+?)\.?$/);
  return repeated && repeated[1].trim() === repeated[2].trim() ? repeated[1].trim() : text;
}

export const MARKET_TYPE_LABELS: Record<MarketType, string> = {
  WORKS: "Travaux",
  SERVICES: "Services",
  SUPPLIES: "Fournitures",
};

export const PROCEDURE_LABELS: Record<ProcedureType, string> = {
  OPEN: "Appel d'offres ouvert",
  RESTRICTED: "Appel d'offres restreint",
  NEGOTIATED: "Procédure négociée",
  COMPETITIVE_DIALOGUE: "Dialogue compétitif",
  ADAPTED: "Procédure adaptée (MAPA)",
  CONCESSION: "Concession / DSP",
  QUALIFICATION_SYSTEM: "Système de qualification",
  PRIOR_INFORMATION_CALL: "Préinformation valant mise en concurrence",
  PURCHASE_PROJECT: "Projet d'achat (prévisionnel)",
  OTHER: "Autre procédure",
};

export const STATUS_LABELS: Record<TenderStatus, string> = {
  ACTIVE: "Actif",
  CLOSED: "Clos",
  CANCELLED: "Annulé",
  UNSUCCESSFUL: "Sans suite",
};

export const FOLLOW_UP_LABELS: Record<FollowUpStatus, string> = {
  NEW: "Nouveau",
  TO_STUDY: "À analyser",
  RESPONDING: "En préparation",
  SUBMITTED: "Réponse envoyée",
  DISMISSED: "Écarté",
};

/** Tabs of the tender list, in display order. */
export const TENDER_TAB_LABELS: Record<TenderTab, string> = {
  NEW: "Nouveaux",
  TO_PROCESS: "À traiter",
  FOLLOWED: "Mes suivis",
  ALL: "Tous",
  DISMISSED: "Écartés",
  ARCHIVED: "Archivés",
  FILTERED_OUT: "Hors critères",
};

/** Kinds of work the company answers; the list will grow with the business. */
export const ACTIVITY_LABELS: Record<string, string> = {
  ELECTRICITY: "Électricité",
  PHOTOVOLTAIC: "Photovoltaïque",
  EV_CHARGING: "IRVE (bornes de recharge)",
  HEATING: "Chauffage",
  AIR_CONDITIONING: "Climatisation",
  ENERGY_RENOVATION: "Rénovation énergétique",
  PUBLIC_LIGHTING: "Éclairage public",
  NETWORKS: "Réseaux",
  VRD: "VRD",
  MAINTENANCE: "Maintenance",
  STUDIES: "Études",
};

export const NOTICE_KIND_LABELS: Record<NoticeKind, string> = {
  INITIAL: "Avis initial",
  RECTIFICATION: "Rectificatif",
  AWARD: "Avis d'attribution",
  CANCELLATION: "Avis d'annulation",
};

export const PIECE_TYPE_LABELS: Record<PieceType, string> = {
  RC: "Règlement de consultation (RC)",
  CCAP: "CCAP",
  CCTP: "CCTP",
  AE: "Acte d'engagement (AE)",
  DPGF: "DPGF",
  BPU: "BPU",
  DQE: "DQE",
  PLAN: "Plan",
  NOTICE: "Avis",
  OTHER: "Autre pièce",
};

export const DOCUMENT_STATE_LABELS: Record<DocumentState, string> = {
  PENDING: "En attente de lecture",
  READ: "Lu",
  SCANNED: "Lu en partie (scan)",
  FAILED: "Non analysé (échec)",
  NOT_ANALYZED: "Non analysé",
};

export const DOSSIER_STATUS_LABELS: Record<DossierStatus, string> = {
  PENDING: "En attente",
  PROCESSING: "Lecture en cours",
  DONE: "Lecture terminée",
  FAILED: "Échec de la lecture",
};

export function formatSize(bytes: number): string {
  if (bytes < 1024 * 1024) {
    return `${Math.max(1, Math.round(bytes / 1024))} Ko`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(1).replace(".", ",")} Mo`;
}

const FILE_FORMATS: Record<string, string> = {
  pdf: "PDF",
  doc: "Word",
  docx: "Word",
  odt: "Texte",
  rtf: "Texte",
  xls: "Excel",
  xlsx: "Excel",
  xlsm: "Excel",
  ods: "Tableur",
  csv: "CSV",
  dwg: "Plan DWG",
  dxf: "Plan DXF",
  jpg: "Image",
  jpeg: "Image",
  png: "Image",
  tif: "Image",
  tiff: "Image",
  zip: "Archive",
};

/** Format of a file, from its extension: "PDF", "Excel"… */
export function fileFormat(fileName: string): string {
  const extension = fileName.split(".").pop()?.toLowerCase() ?? "";
  return FILE_FORMATS[extension] ?? extension.toUpperCase();
}
