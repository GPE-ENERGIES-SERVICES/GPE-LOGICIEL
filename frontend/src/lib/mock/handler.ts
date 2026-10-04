/**
 * Simulated backend: answers the URLs of the Spring Boot API with the same
 * JSON, from the demo data. It also describes the search parameters the
 * backend will have to accept. Decisions taken by the user are kept in the
 * browser so that they survive a reload.
 */

import { ApiError } from "../api";
import { ACTIVITY_LABELS, TENDER_TAB_LABELS } from "../format";
import { REGIONS } from "../regions";
import type {
  CollectJournal,
  FollowUpStatus,
  Page,
  TenderAnalysis,
  TenderDetail,
  TenderSummary,
  TenderTab,
} from "../types";
import { MOCK_DOSSIERS, MOCK_TENDERS, MOCK_USER } from "./data";

const STORAGE_KEY = "gpe-demo-state";
const LATENCY_MS = 150;

type FollowUp = Pick<TenderDetail, "followUpStatus" | "followUpComment" | "followUpBy" | "followUpAt">;

type SavedState = {
  followUps: Record<number, FollowUp>;
  followed: Record<number, boolean>;
};

function loadState(): SavedState {
  try {
    const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? "null");
    if (saved?.followUps && saved?.followed) {
      return saved;
    }
  } catch {
    // unavailable storage or unreadable value: start from the demo data
  }
  return { followUps: {}, followed: {} };
}

const state = loadState();

const tenders: TenderDetail[] = MOCK_TENDERS.map((tender) => ({
  ...tender,
  ...state.followUps[tender.id],
  followed: state.followed[tender.id] ?? tender.followed,
}));

function saveState() {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  } catch {
    // the change only lasts until the next reload
  }
}

function find(id: string): TenderDetail {
  const tender = tenders.find((candidate) => candidate.id === Number(id));
  if (!tender) {
    throw new ApiError(404, "Élément introuvable.");
  }
  return tender;
}

function toSummary(tender: TenderDetail): TenderSummary {
  return {
    id: tender.id,
    title: tender.title,
    buyer: tender.buyer,
    location: tender.location,
    departments: tender.departments,
    marketTypes: tender.marketTypes,
    estimatedAmount: tender.estimatedAmount,
    publishedAt: tender.publishedAt,
    deadline: tender.deadline,
    status: tender.status,
    followUpStatus: tender.followUpStatus,
    retained: tender.retained,
    filterReason: tender.filterReason,
    reference: tender.reference,
    procedureType: tender.procedureType,
    source: tender.source,
    lotCount: tender.lotCount,
    activities: tender.activities,
    distanceKm: tender.distanceKm,
    relevanceScore: tender.relevanceScore,
    followed: tender.followed,
    documentCount: tender.documentCount,
  };
}

// --- Search -----------------------------------------------------------------

/**
 * Tabs of the list. A tender excluded by the filter stays out of the other
 * tabs until a user takes a decision on it.
 */
function inTab(tender: TenderDetail, tab: TenderTab): boolean {
  const active = tender.status === "ACTIVE";
  const status = tender.followUpStatus;
  const filteredOut = tender.retained === false && status === "NEW";
  switch (tab) {
    case "NEW":
      return tender.retained === true && status === "NEW" && active;
    case "TO_PROCESS":
      return (status === "TO_STUDY" || status === "RESPONDING") && active;
    case "FOLLOWED":
      return tender.followed === true;
    case "ALL":
      return !filteredOut;
    case "DISMISSED":
      return status === "DISMISSED";
    case "ARCHIVED":
      return !active && !filteredOut && status !== "DISMISSED";
    case "FILTERED_OUT":
      return filteredOut && active;
  }
}

function normalize(text: string): string {
  return text
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .toLowerCase();
}

/** Every word must appear in one of the searched fields, ignoring case and accents. */
function matchesText(tender: TenderDetail, text: string): boolean {
  const searched = normalize(
    [
      tender.title,
      tender.buyer,
      tender.reference,
      tender.externalId,
      tender.location,
      tender.description,
      ...(tender.activities ?? []).map((code) => ACTIVITY_LABELS[code] ?? code),
    ].join(" "),
  );
  return normalize(text)
    .split(/\s+/)
    .every((word) => searched.includes(word));
}

function list(value: string | null): string[] {
  return value ? value.split(",").map((item) => item.trim()).filter(Boolean) : [];
}

function number(value: string | null): number | null {
  return value === null || value === "" || Number.isNaN(Number(value)) ? null : Number(value);
}

function time(value: string | null | undefined): number | null {
  return value ? new Date(value).getTime() : null;
}

/**
 * Filters accepted by GET /api/tenders, all combinable. Lists are comma
 * separated. As in the backend, an unknown amount never hides a tender; use
 * amountKnown=true to hide them.
 */
function matches(tender: TenderDetail, params: URLSearchParams): boolean {
  const tab = (params.get("tab") as TenderTab | null) ?? "NEW";
  const q = params.get("q")?.trim();
  const departments = list(params.get("department"));
  const region = params.get("region");
  const city = params.get("city")?.trim();
  const maxDistanceKm = number(params.get("maxDistanceKm"));
  const activities = list(params.get("activity"));
  const publishedFrom = params.get("publishedFrom");
  const publishedTo = params.get("publishedTo");
  const deadlineFrom = time(params.get("deadlineFrom") && `${params.get("deadlineFrom")}T00:00:00`);
  const deadlineTo = time(params.get("deadlineTo") && `${params.get("deadlineTo")}T23:59:59`);
  const minDaysLeft = number(params.get("minDaysLeft"));
  const maxDaysLeft = number(params.get("maxDaysLeft"));
  const minAmount = number(params.get("minAmount"));
  const maxAmount = number(params.get("maxAmount"));
  const amountKnown = params.get("amountKnown");
  const procedures = list(params.get("procedure"));
  const hasLots = params.get("hasLots");
  const minLots = number(params.get("minLots"));
  const statuses = list(params.get("status"));

  const deadline = time(tender.deadline);
  const daysLeft = deadline === null ? null : (deadline - Date.now()) / 86_400_000;
  const amount = tender.estimatedAmount;
  const lotCount = tender.lotCount ?? 0;

  return (
    inTab(tender, tab) &&
    (!q || matchesText(tender, q)) &&
    (departments.length === 0 || tender.departments.some((code) => departments.includes(code))) &&
    (!region || tender.departments.some((code) => REGIONS[region]?.includes(code))) &&
    (!city || normalize(tender.location ?? "").includes(normalize(city))) &&
    (maxDistanceKm === null || (tender.distanceKm != null && tender.distanceKm <= maxDistanceKm)) &&
    (activities.length === 0 || (tender.activities ?? []).some((code) => activities.includes(code))) &&
    (!publishedFrom || (tender.publishedAt !== null && tender.publishedAt >= publishedFrom)) &&
    (!publishedTo || (tender.publishedAt !== null && tender.publishedAt <= publishedTo)) &&
    (deadlineFrom === null || (deadline !== null && deadline >= deadlineFrom)) &&
    (deadlineTo === null || (deadline !== null && deadline <= deadlineTo)) &&
    (minDaysLeft === null || (daysLeft !== null && daysLeft >= minDaysLeft)) &&
    (maxDaysLeft === null || (daysLeft !== null && daysLeft >= 0 && daysLeft <= maxDaysLeft)) &&
    (minAmount === null || amount === null || amount >= minAmount) &&
    (maxAmount === null || amount === null || amount <= maxAmount) &&
    (amountKnown === null || (amountKnown === "true") === (amount !== null)) &&
    (procedures.length === 0 || (tender.procedureType != null && procedures.includes(tender.procedureType))) &&
    (hasLots === null || (hasLots === "true") === lotCount > 0) &&
    (minLots === null || lotCount >= minLots) &&
    (statuses.length === 0 || statuses.includes(tender.followUpStatus)) &&
    (params.get("followed") !== "true" || tender.followed === true)
  );
}

/** Sort keys accepted by GET /api/tenders; "-" sorts in descending order. */
const SORT_VALUES: Record<string, (tender: TenderDetail) => number | null> = {
  deadline: (tender) => time(tender.deadline),
  published: (tender) => time(tender.publishedAt),
  amount: (tender) => tender.estimatedAmount,
  relevance: (tender) => tender.relevanceScore ?? null,
  distance: (tender) => tender.distanceKm ?? null,
};

/** Closest deadline first by default, as in the backend; missing values always last. */
function sort(found: TenderDetail[], sortParam: string | null): TenderDetail[] {
  const descending = sortParam?.startsWith("-") ?? false;
  const value = SORT_VALUES[sortParam?.replace(/^-/, "") ?? ""] ?? SORT_VALUES.deadline;
  return [...found].sort((a, b) => {
    const left = value(a);
    const right = value(b);
    if (left === right) {
      return b.id - a.id;
    }
    if (left === null || right === null) {
      return left === null ? 1 : -1;
    }
    return descending ? right - left : left - right;
  });
}

function search(params: URLSearchParams): Page<TenderSummary> {
  const page = Math.max(0, number(params.get("page")) ?? 0);
  const size = Math.min(100, Math.max(1, number(params.get("size")) ?? 20));
  const found = sort(
    tenders.filter((tender) => matches(tender, params)),
    params.get("sort"),
  );
  return {
    content: found.slice(page * size, (page + 1) * size).map(toSummary),
    page,
    size,
    totalElements: found.length,
    totalPages: Math.ceil(found.length / size),
  };
}

function counts(): Record<TenderTab, number> {
  return Object.fromEntries(
    (Object.keys(TENDER_TAB_LABELS) as TenderTab[]).map((tab) => [
      tab,
      tenders.filter((tender) => inTab(tender, tab)).length,
    ]),
  ) as Record<TenderTab, number>;
}

// --- Decisions --------------------------------------------------------------

function followUp(id: string, body: unknown): TenderDetail {
  const tender = find(id);
  const { status, comment } = body as { status: FollowUpStatus; comment?: string | null };
  const change: FollowUp = {
    followUpStatus: status,
    // As in the backend, a missing comment keeps the previous one.
    followUpComment: comment === undefined || comment === null ? tender.followUpComment : comment.trim() || null,
    followUpBy: MOCK_USER.displayName,
    followUpAt: new Date().toISOString(),
  };
  Object.assign(tender, change);
  state.followUps[tender.id] = change;
  saveState();
  return tender;
}

function follow(id: string, followed: boolean): undefined {
  const tender = find(id);
  tender.followed = followed;
  state.followed[tender.id] = followed;
  saveState();
  return undefined;
}

// --- Other screens ----------------------------------------------------------

function collectJournal(): CollectJournal {
  const start = new Date();
  start.setHours(start.getHours() >= 13 ? 13 : 7, 0, 0, 0);
  const today = start.toISOString().slice(0, 10);
  return {
    running: false,
    runs: [
      {
        id: 1,
        source: "BOAMP",
        trigger: "SCHEDULED",
        status: "SUCCESS",
        sinceDate: today,
        startedAt: start.toISOString(),
        finishedAt: new Date(start.getTime() + 95_000).toISOString(),
        noticesReceived: 214,
        noticesNew: tenders.filter((tender) => tender.publishedAt === today).length,
        errorMessage: null,
      },
    ],
  };
}

const NO_ANALYSIS: TenderAnalysis = { configured: false, grid: [], latest: null, history: [] };

// --- Routing ----------------------------------------------------------------

type Route = [method: string, path: RegExp, handle: (id: string, url: URL, body: unknown) => unknown];

const ROUTES: Route[] = [
  ["GET", /^\/api\/auth\/me$/, () => MOCK_USER],
  ["POST", /^\/api\/auth\/login$/, () => MOCK_USER],
  ["POST", /^\/api\/auth\/logout$/, () => undefined],
  ["GET", /^\/api\/collect-runs$/, () => collectJournal()],
  ["GET", /^\/api\/tenders$/, (_, url) => search(url.searchParams)],
  ["GET", /^\/api\/tenders\/counts$/, () => counts()],
  ["GET", /^\/api\/tenders\/(\d+)$/, (id) => find(id)],
  ["PUT", /^\/api\/tenders\/(\d+)\/follow-up$/, (id, _, body) => followUp(id, body)],
  ["PUT", /^\/api\/tenders\/(\d+)\/follow$/, (id) => follow(id, true)],
  ["DELETE", /^\/api\/tenders\/(\d+)\/follow$/, (id) => follow(id, false)],
  ["GET", /^\/api\/tenders\/(\d+)\/dossiers$/, (id) => MOCK_DOSSIERS[find(id).id]],
  ["GET", /^\/api\/tenders\/(\d+)\/analysis$/, (id) => find(id) && NO_ANALYSIS],
];

export async function mockApi<T>(method: string, path: string, body?: unknown): Promise<T> {
  await new Promise((resolve) => setTimeout(resolve, LATENCY_MS));
  const url = new URL(path, window.location.origin);
  for (const [routeMethod, pattern, handle] of ROUTES) {
    const match = url.pathname.match(pattern);
    if (match && routeMethod === method) {
      // A copy, as a response parsed from JSON would be.
      const result = handle(match[1], url, body);
      return (result === undefined ? undefined : structuredClone(result)) as T;
    }
  }
  throw new ApiError(501, "Fonction indisponible avec les données de démonstration.");
}
