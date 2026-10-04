"use client";

import Link from "next/link";

import { CollectStatus } from "@/components/CollectStatus";
import { DeadlineBadge } from "@/components/DeadlineBadge";
import {
  ACTIVITY_LABELS,
  formatDate,
  formatDateTime,
  FOLLOW_UP_LABELS,
  TENDER_TAB_LABELS,
} from "@/lib/format";
import { useLoader } from "@/lib/useLoader";
import { getTenderCounts, getTenders, getTenderStats } from "@/services/tenderApi";
import type { FollowUpStatus, TenderSummary, TenderTab } from "@/types/tender";

/** Open tenders whose deadline is within a week and on which the team may still answer. */
const DEADLINES_QUERY = "tab=ALL&status=NEW,TO_STUDY,RESPONDING&maxDaysLeft=7";
const LATEST_QUERY = "tab=NEW&sort=-published";
const LIST_SIZE = 8;

type BreakdownProps = {
  title: string;
  counts: Record<string, number>;
  label: (key: string) => string;
};

/** Counts sorted from the largest, as a plain table. */
function Breakdown({ title, counts, label }: BreakdownProps) {
  const rows = Object.entries(counts).sort(([, a], [, b]) => b - a);
  return (
    <section className="card">
      <h2>{title}</h2>
      {rows.length === 0 ? (
        <p className="muted">Aucun appel d&apos;offres en cours.</p>
      ) : (
        <table>
          <tbody>
            {rows.map(([key, count]) => (
              <tr key={key}>
                <td>{label(key)}</td>
                <td className="number">{count}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}

function TenderLink({ tender }: { tender: TenderSummary }) {
  return (
    <>
      <Link href={`/tenders/${tender.id}`}>{tender.title}</Link>
      <div className="small muted">{tender.buyer ?? "Acheteur non indiqué"}</div>
    </>
  );
}

export default function DashboardPage() {
  const counts = useLoader(() => getTenderCounts(), "counts");
  const deadlines = useLoader(() => getTenders(`${DEADLINES_QUERY}&size=${LIST_SIZE}`), "deadlines");
  const latest = useLoader(() => getTenders(`${LATEST_QUERY}&size=${LIST_SIZE}`), "latest");
  const stats = useLoader(() => getTenderStats(), "stats");
  const error = counts.error ?? deadlines.error ?? latest.error ?? stats.error;

  const figures: { label: string; value: number | undefined; href: string }[] = [
    ...(["NEW", "TO_PROCESS", "FOLLOWED"] as TenderTab[]).map((tab) => ({
      label: TENDER_TAB_LABELS[tab],
      value: counts.data?.[tab],
      href: tab === "FOLLOWED" ? "/follows" : `/tenders?tab=${tab}`,
    })),
    { label: "Échéances dans les 7 jours", value: deadlines.data?.totalElements, href: `/tenders?${DEADLINES_QUERY}` },
    { label: "Hors critères à vérifier", value: counts.data?.FILTERED_OUT, href: "/tenders?tab=FILTERED_OUT" },
  ];

  return (
    <>
      <h1>Tableau de bord</h1>
      <CollectStatus />
      {error && <p className="banner error">{error}</p>}

      <div className="stats">
        {figures.map((figure) => (
          <Link key={figure.label} href={figure.href} className="card stat">
            <strong>{figure.value ?? "–"}</strong>
            <span>{figure.label}</span>
          </Link>
        ))}
      </div>

      <div className="dashboard-grid">
        <section className="card">
          <h2>Échéances dans les 7 jours</h2>
          {deadlines.data?.content.length === 0 && <p className="muted">Aucune échéance dans les 7 jours.</p>}
          {deadlines.data && deadlines.data.content.length > 0 && (
            <table>
              <tbody>
                {deadlines.data.content.map((tender) => (
                  <tr key={tender.id}>
                    <td>
                      <TenderLink tender={tender} />
                    </td>
                    <td className="nowrap">
                      {formatDateTime(tender.deadline)}
                      <div>
                        <DeadlineBadge deadline={tender.deadline} />
                      </div>
                    </td>
                    <td className="nowrap">{FOLLOW_UP_LABELS[tender.followUpStatus]}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
          <p>
            <Link href={`/tenders?${DEADLINES_QUERY}`}>Voir toutes les échéances proches</Link>
          </p>
        </section>

        <section className="card">
          <h2>Derniers appels d&apos;offres retenus</h2>
          {latest.data?.content.length === 0 && <p className="muted">Aucun nouvel appel d&apos;offres.</p>}
          {latest.data && latest.data.content.length > 0 && (
            <table>
              <tbody>
                {latest.data.content.map((tender) => (
                  <tr key={tender.id}>
                    <td>
                      <TenderLink tender={tender} />
                    </td>
                    <td className="nowrap">{formatDate(tender.publishedAt)}</td>
                    <td className="nowrap">{tender.relevanceScore != null ? `${tender.relevanceScore} %` : "–"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
          <p>
            <Link href={`/tenders?${LATEST_QUERY}`}>Voir tous les nouveaux appels d&apos;offres</Link>
          </p>
        </section>
      </div>

      {stats.data && (
        <div className="dashboard-grid">
          <Breakdown
            title="Appels d'offres en cours par statut"
            counts={stats.data.byStatus}
            label={(key) => FOLLOW_UP_LABELS[key as FollowUpStatus]}
          />
          <Breakdown
            title="Par type de travaux"
            counts={stats.data.byActivity}
            label={(key) => ACTIVITY_LABELS[key] ?? key}
          />
          <Breakdown title="Par département" counts={stats.data.byDepartment} label={(key) => key} />
        </div>
      )}
    </>
  );
}
