/**
 * Simulated backend: answers the URLs of the Spring Boot API with the same
 * JSON, from the demo data. It also describes the search parameters the
 * backend will have to accept. Decisions taken by the user are kept in the
 * browser so that they survive a reload.
 */

import { ACTIVITY_LABELS, formatAmount, MARKET_TYPE_LABELS, TENDER_TAB_LABELS } from "@/lib/format";
import { REGIONS } from "@/lib/regions";
import { ApiError } from "@/services/http";
import type { Alert, AlertInput, AlertSummary } from "@/types/alert";
import type { AwardedContract, AwardSearch } from "@/types/award";
import type { TenderAnalysis } from "@/types/analysis";
import type { Page } from "@/types/common";
import type { DocumentInfo, PieceType } from "@/types/document";
import type { CollectJournal } from "@/types/settings";
import type {
  FollowUpStatus,
  TenderDetail,
  TenderStats,
  TenderSummary,
  TenderTab,
} from "@/types/tender";
import type { CurrentUser } from "@/types/user";

import { MOCK_ALERTS } from "./alerts";
import { MOCK_AWARDS } from "./awardedContracts";
import { MOCK_DOSSIERS } from "./documents";
import { SEARCH_SYNONYMS } from "./reference";
import { MOCK_TENDERS } from "./tenders";

const MOCK_USER: CurrentUser = {
  id: 1,
  email: "demo@gpefrance.eu",
  displayName: "Compte de démonstration",
  role: "ADMIN",
};

// Versioned: decisions saved for an older demo data set no longer apply.
const STORAGE_KEY = "gpe-demo-state-v2";
const LATENCY_MS = 150;

type FollowUp = Pick<TenderDetail, "followUpStatus" | "followUpComment" | "followUpBy" | "followUpAt">;

type SavedState = {
  followUps: Record<number, FollowUp>;
  followed: Record<number, boolean>;
  /** Saved searches, once the user changed them. */
  alerts?: Alert[];
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

const alerts: Alert[] = state.alerts ?? structuredClone(MOCK_ALERTS);

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
    lotCount: tender.lots.length,
    activities: tender.activities,
    distanceKm: tender.distanceKm,
    relevanceScore: tender.relevanceScore,
    followed: tender.followed,
    documentCount: MOCK_DOSSIERS[tender.id]?.[0]?.documents.length ?? 0,
    postalCode: tender.postalCode,
    result: tender.result,
    ...awardSummary(tender.id),
  };
}

/** Winners of a past consultation and the total awarded. */
function awardSummary(tenderId: number): Pick<TenderSummary, "winners" | "awardedAmount"> {
  const awards = MOCK_AWARDS.filter((award) => award.tenderId === tenderId);
  if (!awards.length) {
    return {};
  }
  const amounts = awards.map((award) => award.amount);
  return {
    winners: [...new Set(awards.map((award) => award.winner))],
    awardedAmount: amounts.includes(null) ? null : amounts.reduce<number>((sum, amount) => sum + (amount ?? 0), 0),
  };
}

/** The details, with the award notice completed from the awarded contracts. */
function toDetail(tender: TenderDetail): TenderDetail {
  const awarded = MOCK_AWARDS.filter((award) => award.tenderId === tender.id)
    .map((award) => `${award.lot ? `${award.lot} : ` : ""}${award.winner} (${formatAmount(award.amount)})`)
    .join(" ; ");
  return {
    ...tender,
    ...toSummary(tender),
    notices: tender.notices.map((notice) =>
      notice.kind === "AWARD" && !notice.details && awarded ? { ...notice, details: `Attribution — ${awarded}.` } : notice,
    ),
  };
}

// --- Search -----------------------------------------------------------------

/**
 * Views of the tenders. The tender list shows the open ones (NEW to
 * FILTERED_OUT); followed and past tenders have their own sections. A tender
 * excluded by the filter stays out of the other views until a user takes a
 * decision on it.
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
      return !filteredOut && active;
    case "DISMISSED":
      return status === "DISMISSED" && active;
    case "ARCHIVED":
      return !active;
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

function searchedText(tender: TenderDetail): string {
  return normalize(
    [
      tender.title,
      tender.reference,
      tender.externalId,
      tender.buyer,
      tender.description,
      tender.location,
      tender.postalCode,
      ...tender.marketTypes.map((type) => MARKET_TYPE_LABELS[type]),
      ...(tender.activities ?? []).map((code) => ACTIVITY_LABELS[code] ?? code),
      ...tender.cpvCodes,
      ...(tender.cpv ?? []).map((cpv) => cpv.label),
    ]
      .filter(Boolean)
      .join(" "),
  );
}

/** Expressions of several words of the trade vocabulary, the longest first. */
const EXPRESSIONS = Object.keys(SEARCH_SYNONYMS)
  .filter((term) => term.includes(" "))
  .sort((a, b) => b.length - a.length);

/**
 * Terms of a search, each one with the words that satisfy it: the
 * expressions of the trade vocabulary first, then the remaining words.
 */
function searchTerms(text: string): string[][] {
  let rest = ` ${normalize(text)} `;
  const terms: string[][] = [];
  for (const expression of EXPRESSIONS) {
    if (rest.includes(` ${expression} `)) {
      terms.push([expression, ...SEARCH_SYNONYMS[expression]]);
      rest = rest.replace(` ${expression} `, " ");
    }
  }
  for (const word of rest.split(/\s+/).filter(Boolean)) {
    terms.push([word, ...(SEARCH_SYNONYMS[word] ?? [])]);
  }
  return terms;
}

/** The word starts a word of the text: "college" finds "colleges", not "decollege". */
function startsWord(text: string, word: string): boolean {
  return new RegExp(`(^|[^a-z0-9])${word.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}`).test(text);
}

/**
 * Every term must be found in the searched fields, ignoring case and accents;
 * a synonym of the trade vocabulary counts as the term.
 */
function matchesText(tender: TenderDetail, text: string): boolean {
  const searched = searchedText(tender);
  return searchTerms(text).every((words) => words.some((word) => startsWord(searched, word)));
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
  return inTab(tender, tab) && matchesFilters(tender, params);
}

/** The filters of GET /api/tenders, without the tab. */
function matchesFilters(tender: TenderDetail, params: URLSearchParams): boolean {
  const q = params.get("q")?.trim();
  const alertId = number(params.get("alert"));
  const alert = alerts.find((candidate) => candidate.id === alertId);
  const departments = list(params.get("department"));
  const region = params.get("region");
  const city = params.get("city")?.trim();
  const maxDistanceKm = number(params.get("maxDistanceKm"));
  const activities = list(params.get("activity"));
  const marketTypes = list(params.get("marketType"));
  const year = params.get("year");
  const result = params.get("result");
  const buyer = params.get("buyer")?.trim();
  const publishedWithinDays = number(params.get("publishedWithinDays"));
  const publishedFrom =
    publishedWithinDays === null
      ? params.get("publishedFrom")
      : new Date(Date.now() - publishedWithinDays * 86_400_000).toLocaleDateString("sv-SE");
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
  const lotCount = tender.lots.length;

  return (
    (!q || matchesText(tender, q)) &&
    (alertId === null || (alert !== undefined && matchesAlert(tender, alert))) &&
    (departments.length === 0 || tender.departments.some((code) => departments.includes(code))) &&
    (!region || tender.departments.some((code) => REGIONS[region]?.includes(code))) &&
    (!city || normalize(tender.location ?? "").includes(normalize(city)) || (tender.postalCode ?? "").startsWith(city)) &&
    (maxDistanceKm === null || (tender.distanceKm != null && tender.distanceKm <= maxDistanceKm)) &&
    (activities.length === 0 || (tender.activities ?? []).some((code) => activities.includes(code))) &&
    (marketTypes.length === 0 || tender.marketTypes.some((type) => marketTypes.includes(type))) &&
    (!year || (tender.publishedAt ?? "").startsWith(year)) &&
    (!result || (tender.result?.kind ?? "UNKNOWN") === result) &&
    (!buyer || normalize(tender.buyer ?? "").includes(normalize(buyer))) &&
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

/**
 * Closest deadline first by default; missing values always last. With the
 * closest deadline first, passed deadlines come after the open ones, the most
 * recent first: they no longer call for an action.
 */
function sort(found: TenderDetail[], sortParam: string | null): TenderDetail[] {
  const key = sortParam?.replace(/^-/, "") ?? "";
  const descending = sortParam?.startsWith("-") ?? false;
  const value = SORT_VALUES[key] ?? SORT_VALUES.deadline;
  const closestDeadlineFirst = value === SORT_VALUES.deadline && !descending;
  const now = Date.now();
  const passed = (tender: TenderDetail) => (time(tender.deadline) ?? Infinity) < now;

  return [...found].sort((a, b) => {
    let direction = descending ? -1 : 1;
    if (closestDeadlineFirst) {
      if (passed(a) !== passed(b)) {
        return passed(a) ? 1 : -1;
      }
      direction = passed(a) ? -1 : 1;
    }
    const left = value(a);
    const right = value(b);
    if (left === right) {
      return b.id - a.id;
    }
    if (left === null || right === null) {
      return left === null ? 1 : -1;
    }
    return (left - right) * direction;
  });
}

/** The page asked by the page and size parameters, 20 lines by default and 100 at most. */
function paginate<T>(found: T[], params: URLSearchParams): Page<T> {
  const page = Math.max(0, number(params.get("page")) ?? 0);
  const size = Math.min(100, Math.max(1, number(params.get("size")) ?? 20));
  return {
    content: found.slice(page * size, (page + 1) * size),
    page,
    size,
    totalElements: found.length,
    totalPages: Math.ceil(found.length / size),
  };
}

function search(params: URLSearchParams): Page<TenderSummary> {
  const found = sort(
    tenders.filter((tender) => matches(tender, params)),
    params.get("sort"),
  );
  const result = paginate(found, params);
  return { ...result, content: result.content.map(toSummary) };
}

/** Number of tenders of each tab matching the search and filters. */
function counts(params: URLSearchParams): Record<TenderTab, number> {
  return Object.fromEntries(
    (Object.keys(TENDER_TAB_LABELS) as TenderTab[]).map((tab) => {
      const tabParams = new URLSearchParams(params);
      tabParams.set("tab", tab);
      return [tab, tenders.filter((tender) => matches(tender, tabParams)).length];
    }),
  ) as Record<TenderTab, number>;
}

/** Number of tenders of each status matching the search and filters, whatever the status filter. */
function statusCounts(params: URLSearchParams): Record<FollowUpStatus, number> {
  const others = new URLSearchParams(params);
  others.delete("status");
  const counts: Record<FollowUpStatus, number> = { NEW: 0, TO_STUDY: 0, RESPONDING: 0, SUBMITTED: 0, DISMISSED: 0 };
  for (const tender of tenders.filter((candidate) => matches(candidate, others))) {
    counts[tender.followUpStatus] += 1;
  }
  return counts;
}

function stats(): TenderStats {
  const open = tenders.filter((tender) => inTab(tender, "ALL"));
  const countBy = (keys: (tender: TenderDetail) => string[]) => {
    const counts: Record<string, number> = {};
    for (const tender of open) {
      for (const key of keys(tender)) {
        counts[key] = (counts[key] ?? 0) + 1;
      }
    }
    return counts;
  };
  return {
    byStatus: countBy((tender) => [tender.followUpStatus]) as Record<FollowUpStatus, number>,
    byActivity: countBy((tender) => tender.activities ?? []),
    byDepartment: countBy((tender) => tender.departments),
  };
}

// --- Awarded contracts ------------------------------------------------------

/** Filters of GET /api/awards, all combinable; an unknown amount never hides a contract. */
function matchesAward(award: AwardedContract, params: URLSearchParams): boolean {
  const q = params.get("q")?.trim();
  const text = (key: string) => normalize(params.get(key)?.trim() ?? "");
  const departments = list(params.get("department"));
  const activities = list(params.get("activity"));
  const marketTypes = list(params.get("marketType"));
  const region = params.get("region");
  const city = params.get("city")?.trim();
  const minAmount = number(params.get("minAmount"));
  const maxAmount = number(params.get("maxAmount"));
  const searched = normalize(
    [
      award.title,
      award.lot,
      award.buyer,
      award.winner,
      award.location,
      award.postalCode,
      ...award.activities.map((code) => ACTIVITY_LABELS[code] ?? code),
    ]
      .filter(Boolean)
      .join(" "),
  );
  return (
    (!q || searchTerms(q).every((words) => words.some((word) => startsWord(searched, word)))) &&
    normalize(award.winner).includes(text("winner")) &&
    normalize(award.buyer).includes(text("buyer")) &&
    (!params.get("year") || award.awardedAt.startsWith(params.get("year") ?? "")) &&
    (departments.length === 0 || (award.department !== null && departments.includes(award.department))) &&
    (!region || (award.department !== null && REGIONS[region]?.includes(award.department))) &&
    (!city || normalize(award.location ?? "").includes(normalize(city)) || (award.postalCode ?? "").startsWith(city)) &&
    (activities.length === 0 || award.activities.some((code) => activities.includes(code))) &&
    (marketTypes.length === 0 || marketTypes.includes(award.marketType)) &&
    (minAmount === null || award.amount === null || award.amount >= minAmount) &&
    (maxAmount === null || award.amount === null || award.amount <= maxAmount)
  );
}

/** The most recent first by default; "amount" and "awardedAt", "-" for descending. Unknown amounts last. */
function searchAwards(params: URLSearchParams): AwardSearch {
  const sortParam = params.get("sort") || "-awardedAt";
  const descending = sortParam.startsWith("-");
  const byAmount = sortParam.replace(/^-/, "") === "amount";
  const found = MOCK_AWARDS.filter((award) => matchesAward(award, params)).sort((a, b) => {
    if (byAmount && (a.amount === null || b.amount === null)) {
      return a.amount === b.amount ? 0 : a.amount === null ? 1 : -1;
    }
    const order = byAmount ? (a.amount ?? 0) - (b.amount ?? 0) : a.awardedAt.localeCompare(b.awardedAt);
    return descending ? -order : order;
  });
  return {
    ...paginate(found, params),
    winnerCount: new Set(found.map((award) => award.winner)).size,
    totalAmount: found.reduce((sum, award) => sum + (award.amount ?? 0), 0),
  };
}

// --- Saved searches ---------------------------------------------------------

/**
 * Criteria of a saved search, all combined. Its keywords are alternatives:
 * one of them is enough, with the synonyms of the trade.
 */
function matchesAlert(tender: TenderDetail, alert: Alert): boolean {
  const params = new URLSearchParams();
  const set = (key: string, value: string | number | null) => {
    if (value !== null && value !== "") {
      params.set(key, String(value));
    }
  };
  set("region", alert.region);
  set("department", alert.departments.join(","));
  set("marketType", alert.marketTypes.join(","));
  set("activity", alert.activities.join(","));
  set("minAmount", alert.minAmount);
  set("maxAmount", alert.maxAmount);
  return (
    matchesFilters(tender, params) &&
    (alert.keywords.length === 0 || alert.keywords.some((keyword) => matchesText(tender, keyword))) &&
    (alert.cpvPrefixes.length === 0 ||
      tender.cpvCodes.some((code) => alert.cpvPrefixes.some((prefix) => code.startsWith(prefix))))
  );
}

/** Results among the open consultations; those published after the day of the last visit are new. */
function alertSummary(alert: Alert): AlertSummary {
  const found = tenders.filter((tender) => inTab(tender, "ALL") && matchesAlert(tender, alert));
  const lastVisit = alert.lastViewedAt ? new Date(alert.lastViewedAt).toLocaleDateString("sv-SE") : "";
  return {
    ...alert,
    matchCount: found.length,
    newCount: found.filter((tender) => (tender.publishedAt ?? "") > lastVisit).length,
  };
}

function findAlert(id: string): Alert {
  const alert = alerts.find((candidate) => candidate.id === Number(id));
  if (!alert) {
    throw new ApiError(404, "Élément introuvable.");
  }
  return alert;
}

function alertInput(body: unknown): AlertInput {
  const input = body as AlertInput;
  if (!input.name?.trim()) {
    throw new ApiError(400, "Le nom de la veille est obligatoire.");
  }
  return { ...input, name: input.name.trim() };
}

function saveAlerts() {
  state.alerts = alerts;
  saveState();
}

function createAlert(body: unknown): AlertSummary {
  const alert: Alert = {
    ...alertInput(body),
    id: Math.max(0, ...alerts.map((candidate) => candidate.id)) + 1,
    createdAt: new Date().toISOString(),
    lastViewedAt: null,
  };
  alerts.push(alert);
  saveAlerts();
  return alertSummary(alert);
}

function updateAlert(id: string, body: unknown): AlertSummary {
  const alert = Object.assign(findAlert(id), alertInput(body));
  saveAlerts();
  return alertSummary(alert);
}

function deleteAlert(id: string): undefined {
  alerts.splice(alerts.indexOf(findAlert(id)), 1);
  saveAlerts();
  return undefined;
}

function markAlertViewed(id: string): AlertSummary {
  const alert = findAlert(id);
  alert.lastViewedAt = new Date().toISOString();
  saveAlerts();
  return alertSummary(alert);
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

/** Correction of the type of a piece; kept until the next reload. */
function changePieceType(id: string, body: unknown): DocumentInfo {
  const document = Object.values(MOCK_DOSSIERS)
    .flat()
    .flatMap((dossier) => dossier.documents)
    .find((candidate) => candidate.id === Number(id));
  if (!document) {
    throw new ApiError(404, "Élément introuvable.");
  }
  document.pieceType = (body as { pieceType: PieceType }).pieceType;
  document.pieceTypeCorrected = true;
  return document;
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
  ["GET", /^\/api\/tenders\/counts$/, (_, url) => counts(url.searchParams)],
  ["GET", /^\/api\/tenders\/stats$/, () => stats()],
  ["GET", /^\/api\/tenders\/status-counts$/, (_, url) => statusCounts(url.searchParams)],
  ["GET", /^\/api\/tenders\/(\d+)$/, (id) => toDetail(find(id))],
  ["PUT", /^\/api\/tenders\/(\d+)\/follow-up$/, (id, _, body) => toDetail(followUp(id, body))],
  ["PUT", /^\/api\/tenders\/(\d+)\/follow$/, (id) => follow(id, true)],
  ["DELETE", /^\/api\/tenders\/(\d+)\/follow$/, (id) => follow(id, false)],
  ["GET", /^\/api\/tenders\/(\d+)\/dossiers$/, (id) => MOCK_DOSSIERS[find(id).id] ?? []],
  ["GET", /^\/api\/tenders\/(\d+)\/analysis$/, (id) => find(id) && NO_ANALYSIS],
  ["GET", /^\/api\/tenders\/(\d+)\/awards$/, (id) => MOCK_AWARDS.filter((award) => award.tenderId === find(id).id)],
  ["PUT", /^\/api\/documents\/(\d+)\/piece-type$/, (id, _, body) => changePieceType(id, body)],
  ["GET", /^\/api\/awards$/, (_, url) => searchAwards(url.searchParams)],
  ["GET", /^\/api\/alerts$/, () => alerts.map(alertSummary)],
  ["POST", /^\/api\/alerts$/, (_, __, body) => createAlert(body)],
  ["PUT", /^\/api\/alerts\/(\d+)$/, (id, _, body) => updateAlert(id, body)],
  ["DELETE", /^\/api\/alerts\/(\d+)$/, (id) => deleteAlert(id)],
  ["POST", /^\/api\/alerts\/(\d+)\/viewed$/, (id) => markAlertViewed(id)],
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
