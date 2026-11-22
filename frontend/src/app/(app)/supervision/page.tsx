"use client";

import Link from "next/link";

import { useAuth } from "@/components/AuthProvider";
import { CollectStatus } from "@/components/CollectStatus";
import { Fold } from "@/components/Fold";
import { Icon, type IconName } from "@/components/Icon";
import { AnalysisJournal } from "@/components/settings/AnalysisJournal";
import { CollectJournal } from "@/components/settings/CollectJournal";
import { DcePanel } from "@/components/settings/DcePanel";
import { DuplicatesPanel } from "@/components/settings/DuplicatesPanel";
import { useLoader } from "@/lib/useLoader";
import { formatMoney } from "@/lib/estimate";
import { getAnalysisConfiguration, getAnalysisQuality } from "@/services/analysisApi";
import { getDuplicateGroups } from "@/services/duplicateApi";
import { getTenderCounts } from "@/services/tenderApi";

/** Consultations published since yesterday. */
const RECENT_QUERY = "publishedWithinDays=1";

type Figure = {
  value: number | string | null | undefined;
  label: string;
  detail?: string;
  href?: string;
  icon: IconName;
  tone?: string;
};

/** What an AI call was for, by its kind (analysis or assistance). */
function labelOfUse(kind: string): string {
  if (kind.startsWith("ESTIMATE")) return "Chiffrage";
  if (kind.startsWith("MEMOIR")) return "Mémoire";
  if (kind.startsWith("RESPONSE")) return "Formulaires";
  return "Analyse DCE";
}

/**
 * What an administrator checks: the state of the collect and the volume it
 * brings, the duplicates to decide first, the only
 * work to do here, then the journals. Each part is folded; the duplicates open
 * by themselves when some wait.
 */
export default function SupervisionPage() {
  const { user } = useAuth();
  const admin = user.role === "ADMIN";
  // shortcut: the count reads again what the panel reads; one more small request.
  const duplicates = useLoader(() => (admin ? getDuplicateGroups() : Promise.resolve([])), "supervision-duplicates");
  const recent = useLoader(() => (admin ? getTenderCounts(RECENT_QUERY) : Promise.resolve(null)), "supervision-recent");
  const counts = useLoader(() => (admin ? getTenderCounts() : Promise.resolve(null)), "supervision-counts");
  const ai = useLoader(() => (admin ? getAnalysisConfiguration() : Promise.resolve(null)), "supervision-ai");
  const quality = useLoader(() => (admin ? getAnalysisQuality() : Promise.resolve(null)), "supervision-quality");

  if (!admin) {
    return <p className="banner error">Cette page est réservée aux administrateurs.</p>;
  }

  const duplicateCount = duplicates.data?.length ?? 0;
  const newCount = recent.data ? recent.data.ALL + recent.data.FILTERED_OUT : undefined;
  // Health of the collect and of the criteria: none new says a collect stopped, many out of criteria too strict criteria.
  const month = ai.data?.monthToDate;
  const budget = ai.data?.limits.monthlyBudget ?? null;
  const spent = month?.estimatedCost ?? null;
  const nearBudget = budget !== null && spent !== null && spent >= budget * 0.8;
  const byUse = new Map<string, number>();
  for (const use of month?.byUse ?? []) {
    const label = labelOfUse(use.kind);
    byUse.set(label, (byUse.get(label) ?? 0) + (use.estimatedCost ?? 0));
  }
  const figures: Figure[] = [
    { value: newCount, label: "nouvelles depuis hier", href: `/tenders?tab=ALL&${RECENT_QUERY}`, icon: "tenders" },
    {
      value: recent.data?.FILTERED_OUT,
      label: "dont hors critères",
      href: `/tenders?tab=FILTERED_OUT&${RECENT_QUERY}`,
      icon: "eye",
    },
    { value: counts.data?.FILTERED_OUT, label: "hors critères à vérifier", href: "/tenders?tab=FILTERED_OUT", icon: "eye" },
    {
      value: month && (spent === null ? "–" : formatMoney(spent)),
      label: budget === null ? "dépensés en IA ce mois" : `dépensés en IA sur ${formatMoney(budget)}`,
      detail:
        spent === null
          ? "Prix des tokens non configurés"
          : [...byUse].map(([label, cost]) => `${label} ${formatMoney(cost)}`).join(" · ") || undefined,
      icon: "calculator",
      tone: nearBudget ? "warning" : spent ? "accent" : "muted",
    },
    {
      value: quality.data && (quality.data.averageCost === null ? "–" : formatMoney(quality.data.averageCost)),
      label: "par analyse de DCE en moyenne",
      detail: quality.data ? `${quality.data.done} analyses terminées` : undefined,
      icon: "file",
    },
  ];
  const error = [recent, counts, ai, quality].find((loader) => loader.error)?.error;

  return (
    <div className="settings">
      <h1>Supervision</h1>
      <CollectStatus />
      {error && <p className="banner error">{error}</p>}
      <div className="stats">
        {figures.map((figure) => {
          const content = (
            <>
              <span className="stat-head">
                <Icon name={figure.icon} size={18} />
                <strong>{figure.value ?? "–"}</strong>
              </span>
              <span>{figure.label}</span>
              {figure.detail && <span className="small muted">{figure.detail}</span>}
            </>
          );
          const className = `card stat tone-${figure.tone ?? (figure.value ? "accent" : "muted")}`;
          return figure.href ? (
            <Link key={figure.label} href={figure.href} className={className}>
              {content}
            </Link>
          ) : (
            <div key={figure.label} className={className}>
              {content}
            </div>
          );
        })}
      </div>
      <Fold
        className="card settings-fold"
        title="Doublons à vérifier"
        count={duplicates.data ? (duplicateCount === 0 ? "aucun" : duplicateCount) : undefined}
        open={duplicateCount > 0}
      >
        <DuplicatesPanel />
      </Fold>
      <Fold className="card settings-fold" title="Journal de collecte">
        <CollectJournal />
      </Fold>
      <Fold className="card settings-fold" title="Récupération des DCE">
        <DcePanel />
      </Fold>
      <Fold className="card settings-fold" title="Analyses par IA : journal et qualité">
        <AnalysisJournal />
      </Fold>
    </div>
  );
}
