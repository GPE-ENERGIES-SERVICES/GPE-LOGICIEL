"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";

import { useAuth } from "@/components/AuthProvider";
import { CollectStatus } from "@/components/CollectStatus";
import { DeadlineBadge } from "@/components/DeadlineBadge";
import { formatAmount, formatDate, formatDateTime, FOLLOW_UP_LABELS } from "@/lib/format";
import { alertResultsHref, tenderHref } from "@/lib/navigation";
import { useLoader } from "@/lib/useLoader";
import { getAlerts, markAlertViewed } from "@/services/alertApi";
import { getAwardedContracts } from "@/services/awardApi";
import { available } from "@/services/http";
import { getStatusCounts, getTenderCounts, getTenders } from "@/services/tenderApi";
import type { TenderSummary } from "@/types/tender";

/** Consultations published since yesterday. */
const RECENT_QUERY = "publishedWithinDays=1";
/** Open consultations whose deadline is within a week and on which the team may still answer. */
const DEADLINES_QUERY = "tab=ALL&status=NEW,TO_STUDY,RESPONDING&maxDaysLeft=7";
const OPPORTUNITIES_QUERY = "tab=NEW&sort=-published";
const LIST_SIZE = 8;

function TenderLink({ tender }: { tender: TenderSummary }) {
  return (
    <>
      <Link href={tenderHref(tender.id, "/")}>{tender.title}</Link>
      <div className="small muted">{tender.buyer ?? "Acheteur non indiqué"}</div>
    </>
  );
}

/** What calls for an action today, then the latest news of the market. */
export default function DashboardPage() {
  const router = useRouter();
  const { user } = useAuth();
  const recent = useLoader(() => getTenderCounts(RECENT_QUERY), "recent");
  const counts = useLoader(() => getTenderCounts(), "counts");
  const statuses = useLoader(() => getStatusCounts("tab=ALL"), "statuses");
  const deadlines = useLoader(() => getTenders(`${DEADLINES_QUERY}&size=${LIST_SIZE}`), "deadlines");
  const opportunities = useLoader(() => getTenders(`${OPPORTUNITIES_QUERY}&size=${LIST_SIZE}`), "opportunities");
  const alerts = useLoader(() => (available("alerts") ? getAlerts() : Promise.resolve([])), "alerts");
  const awards = useLoader(() => (available("awards") ? getAwardedContracts("size=5") : Promise.resolve(null)), "awards");
  const error = [recent, counts, statuses, deadlines, opportunities, alerts, awards].find((loader) => loader.error)?.error;

  // New consultations: those matching the company's criteria and those excluded by them.
  const newCount = recent.data ? recent.data.ALL + recent.data.FILTERED_OUT : undefined;
  const activeAlerts = alerts.data?.filter((alert) => alert.active) ?? [];
  const today = new Date().toLocaleDateString("fr-FR", { day: "numeric", month: "long", year: "numeric" });

  const figures = [
    {
      value: newCount,
      label: newCount === 1 ? "nouvelle opportunité depuis hier" : "nouvelles opportunités depuis hier",
      detail: recent.data && `dont ${recent.data.ALL} correspondant à vos critères`,
      href: `/tenders?tab=ALL&${RECENT_QUERY}`,
    },
    { value: statuses.data?.TO_STUDY, label: "à analyser", href: "/tenders?tab=TO_PROCESS&status=TO_STUDY" },
    {
      value: deadlines.data?.totalElements,
      label: "échéances dans les 7 prochains jours",
      href: `/tenders?${DEADLINES_QUERY}`,
    },
    { value: counts.data?.FOLLOWED, label: "consultations suivies", href: "/follows" },
    { value: counts.data?.FILTERED_OUT, label: "hors critères à vérifier", href: "/tenders?tab=FILTERED_OUT" },
  ];

  const openAlert = async (alertId: number) => {
    try {
      await markAlertViewed(alertId);
    } catch {
      // the results are shown all the same
    }
    router.push(alertResultsHref(alertId));
  };

  return (
    <>
      <h1>Bonjour {user.displayName.split(" ")[0]}</h1>
      <p className="muted">Voici la situation au {today}.</p>
      <CollectStatus />
      {error && <p className="banner error">{error}</p>}

      <h2>Aujourd&apos;hui</h2>
      <div className="stats">
        {figures.map((figure) => (
          <Link key={figure.href} href={figure.href} className="card stat">
            <strong>{figure.value ?? "–"}</strong>
            <span>{figure.label}</span>
            {figure.detail && <span className="small muted">{figure.detail}</span>}
          </Link>
        ))}
      </div>

      <div className="dashboard-grid">
        <section className="card">
          <h2>Échéances proches</h2>
          {deadlines.data?.content.length === 0 && <p className="muted">Aucune échéance dans les 7 prochains jours.</p>}
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
          <h2>Nouvelles opportunités</h2>
          {opportunities.data?.content.length === 0 && <p className="muted">Aucune nouvelle opportunité.</p>}
          {opportunities.data && opportunities.data.content.length > 0 && (
            <table>
              <tbody>
                {opportunities.data.content.map((tender) => (
                  <tr key={tender.id}>
                    <td>
                      <TenderLink tender={tender} />
                    </td>
                    <td className="nowrap">{formatDate(tender.publishedAt)}</td>
                    {available("relevance") && (
                      <td className="nowrap">{tender.relevanceScore != null ? `${tender.relevanceScore} %` : "–"}</td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          )}
          <p>
            <Link href={`/tenders?${OPPORTUNITIES_QUERY}`}>Voir toutes les nouvelles opportunités</Link>
          </p>
        </section>
      </div>

      {(available("alerts") || available("awards")) && (
        <div className="dashboard-grid">
          {available("alerts") && (
            <section className="card">
              <h2>Mes veilles</h2>
              {alerts.data && activeAlerts.length === 0 && <p className="muted">Aucune veille active.</p>}
              {activeAlerts.length > 0 && (
                <table>
                  <tbody>
                    {activeAlerts.map((alert) => (
                      <tr key={alert.id}>
                        <td>
                          <button type="button" className="link-button" onClick={() => openAlert(alert.id)}>
                            {alert.name}
                          </button>
                        </td>
                        <td className="nowrap">
                          {alert.newCount > 0 ? (
                            <strong>
                              {alert.newCount} nouvelle{alert.newCount > 1 ? "s" : ""}
                            </strong>
                          ) : (
                            <span className="muted">Aucune nouvelle</span>
                          )}
                        </td>
                        <td className="nowrap muted">{alert.matchCount} en cours</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
              <p>
                <Link href="/alerts">Gérer mes veilles</Link>
              </p>
            </section>
          )}

          {available("awards") && (
            <section className="card">
              <h2>Derniers marchés attribués</h2>
              {awards.data?.content.length === 0 && <p className="muted">Aucune attribution.</p>}
              {awards.data && awards.data.content.length > 0 && (
                <table>
                  <tbody>
                    {awards.data.content.map((award) => (
                      <tr key={award.id}>
                        <td>
                          {award.tenderId ? (
                            <Link href={tenderHref(award.tenderId, "/")}>{award.title}</Link>
                          ) : (
                            award.title
                          )}
                          <div className="small muted">
                            {award.winner} · {award.buyer}
                          </div>
                        </td>
                        <td className="nowrap">{formatAmount(award.amount)}</td>
                        <td className="nowrap">{formatDate(award.awardedAt)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
              <p>
                <Link href="/awards">Voir les marchés attribués</Link>
              </p>
            </section>
          )}
        </div>
      )}
    </>
  );
}
