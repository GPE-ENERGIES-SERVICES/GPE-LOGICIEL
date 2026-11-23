"use client";

import Link from "next/link";
import { useState } from "react";

import { Icon } from "@/components/Icon";
import { Pager } from "@/components/Pager";
import { AI_SERVICE_KINDS, formatCost, serviceLabel, serviceStep } from "@/lib/aiServices";
import { formatDateTime } from "@/lib/format";
import { useLoader } from "@/lib/useLoader";
import { getAiCalls, getAiUsage, getAiUsageByTender } from "@/services/aiUsageApi";
import type { AiCallStatus, AiUsageOverview, ServiceUse } from "@/types/aiUsage";

const STATUS: Record<AiCallStatus, { label: string; badge: string }> = {
  RUNNING: { label: "En cours", badge: "badge info" },
  DONE: { label: "Terminé", badge: "badge success" },
  FAILED: { label: "Erreur", badge: "badge danger" },
  INTERRUPTED: { label: "Interrompu", badge: "badge muted-badge" },
};

const HEALTH = {
  GREEN: { label: "Traitements IA normaux", badge: "badge success" },
  ORANGE: { label: "Traitements IA à surveiller", badge: "badge warning" },
  RED: { label: "Erreurs IA dans les 24 h", badge: "badge danger" },
};

/** The first day of the month, as the start of a period. */
function monthStart(): string {
  const now = new Date();
  return new Date(now.getFullYear(), now.getMonth(), 1).toISOString();
}

/** The budget of the month and how much of it is spent, as a gauge. */
export function AiBudgetCard({ overview }: { overview: AiUsageOverview | null | undefined }) {
  const month = overview?.month;
  const budget = month?.budget ?? null;
  const ratio = month && budget ? Math.min(1, month.spent / budget) : null;
  const tone = ratio === null ? "info" : ratio >= 0.9 ? "danger" : ratio >= 0.6 ? "warning" : "success";
  return (
    <div className={`card stat ai-budget tone-${tone}`}>
      <span className="stat-head">
        <Icon name="tag" size={18} />
        <strong>{month ? (month.pricesSet ? formatCost(month.spent) : "—") : "–"}</strong>
      </span>
      <span>
        {!month || month.pricesSet
          ? `dépensés en IA ce mois${budget !== null ? ` sur ${formatCost(budget)}` : ""}`
          : "prix des tokens non configurés"}
      </span>
      {ratio !== null && (
        <span
          className="gauge"
          role="meter"
          aria-label="Budget IA consommé"
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={Math.round(ratio * 100)}
        >
          <span style={{ width: `${Math.max(2, ratio * 100)}%` }} />
        </span>
      )}
    </div>
  );
}

/** The processing of the AI now: running, waiting, failed; the errors on request. */
export function AiHealth({ overview, onShowErrors }: { overview: AiUsageOverview | null | undefined; onShowErrors: () => void }) {
  if (!overview) {
    return null;
  }
  const health = overview.health;
  return (
    <div className="ai-health">
      <span className={HEALTH[health.state].badge}>{HEALTH[health.state].label}</span>
      <span>
        <strong>{health.running}</strong> en cours
      </span>
      <span>
        <strong>{health.waiting}</strong> en attente
      </span>
      <span>
        <strong>{health.doneWeek}</strong> terminés sur 7 j
      </span>
      {health.failedWeek > 0 ? (
        <button type="button" className="link-button danger" onClick={onShowErrors}>
          {health.failedWeek} erreur{health.failedWeek > 1 ? "s" : ""} sur 7 j
        </button>
      ) : (
        <span>0 erreur sur 7 j</span>
      )}
    </div>
  );
}

/** The services over a period: what each cost, how often it ran, its part of the total. */
function ByService({ overview }: { overview: AiUsageOverview }) {
  const [period, setPeriod] = useState<"month" | "all">("month");
  const [since] = useState(monthStart);
  const loaded = useLoader(() => (period === "month" ? getAiUsage(since) : getAiUsage()), `ai-services-${period}`);
  const data = loaded.data ?? (period === "month" ? null : overview);
  return (
    <>
      <div className="list-toolbar">
        <p className="result-count">
          {data ? `${data.calls} appel${data.calls > 1 ? "s" : ""} · ${formatCost(data.cost)}` : "Chargement…"}
        </p>
        <label>
          Période
          <select value={period} onChange={(e) => setPeriod(e.target.value as "month" | "all")}>
            <option value="month">Ce mois</option>
            <option value="all">Depuis le début</option>
          </select>
        </label>
      </div>
      {loaded.error && <p className="banner error">{loaded.error}</p>}
      {data && data.services.length === 0 && <p className="page-message">Aucun appel à l&apos;IA sur la période.</p>}
      {data && data.services.length > 0 && (
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>Service</th>
                <th>Étape</th>
                <th className="number">Exécutions</th>
                <th className="number">Erreurs</th>
                <th className="number">Coût total</th>
                <th className="number">Coût moyen</th>
                <th>Part du total</th>
              </tr>
            </thead>
            <tbody>
              {data.services.map((use: ServiceUse) => (
                <tr key={use.kind}>
                  <td>{serviceLabel(use.kind)}</td>
                  <td className="muted nowrap">{serviceStep(use.kind)}</td>
                  <td className="number">{use.calls}</td>
                  <td className="number">{use.failed > 0 ? <span className="badge danger">{use.failed}</span> : 0}</td>
                  <td className="number">{formatCost(use.cost)}</td>
                  <td className="number">{formatCost(use.averageCost)}</td>
                  <td className="share-cell">
                    {use.share !== null && (
                      <span className="share">
                        <span className="gauge small">
                          <span style={{ width: `${Math.max(2, use.share * 100)}%` }} />
                        </span>
                        <span className="nowrap">{Math.round(use.share * 100)} %</span>
                      </span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <p className="hint">Coût moyen = coût total du service / nombre d&apos;exécutions (erreurs comprises : elles sont facturées).</p>
    </>
  );
}

/** Each tender and what its whole processing cost, step by step. */
function ByTender({ overview }: { overview: AiUsageOverview }) {
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const loaded = useLoader(() => getAiUsageByTender(page, size), `ai-tenders-${page}-${size}`);
  const per = overview.perTender;
  return (
    <>
      <p className="hint">
        Coût complet d&apos;un AO = somme de tous les appels IA qui lui sont rattachés (analyse du DCE, choix techniques,
        chiffrage, mémoire, dossier de réponse), chacun compté une fois. La moyenne porte sur les AO terminés (clos, date
        limite passée, réponse envoyée ou écartés) ; celle des AO en cours est provisoire. Les appels sans AO (
        {per.unattributedCalls}, {formatCost(per.unattributedCost)}) comptent dans le total mais dans aucun AO.
      </p>
      {loaded.error && <p className="banner error">{loaded.error}</p>}
      {loaded.data && loaded.data.content.length === 0 && (
        <p className="page-message">Aucun appel à l&apos;IA rattaché à un appel d&apos;offres.</p>
      )}
      {loaded.data && loaded.data.content.length > 0 && (
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>Appel d&apos;offres</th>
                <th>Dossier</th>
                <th>Détail par étape</th>
                <th className="number">Appels</th>
                <th className="number">Coût complet</th>
              </tr>
            </thead>
            <tbody>
              {loaded.data.content.map((tender) => {
                const steps = new Map<string, number>();
                for (const use of tender.services) {
                  steps.set(serviceStep(use.kind), (steps.get(serviceStep(use.kind)) ?? 0) + (use.cost ?? 0));
                }
                return (
                  <tr key={tender.tenderId}>
                    <td className="title-cell">
                      <Link href={`/tenders/${tender.tenderId}`} title={tender.title}>
                        {tender.title}
                      </Link>
                    </td>
                    <td>
                      <span className={tender.finished ? "badge success" : "badge info"}>
                        {tender.finished ? "Terminé" : "En cours"}
                      </span>
                    </td>
                    <td>
                      <span className="step-costs">
                        {[...steps].map(([step, cost]) => (
                          <span key={step} className="nowrap">
                            {step} <strong>{formatCost(cost)}</strong>
                          </span>
                        ))}
                      </span>
                    </td>
                    <td className="number">{tender.calls}</td>
                    <td className="number">
                      <strong>{formatCost(tender.cost)}</strong>
                      {!tender.finished && <div className="small muted">provisoire</div>}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
      {loaded.data && (
        <Pager
          page={page}
          total={loaded.data.totalElements}
          size={size}
          label="Pages des appels d'offres"
          onChange={setPage}
          onSizeChange={(next) => {
            setSize(next);
            setPage(0);
          }}
        />
      )}
    </>
  );
}

type Filters = { kind: string; status: string; tenderId: string; from: string; to: string; sort: string };

/** Every call, filtered, the latest or the most expensive first. */
function History({ initialStatus }: { initialStatus: string }) {
  const [filters, setFilters] = useState<Filters>({ kind: "", status: initialStatus, tenderId: "", from: "", to: "", sort: "date" });
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const params = new URLSearchParams({ page: String(page), size: String(size), sort: filters.sort });
  if (filters.kind) params.set("kind", filters.kind);
  if (filters.status) params.set("status", filters.status);
  if (/^\d+$/.test(filters.tenderId)) params.set("tenderId", filters.tenderId);
  if (filters.from) params.set("from", new Date(`${filters.from}T00:00:00`).toISOString());
  if (filters.to) params.set("to", new Date(`${filters.to}T23:59:59.999`).toISOString());
  const loaded = useLoader(() => getAiCalls(params), `ai-calls-${params}`);
  // A filter changes the results: back to the first page.
  const change = (key: keyof Filters, value: string) => {
    setFilters((current) => ({ ...current, [key]: value }));
    setPage(0);
  };
  return (
    <>
      <div className="filter-fields history-filters">
        <label>
          Service
          <select value={filters.kind} onChange={(e) => change("kind", e.target.value)}>
            <option value="">Tous</option>
            {AI_SERVICE_KINDS.map((kind) => (
              <option key={kind} value={kind}>
                {serviceLabel(kind)}
              </option>
            ))}
          </select>
        </label>
        <label>
          Statut
          <select value={filters.status} onChange={(e) => change("status", e.target.value)}>
            <option value="">Tous</option>
            {(Object.keys(STATUS) as AiCallStatus[]).map((status) => (
              <option key={status} value={status}>
                {STATUS[status].label}
              </option>
            ))}
          </select>
        </label>
        <label>
          N° d&apos;AO
          <input inputMode="numeric" value={filters.tenderId} onChange={(e) => change("tenderId", e.target.value.trim())} />
        </label>
        <label>
          Du
          <input type="date" value={filters.from} onChange={(e) => change("from", e.target.value)} />
        </label>
        <label>
          Au
          <input type="date" value={filters.to} onChange={(e) => change("to", e.target.value)} />
        </label>
        <label>
          Trier
          <select value={filters.sort} onChange={(e) => change("sort", e.target.value)}>
            <option value="date">Les plus récents</option>
            <option value="cost">Les plus coûteux</option>
          </select>
        </label>
      </div>
      {loaded.error && <p className="banner error">{loaded.error}</p>}
      {loaded.data && loaded.data.content.length === 0 && <p className="page-message">Aucun appel ne correspond.</p>}
      {loaded.data && loaded.data.content.length > 0 && (
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>Date</th>
                <th>Service</th>
                <th>Appel d&apos;offres</th>
                <th>Statut</th>
                <th>Modèle</th>
                <th className="number">Tokens entrée / sortie</th>
                <th className="number">Durée</th>
                <th className="number">Coût</th>
              </tr>
            </thead>
            <tbody>
              {loaded.data.content.map((call) => (
                <tr key={call.id}>
                  <td className="nowrap">{formatDateTime(call.createdAt)}</td>
                  <td>
                    {serviceLabel(call.kind)}
                    {call.createdBy && <div className="small muted">{call.createdBy}</div>}
                  </td>
                  <td className="title-cell">
                    {call.tenderId ? (
                      <Link href={`/tenders/${call.tenderId}`} title={call.tenderTitle ?? undefined}>
                        {call.tenderTitle ?? `N° ${call.tenderId}`}
                      </Link>
                    ) : (
                      <span className="muted">Sans AO</span>
                    )}
                  </td>
                  <td>
                    <span className={STATUS[call.status].badge}>{STATUS[call.status].label}</span>
                    {call.errorKind && <div className="small muted">{call.errorKind}</div>}
                  </td>
                  <td className="small muted nowrap">{call.model ?? "—"}</td>
                  <td className="number">
                    {call.inputTokens.toLocaleString("fr-FR")} / {call.outputTokens.toLocaleString("fr-FR")}
                  </td>
                  <td className="number">{call.durationMs === null ? "—" : `${Math.round(call.durationMs / 1000)} s`}</td>
                  <td className="number">
                    {formatCost(call.cost)}
                    {call.cost !== null && !call.costConfirmed && <div className="small muted">estimé</div>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {loaded.data && (
        <Pager
          page={page}
          total={loaded.data.totalElements}
          size={size}
          label="Pages de l'historique"
          onChange={setPage}
          onSizeChange={(next) => {
            setSize(next);
            setPage(0);
          }}
        />
      )}
    </>
  );
}

type View = "services" | "tenders" | "history";

/** The consumption of the AI: by service, by tender, call by call. */
export function AiUsagePanel({
  overview,
  view,
  historyStatus,
  onView,
}: {
  overview: AiUsageOverview;
  view: View;
  historyStatus: string;
  onView: (view: View) => void;
}) {
  const tabs: { id: View; label: string }[] = [
    { id: "services", label: "Par service" },
    { id: "tenders", label: "Par appel d'offres" },
    { id: "history", label: "Historique" },
  ];
  return (
    <section className="card" id="consommation-ia">
      <h2>Consommation IA</h2>
      <div className="tabs" role="tablist" aria-label="Consommation IA">
        {tabs.map((tab) => (
          <button
            key={tab.id}
            type="button"
            role="tab"
            aria-selected={view === tab.id}
            className={view === tab.id ? "tab active" : "tab"}
            onClick={() => onView(tab.id)}
          >
            {tab.label}
          </button>
        ))}
      </div>
      {view === "services" && <ByService overview={overview} />}
      {view === "tenders" && <ByTender overview={overview} />}
      {view === "history" && <History key={historyStatus} initialStatus={historyStatus} />}
    </section>
  );
}
