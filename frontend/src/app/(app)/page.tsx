"use client";

import Link from "next/link";

import { useAuth } from "@/components/AuthProvider";
import { DeadlineBadge } from "@/components/DeadlineBadge";
import { Icon } from "@/components/Icon";
import { formatDate } from "@/lib/format";
import { tenderHref } from "@/lib/navigation";
import { useLoader } from "@/lib/useLoader";
import { getRecentEstimates } from "@/services/estimateApi";
import { getMemoirs } from "@/services/memoirApi";
import { getResponses } from "@/services/responseApi";
import { getTenderCounts, getTenders } from "@/services/tenderApi";
import type { ListedEstimate } from "@/types/estimate";
import type { Memoir } from "@/types/memoir";
import type { ResponseSummary } from "@/types/response";
import type { TenderSummary } from "@/types/tender";

/** Open consultations whose deadline is within a week and on which the team may still answer. */
const DEADLINES_QUERY = "tab=ALL&status=NEW,TO_STUDY,RESPONDING&maxDaysLeft=7";
/** Consultations matching the criteria on which nobody decided yet. */
const TO_DECIDE_QUERY = "tab=NEW&sort=-published";
const LIST_SIZE = 5;

type Badge = { label: string; className: string };

/** What is left to do on a consultation the team answers: estimate, technical memoir, pieces. */
function progress(
  tender: TenderSummary,
  estimates: ListedEstimate[],
  memoirs: Memoir[],
  responses: ResponseSummary[],
): Badge[] {
  if (tender.followUpStatus !== "RESPONDING") {
    return [{ label: "Réponse à décider", className: "badge urgent" }];
  }
  const estimate = estimates.find((listed) => listed.tenderId === tender.id && listed.estimate.status !== "SUPERSEDED");
  const memoir = memoirs.find((item) => item.tenderId === tender.id);
  const response = responses.find((item) => item.tenderId === tender.id);
  return [
    !estimate
      ? { label: "Chiffrage à faire", className: "badge urgent" }
      : estimate.estimate.status === "VALIDATED"
        ? { label: "Chiffrage validé", className: "badge verified" }
        : { label: "Chiffrage en brouillon", className: "badge" },
    !memoir
      ? { label: "Mémoire à faire", className: "badge urgent" }
      : memoir.status === "DONE"
        ? { label: "Mémoire terminé", className: "badge verified" }
        : { label: "Mémoire en cours", className: "badge" },
    !response || response.total === 0
      ? { label: "Pièces à préparer", className: "badge urgent" }
      : {
          label: `Pièces ${response.ready}/${response.total}`,
          className: response.ready === response.total ? "badge verified" : "badge",
        },
  ];
}

type Work = { key: string; kind: string; title: string; href: string; status: string; updatedAt: string };

/** What the user changed last and did not finish: draft estimates, memoirs not done. */
function myWork(user: string, estimates: ListedEstimate[], memoirs: Memoir[]): Work[] {
  const drafts: Work[] = estimates
    .filter((listed) => listed.estimate.status === "DRAFT" && listed.estimate.updatedBy === user)
    .map((listed) => ({
      key: `e${listed.estimate.id}`,
      kind: "Chiffrage",
      title: listed.tenderTitle ?? `Chiffrage v${listed.estimate.version}`,
      href: `/estimates/${listed.estimate.id}`,
      status: listed.estimate.incompleteItems > 0 ? `${listed.estimate.incompleteItems} postes sans prix` : "Brouillon",
      updatedAt: listed.estimate.updatedAt,
    }));
  const writing: Work[] = memoirs
    .filter((memoir) => memoir.status !== "DONE" && (memoir.updatedBy === user || memoir.createdBy === user))
    .map((memoir) => ({
      key: `m${memoir.id}`,
      kind: "Mémoire",
      title: memoir.tenderTitle ?? memoir.title,
      href: `/memoirs/${memoir.id}`,
      status: "En cours",
      updatedAt: memoir.updatedAt,
    }));
  return [...drafts, ...writing].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt)).slice(0, LIST_SIZE);
}

/** What the user has to do today: answers due soon, consultations to decide, own work in progress. */
export default function DashboardPage() {
  const { user } = useAuth();
  const counts = useLoader(() => getTenderCounts(), "counts");
  const deadlines = useLoader(() => getTenders(`${DEADLINES_QUERY}&size=${LIST_SIZE}`), "deadlines");
  const toDecide = useLoader(() => getTenders(`${TO_DECIDE_QUERY}&size=${LIST_SIZE}`), "to-decide");
  const estimates = useLoader(() => getRecentEstimates(), "estimates");
  const memoirs = useLoader(() => getMemoirs(), "memoirs");
  const responses = useLoader(() => getResponses(), "responses");
  const error = [counts, deadlines, toDecide, estimates, memoirs, responses].find((loader) => loader.error)?.error;
  const work = myWork(user.displayName, estimates.data ?? [], memoirs.data ?? []);

  const figures = [
    {
      value: deadlines.data?.totalElements,
      label: "à rendre sous 7 jours",
      href: `/tenders?${DEADLINES_QUERY}`,
      icon: "clock" as const,
      tone: deadlines.data?.totalElements ? "warning" : "muted",
    },
    {
      value: counts.data?.NEW,
      label: "à trancher",
      href: `/tenders?${TO_DECIDE_QUERY}`,
      icon: "clipboard" as const,
      tone: counts.data?.NEW ? "accent" : "muted",
    },
    { value: counts.data?.FOLLOWED, label: "suivies", href: "/follows", icon: "star" as const, tone: "accent" },
  ];

  return (
    <>
      <h1>Bonjour {user.displayName.split(" ")[0]}</h1>
      {error && <p className="banner error">{error}</p>}

      <div className="stats">
        {figures.map((figure) => (
          <Link key={figure.href} href={figure.href} className={`card stat tone-${figure.tone}`}>
            <span className="stat-head">
              <Icon name={figure.icon} size={18} />
              <strong>{figure.value ?? "–"}</strong>
            </span>
            <span>{figure.label}</span>
          </Link>
        ))}
      </div>

      <section className="card">
        <h2>À rendre bientôt</h2>
        {deadlines.data?.content.length === 0 && <p className="muted">Rien à rendre dans les 7 jours.</p>}
        {deadlines.data && deadlines.data.content.length > 0 && (
          <table>
            <tbody>
              {deadlines.data.content.map((tender) => (
                <tr key={tender.id}>
                  <td>
                    <Link href={tenderHref(tender.id, "/")}>{tender.title}</Link>
                  </td>
                  <td>
                    <span className="badges">
                      {progress(tender, estimates.data ?? [], memoirs.data ?? [], responses.data ?? []).map((badge) => (
                        <span key={badge.label} className={badge.className}>
                          {badge.label}
                        </span>
                      ))}
                    </span>
                  </td>
                  <td className="nowrap">
                    <DeadlineBadge deadline={tender.deadline} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        {(deadlines.data?.totalElements ?? 0) > LIST_SIZE && (
          <p>
            <Link href={`/tenders?${DEADLINES_QUERY}`}>Tout voir</Link>
          </p>
        )}
      </section>

      <div className="dashboard-grid">
        <section className="card">
          <h2>À trancher</h2>
          {toDecide.data?.content.length === 0 && <p className="muted">Rien à trancher.</p>}
          {toDecide.data && toDecide.data.content.length > 0 && (
            <table>
              <tbody>
                {toDecide.data.content.map((tender) => (
                  <tr key={tender.id}>
                    <td>
                      <Link href={tenderHref(tender.id, "/")}>{tender.title}</Link>
                      {tender.buyer && <div className="small muted">{tender.buyer}</div>}
                    </td>
                    <td className="nowrap">
                      <DeadlineBadge deadline={tender.deadline} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
          {(toDecide.data?.totalElements ?? 0) > LIST_SIZE && (
            <p>
              <Link href={`/tenders?${TO_DECIDE_QUERY}`}>Tout voir</Link>
            </p>
          )}
        </section>

        <section className="card">
          <h2>Mon travail en cours</h2>
          {estimates.data && memoirs.data && work.length === 0 && <p className="muted">Rien en cours.</p>}
          {work.length > 0 && (
            <table>
              <tbody>
                {work.map((item) => (
                  <tr key={item.key}>
                    <td>
                      <Link href={item.href}>{item.title}</Link>
                      <div className="small muted">
                        {item.kind} · {item.status}
                      </div>
                    </td>
                    <td className="nowrap muted">{formatDate(item.updatedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>
      </div>
    </>
  );
}
